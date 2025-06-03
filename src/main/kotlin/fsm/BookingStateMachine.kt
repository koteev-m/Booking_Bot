package fsm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

// Поддерживаемая архитектура:
//  • Репозитории (Exposed) остаются без изменений — интерфейсы находятся в пакете db.repositaries
//  • Фасад бота (BotFacade) остаётся в пакете bot.facade
//  • Весь FSM-код (пакет fsm) заменяет прежнюю библиотеку kstatemachine-coroutines
//
// Для работы BookingStateMachine потребуются следующие зависимости (инъекция через FsmDeps):
//  • clubsRepo: db.repositaries.ClubsRepo
//  • tablesRepo: db.repositaries.TablesRepo
//  • bookingsRepo: db.repositaries.BookingsRepo
//  • usersRepo: db.repositaries.UsersRepo
//  • botFacade: bot.facade.BotFacade
//  • scope: CoroutineScope — корутинный скоуп для выполнения фоновых действий

import db.Club
import db.TableInfo
import db.repositories.BookingsRepo
import db.repositories.ClubsRepo
import db.repositories.TablesRepo
import db.repositories.UsersRepo

// --- ДАННЫЕ ДЛЯ ХРАНЕНИЯ ПРОМЕЖУТОЧНОЙ ИНФОРМАЦИИ О БРОНИРОВАНИИ ---
data class DraftBooking(
    val clubId: Int,
    val clubTitle: String,
    val tableId: Int,
    val tableLabel: String,
    val date: LocalDate,
    val slot: String,
    val peopleCount: Int,
    val guestName: String,
    val guestPhone: String
)

// --- СОСТОЯНИЯ БРОНИРОВАНИЯ ---
sealed class BookingState {
    /** Начальное состояние: ничего не делаем, ждем команды Start */
    object Idle : BookingState()

    /** Показ списка клубов */
    data class ShowingClubs(val clubs: List<Club>) : BookingState()

    /** Показ доступных дат для выбранного клуба */
    data class ShowingDates(
        val clubId: Int,
        val clubTitle: String,
        val availableDates: List<LocalDate>
    ) : BookingState()

    /** Показ доступных столов для выбранного клуба и даты */
    data class ShowingTables(
        val clubId: Int,
        val clubTitle: String,
        val date: LocalDate,
        val tables: List<TableInfo>
    ) : BookingState()

    /** Показ доступных слотов (временных интервалов) для выбранного стола */
    data class ShowingSlots(
        val clubId: Int,
        val clubTitle: String,
        val date: LocalDate,
        val tableId: Int,
        val tableLabel: String,
        val availableSlots: List<String>
    ) : BookingState()

    /** Ввод количества гостей */
    data class EnteringGuestCount(
        val clubId: Int,
        val clubTitle: String,
        val date: LocalDate,
        val tableId: Int,
        val tableLabel: String,
        val slot: String
    ) : BookingState()

    /** Ввод имени гостя */
    data class EnteringGuestName(
        val draft: DraftBookingTemp
    ) : BookingState()

    /** Ввод телефона гостя */
    data class EnteringGuestPhone(
        val draft: DraftBookingTemp
    ) : BookingState()

    /** Подтверждение брони (показ деталей) */
    data class ConfirmingBooking(
        val draft: DraftBookingTemp
    ) : BookingState()

    /** Завершено успешно: бронь сохранена */
    data class BookingDone(
        val bookingId: Int,
        val loyaltyPoints: Int
    ) : BookingState()

    /** Состояние отмены */
    object Cancelled : BookingState()

    /** Ошибка — показываем сообщение пользователю */
    data class Error(val message: String) : BookingState()
}

/**
 * Промежуточный класс для последовательного сбора данных перед созданием окончательного DraftBooking.
 * По мере ввода собирается частичная информация:
 *   - clubId, clubTitle, tableId, tableLabel, date, slot, peopleCount, guestName, guestPhone
 */
data class DraftBookingTemp(
    val clubId: Int,
    val clubTitle: String,
    val tableId: Int,
    val tableLabel: String,
    val date: LocalDate,
    val slot: String,
    val peopleCount: Int? = null,
    val guestName: String? = null,
    val guestPhone: String? = null
) {
    fun withCount(count: Int): DraftBookingTemp =
        copy(peopleCount = count)

    fun withName(name: String): DraftBookingTemp =
        copy(guestName = name)

    fun withPhone(phone: String): DraftBookingTemp =
        copy(guestPhone = phone)

    fun toFinal(): DraftBooking {
        // Все поля должны быть непустыми к моменту финальной конвертации
        require(peopleCount != null) { "peopleCount not set" }
        require(guestName != null) { "guestName not set" }
        require(guestPhone != null) { "guestPhone not set" }
        return DraftBooking(
            clubId = clubId,
            clubTitle = clubTitle,
            tableId = tableId,
            tableLabel = tableLabel,
            date = date,
            slot = slot,
            peopleCount = peopleCount,
            guestName = guestName,
            guestPhone = guestPhone
        )
    }
}

// --- СОБЫТИЯ БРОНИРОВАНИЯ ---
sealed class BookingEvent {
    /** Пользователь начал новый процесс бронирования */
    object Start : BookingEvent()

    /** Пользователь выбрал клуб (по его ID) */
    data class ClubSelected(val clubId: Int) : BookingEvent()

    /** Пользователь выбрал дату (LocalDate) */
    data class DateSelected(val date: LocalDate) : BookingEvent()

    /** Пользователь выбрал стол (объект TableInfo) */
    data class TableSelected(val table: TableInfo) : BookingEvent()

    /** Пользователь выбрал временной слот (строка, например "19:00–20:00") */
    data class SlotSelected(val slot: String) : BookingEvent()

    /** Пользователь ввел количество гостей */
    data class GuestCountEntered(val count: Int) : BookingEvent()

    /** Пользователь ввел имя гостя */
    data class GuestNameEntered(val name: String) : BookingEvent()

    /** Пользователь ввел телефон гостя */
    data class GuestPhoneEntered(val phone: String) : BookingEvent()

    /** Пользователь подтвердил создание брони */
    object ConfirmBooking : BookingEvent()

    /** Пользователь отменил операцию в любой момент */
    object Cancel : BookingEvent()
}

// --- ДЕПСЫ (ЗАВИСИМОСТИ), ИНЪЕКЦИЯ В STATE MACHINE ---
/**
 * Контейнер зависимостей для BookingStateMachine:
 *  • clubsRepo — интерфейс db.repositaries.ClubsRepo
 *  • tablesRepo — интерфейс db.repositaries.TablesRepo
 *  • bookingsRepo — интерфейс db.repositaries.BookingsRepo
 *  • usersRepo — интерфейс db.repositaries.UsersRepo (если понадобится, например, для начисления лояльти)
 *  • botFacade — фасад для отправки сообщений в Telegram
 *  • scope — CoroutineScope для запуска фоновых операций
 */
data class FsmDeps(
    val clubsRepo: ClubsRepo,
    val tablesRepo: TablesRepo,
    val bookingsRepo: BookingsRepo,
    val usersRepo: UsersRepo,
    val botFacade: BotFacade,
    val scope: CoroutineScope
)

// --- СОБСТВЕННО BookingStateMachine, РЕАЛИЗАЦИЯ НА STATEFLOW + sealed class ---
/**
 * BookingStateMachine — управляет всем процессом онлайн‐бронирования:
 * 1) Пользователь присылает событие Start → показываем список клубов
 * 2) Пользователь выбирает клуб → показываем календарь доступных дат
 * 3) Пользователь выбирает дату → показываем свободные столы
 * 4) Пользователь выбирает стол → показываем свободные слоты
 * 5) Пользователь выбирает слот → спрашиваем кол‐во гостей
 * 6) Пользователь вводит кол‐во гостей → спрашиваем имя гостя
 * 7) Пользователь вводит имя гостя → спрашиваем телефон гостя
 * 8) Пользователь вводит телефон → показываем финальную информацию и кнопку «Подтвердить»
 * 9) Пользователь нажимает «Подтвердить» → сохраняем бронирование в БД, начисляем баллы, сообщаем успех
 * 10) При нажатии «Отменить» в любой момент → переходим в состояние Cancelled и отправляем «Действие отменено»
 *
 * Любое исключение в потоках приводит к показу состояния Error со стандартным сообщением.
 */
class BookingStateMachine(
    private val deps: FsmDeps,
    private val userId: Long
) {
    // Внутренний StateFlow для слежения за состоянием
    private val _state: MutableStateFlow<BookingState> = MutableStateFlow(BookingState.Idle)
    val state: StateFlow<BookingState> = _state.asStateFlow()

    /** Обработка входящего события BookingEvent */
    fun onEvent(event: BookingEvent) {
        when (event) {
            BookingEvent.Start -> handleStart()
            BookingEvent.Cancel -> handleCancel()
            is BookingEvent.ClubSelected -> handleClubSelected(event)
            is BookingEvent.DateSelected -> handleDateSelected(event)
            is BookingEvent.TableSelected -> handleTableSelected(event)
            is BookingEvent.SlotSelected -> handleSlotSelected(event)
            is BookingEvent.GuestCountEntered -> handleGuestCountEntered(event)
            is BookingEvent.GuestNameEntered -> handleGuestNameEntered(event)
            is BookingEvent.GuestPhoneEntered -> handleGuestPhoneEntered(event)
            BookingEvent.ConfirmBooking -> handleConfirmBooking()
        }
    }

    /** 1) Начало: показываем список клубов */
    private fun handleStart() {
        // Запрос списка клубов — делаем асинхронно
        deps.scope.launch {
            try {
                val clubs: List<Club> = deps.clubsRepo.getAllClubs()
                // Обновляем состояние
                _state.value = BookingState.ShowingClubs(clubs)
                // Высылаем клавиатуру со списком клубов
                deps.botFacade.sendClubsList(userId, clubs)
            } catch (ex: Throwable) {
                transitionToError("Ошибка при получении списка клубов.")
            }
        }
    }

    /** 2) Пользователь выбрал клуб → показываем календарь доступных дат */
    private fun handleClubSelected(event: BookingEvent.ClubSelected) {
        deps.scope.launch {
            try {
                // Находим название клуба (по ID) для вывода в подсказках
                val club: Club = deps.clubsRepo.getById(event.clubId)
                // Запрашиваем доступные даты
                val availableDates: List<LocalDate> =
                    deps.tablesRepo.getAvailableDatesForClub(event.clubId)
                // Обновляем состояние
                _state.value = BookingState.ShowingDates(
                    clubId = club.id,
                    clubTitle = club.title,
                    availableDates = availableDates
                )
                // Отправляем inline‐календарь с доступными датами
                deps.botFacade.sendCalendarInline(
                    chatId = userId,
                    clubId = club.id,
                    clubTitle = club.title,
                    availableDates = availableDates
                )
            } catch (ex: Throwable) {
                transitionToError("Ошибка при получении доступных дат.")
            }
        }
    }

    /** 3) Пользователь выбрал дату → показываем свободные столы */
    private fun handleDateSelected(event: BookingEvent.DateSelected) {
        val current = _state.value
        if (current is BookingState.ShowingDates) {
            deps.scope.launch {
                try {
                    val clubId = current.clubId
                    val clubTitle = current.clubTitle
                    val date = event.date

                    // Запрашиваем свободные столы для выбранного клуба и даты
                    val tables: List<TableInfo> =
                        deps.tablesRepo.getAvailableTables(clubId, date)

                    // Обновляем состояние
                    _state.value = BookingState.ShowingTables(
                        clubId = clubId,
                        clubTitle = clubTitle,
                        date = date,
                        tables = tables
                    )
                    // Отправляем inline‐клавиатуру со списком свободных столов
                    deps.botFacade.sendTablesInline(
                        chatId = userId,
                        clubId = clubId,
                        clubTitle = clubTitle,
                        date = date,
                        tables = tables
                    )
                } catch (ex: Throwable) {
                    transitionToError("Ошибка при получении свободных столов.")
                }
            }
        } else {
            // Неподходящее состояние: игнорируем
        }
    }

    /** 4) Пользователь выбрал стол → показываем слоты (временные интервалы) */
    private fun handleTableSelected(event: BookingEvent.TableSelected) {
        val current = _state.value
        if (current is BookingState.ShowingTables) {
            deps.scope.launch {
                try {
                    val clubId = current.clubId
                    val clubTitle = current.clubTitle
                    val date = current.date
                    val table = event.table // содержит id и название стола

                    // Запрашиваем свободные слоты для выбранного стола
                    val slots: List<String> =
                        deps.tablesRepo.getAvailableSlotsForTable(table.id, date)

                    // Обновляем состояние
                    _state.value = BookingState.ShowingSlots(
                        clubId = clubId,
                        clubTitle = clubTitle,
                        date = date,
                        tableId = table.id,
                        tableLabel = table.label,
                        availableSlots = slots
                    )
                    // Отправляем inline‐клавиатуру со списком слотов
                    deps.botFacade.sendSlotsInline(
                        chatId = userId,
                        clubId = clubId,
                        clubTitle = clubTitle,
                        date = date,
                        tableId = table.id,
                        tableLabel = table.label,
                        slots = slots
                    )
                } catch (ex: Throwable) {
                    transitionToError("Ошибка при получении свободных слотов.")
                }
            }
        } else {
            // Неподходящее состояние: игнорируем
        }
    }

    /** 5) Пользователь выбрал слот → спрашиваем количество гостей */
    private fun handleSlotSelected(event: BookingEvent.SlotSelected) {
        val current = _state.value
        if (current is BookingState.ShowingSlots) {
            // Переходим к вводу количества гостей
            _state.value = BookingState.EnteringGuestCount(
                clubId = current.clubId,
                clubTitle = current.clubTitle,
                date = current.date,
                tableId = current.tableId,
                tableLabel = current.tableLabel,
                slot = event.slot
            )
            // Отправляем запрос на ввод числа гостей
            deps.botFacade.askGuestCount(
                chatId = userId,
                clubTitle = current.clubTitle,
                tableLabel = current.tableLabel,
                date = current.date,
                slot = event.slot
            )
        } else {
            // Неподходящее состояние: игнорируем
        }
    }

    /** 6) Пользователь ввел количество гостей → спрашиваем имя гостя */
    private fun handleGuestCountEntered(event: BookingEvent.GuestCountEntered) {
        val current = _state.value
        if (current is BookingState.EnteringGuestCount) {
            val count = event.count
            // Проверка валидности количества гостей: допустим 1..10
            if (count < 1 || count > 10) {
                // Некорректное число: просим ввести заново
                deps.botFacade.askGuestCountInvalid(
                    chatId = userId,
                    min = 1,
                    max = 10
                )
                return
            }

            // Формируем промежуточный объект DraftBookingTemp
            val draftTemp = DraftBookingTemp(
                clubId = current.clubId,
                clubTitle = current.clubTitle,
                tableId = current.tableId,
                tableLabel = current.tableLabel,
                date = current.date,
                slot = current.slot
            ).withCount(count)

            // Обновляем состояние — ввод имени гостя
            _state.value = BookingState.EnteringGuestName(draftTemp)

            // Отправляем запрос на ввод имени
            deps.botFacade.askGuestName(
                chatId = userId
            )
        } else {
            // Неподходящее состояние: игнорируем
        }
    }

    /** 7) Пользователь ввел имя гостя → спрашиваем телефон */
    private fun handleGuestNameEntered(event: BookingEvent.GuestNameEntered) {
        val current = _state.value
        if (current is BookingState.EnteringGuestName) {
            val name = event.name.trim()
            if (name.isEmpty() || name.length < 2) {
                deps.botFacade.askGuestNameInvalid(
                    chatId = userId
                )
                return
            }
            // Дополняем промежуточный объект именем
            val draftTemp = current.draft.withName(name)

            // Обновляем состояние — ввод телефона
            _state.value = BookingState.EnteringGuestPhone(draftTemp)

            // Отправляем запрос на ввод телефона
            deps.botFacade.askGuestPhone(
                chatId = userId
            )
        } else {
            // Неподходящее состояние: игнорируем
        }
    }

    /** 8) Пользователь ввел телефон → показываем финальную информацию */
    private fun handleGuestPhoneEntered(event: BookingEvent.GuestPhoneEntered) {
        val current = _state.value
        if (current is BookingState.EnteringGuestPhone) {
            val phone = event.phone.trim()
            // Простейшая валидация формата номера: от 10 до 15 символов (цифры, пробелы, +)
            val normalized = phone.replace(" ", "")
            if (!normalized.matches(Regex("""^\+?\d{10,15}${'$'}"""))) {
                deps.botFacade.askGuestPhoneInvalid(
                    chatId = userId
                )
                return
            }
            // Дополняем промежуточный объект телефоном
            val draftTemp = current.draft.withPhone(phone)

            // Переходим к подтверждению бронирования
            _state.value = BookingState.ConfirmingBooking(draftTemp)

            // Отправляем пользователю финальные детали и кнопку «Подтвердить»
            deps.botFacade.showConfirmBooking(
                chatId = userId,
                draft = draftTemp.toFinal()
            )
        } else {
            // Неподходящее состояние: игнорируем
        }
    }

    /** 9) Пользователь нажал «Подтвердить» → сохраняем бронь и показываем результат */
    private fun handleConfirmBooking() {
        val current = _state.value
        if (current is BookingState.ConfirmingBooking) {
            deps.scope.launch {
                try {
                    // Преобразуем во Final DraftBooking
                    val finalDraft: DraftBooking = current.draft.toFinal()

                    // Сохраняем бронь через bookingsRepo
                    // Предполагаемая сигнатура:
                    //   fun saveBooking(
                    //     tableId: Int,
                    //     userId: Long,
                    //     date: LocalDate,
                    //     slot: String,
                    //     peopleCount: Int,
                    //     guestName: String,
                    //     guestPhone: String
                    //   ): Pair<Int /*bookingId*/, Int /*earnedPoints*/>
                    val (bookingId, earnedPoints) = deps.bookingsRepo.saveBooking(
                        tableId = finalDraft.tableId,
                        userId = userId,
                        date = finalDraft.date,
                        slot = finalDraft.slot,
                        peopleCount = finalDraft.peopleCount,
                        guestName = finalDraft.guestName,
                        guestPhone = finalDraft.guestPhone
                    )

                    // Обновляем состояние — успешно завершено
                    _state.value = BookingState.BookingDone(
                        bookingId = bookingId,
                        loyaltyPoints = earnedPoints
                    )
                    // Оповещаем пользователя об успехе
                    deps.botFacade.sendBookingSuccess(
                        chatId = userId,
                        bookingId = bookingId,
                        points = earnedPoints
                    )
                } catch (ex: Throwable) {
                    transitionToError("Ошибка при сохранении бронирования.")
                }
            }
        } else {
            // Неподходящее состояние: игнорируем
        }
    }

    /** 10) Пользователь отменил операцию → показываем «Действие отменено» */
    private fun handleCancel() {
        // Сразу переходим в состояние Cancelled
        _state.value = BookingState.Cancelled
        deps.botFacade.sendActionCancelled(
            chatId = userId
        )
    }

    /** Помощник для перехода в Error-состояние */
    private fun transitionToError(message: String) {
        _state.value = BookingState.Error(message)
        deps.botFacade.sendErrorMessage(
            chatId = userId,
            text = message
        )
    }
}