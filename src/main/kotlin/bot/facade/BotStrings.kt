package bot.facade

import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import db.BookingStatus // Assuming BookingStatus is in db package

// ... (interface LocalizedStrings remains the same) ...
interface LocalizedStrings {
    val languageCode: String
    val languageName: String

    // Общие
    fun welcomeMessage(userName: String?): String
    val chooseAction: String
    val startBookingCommand: String // Text for /book or /start command
    val menuCommand: String // Text for /menu command
    val helpCommand: String // Text for /help command
    val langCommand: String // Text for /lang command
    val buttonBack: String
    val buttonConfirm: String
    val buttonCancel: String
    val actionCancelled: String
    val errorMessageDefault: String
    val unknownCommand: String
    val featureInDevelopment: String
    fun stepTracker(current: Int, total: Int): String

    // Главное меню
    val menuVenueInfo: String
    val menuMyBookings: String
    fun menuBookTableInClub(clubName: String): String
    val menuAskQuestion: String
    val menuOpenApp: String // Placeholder for "Open App" button
    val menuHelp: String
    val menuChangeLanguage: String

    // Выбор языка
    val chooseLanguagePrompt: String

    // Бронирование
    val chooseClubPrompt: String
    val chooseDatePrompt: String
    val chooseTablePrompt: String
    val tableLayoutInfo: String // Caption for the table layout image
    fun tableButtonText(tableNumber: Int, seats: Int): String
    val noAvailableTables: String
    val askPeopleCount: String
    fun invalidPeopleCount(min: Int, max: Int): String
    val chooseSlotPrompt: String
    fun chooseSlotForTableInClub(tableNumber: Int, clubName: String): String
    val noAvailableSlots: String
    val confirmBookingPrompt: String
    fun bookingDetailsFormat(
        clubName: String,
        tableNumber: Int,
        guestCount: Int,
        date: String,
        timeSlot: String,
        guestName: String,
        guestPhone: String
    ): String
    val askGuestName: String
    val askGuestNameExample: String
    val invalidGuestName: String
    val askGuestPhone: String
    val askGuestPhoneExample: String
    val invalidPhoneFormat: String
    fun bookingSuccess(bookingId: Int, points: Int): String
    val bookingCancelledMessage: String
    val bookingAlreadyCancelled: String
    val bookingNotFound: String
    fun bookingCancellationConfirmed(bookingId: Int, clubName: String): String
    val notAllDataCollectedError: String

    // Информация о заведениях
    val chooseVenueForInfo: String
    val venueInfoButtonPosters: String
    val venueInfoButtonPhotos: String
    val venueNotFoundInfo: String
    fun venueDetails(clubTitle: String, clubDescription: String?, address: String?, phone: String?, workingHours: String?): String

    // Мои бронирования
    val noActiveBookings: String
    val myBookingsHeader: String
    fun bookingItemFormat(bookingId: Int, clubName: String, tableNumber: Int, date: String, time: String, guestsCount: Int, status: String): String
    val chooseBookingToManage: String
    fun manageBookingPrompt(bookingId: Int, details: String): String
    val buttonChangeBooking: String
    val buttonCancelBooking: String
    val buttonRateBooking: String // Для обратной связи
    val changeBookingInfo: String

    // Задать вопрос
    val askQuestionPrompt: String
    val questionReceived: String

    // Помощь
    val helpText: String

    // Обратная связь
    fun askForFeedbackPrompt(clubName: String, bookingId: Int): String
    fun feedbackThanks(rating: Int): String
    val feedbackSkipped: String

    // Календарь
    fun calendarDay(day: Int): String
    val calendarPrevMonth: String
    val calendarNextMonth: String
    fun calendarMonthYear(month: Month, year: Int): String // Takes java.time.Month
    val weekdaysShort: List<String> // Пн, Вт, Ср...

    fun chooseTablePromptForClubAndDate(clubTitle: String, formattedDate: String): String
    val notApplicable: String
    fun bookingStatusToText(status: BookingStatus): String
}


object RussianStrings : LocalizedStrings {
    override val languageCode = BotConstants.RUSSIAN_LANGUAGE_CODE
    override val languageName = "Русский 🇷🇺"

    override fun welcomeMessage(userName: String?) =
        userName?.let { "Рады Вас Видеть, @$it!\nВыберите действие:" }
            ?: "Рады Вас Видеть!\nВыберите действие:"

    override val chooseAction = "Выберите действие:"
    override val startBookingCommand = "/book"
    override val menuCommand = "/menu"
    override val helpCommand = "/help"
    override val langCommand = "/lang"
    override val buttonBack = "⬅️ Назад"
    override val buttonConfirm = "✅ Подтвердить"
    override val buttonCancel = "❌ Отменить"
    override val actionCancelled = "Действие отменено."
    override val errorMessageDefault = "⚠️ Произошла ошибка. Пожалуйста, попробуйте еще раз или начните заново с команды /start."
    override val unknownCommand = "Не совсем понял вас. Пожалуйста, используйте кнопки меню или команду /start."
    override val featureInDevelopment = "🛠 Эта функция пока в разработке. Следите за обновлениями!"
    override fun stepTracker(current: Int, total: Int) = "<i>Шаг $current из $total</i>"

    override val menuVenueInfo = "Наши заведения (INFO)"
    override val menuMyBookings = "Мои бронирования"
    override fun menuBookTableInClub(clubName: String) = "Бронь в $clubName"
    override val menuAskQuestion = "Задать вопрос"
    override val menuOpenApp = "Открыть приложение"
    override val menuHelp = "❓ Помощь/FAQ"
    override val menuChangeLanguage = "Сменить язык"

    override val chooseLanguagePrompt = "Пожалуйста, выберите язык:"

    override val chooseClubPrompt = "Выберите клуб для бронирования:"
    override val chooseDatePrompt = "🗓 Выберите дату:"
    override val chooseTablePrompt = "Выберите стол:"
    override val tableLayoutInfo =
        "Ниже представлена схема расположения столов. Нажмите на кнопку с номером свободного стола."
    override fun tableButtonText(tableNumber: Int, seats: Int) = "Стол №$tableNumber (до $seats чел.)"
    override val noAvailableTables =
        "К сожалению, в выбранном клубе сейчас нет доступных столов для онлайн-бронирования на выбранную дату."
    override val askPeopleCount = "Сколько гостей придёт? (например, <b>5</b>)"
    override fun invalidPeopleCount(min: Int, max: Int) =
        "Количество гостей должно быть числом от $min до $max. Пожалуйста, введите корректное число."
    override val chooseSlotPrompt = "Выберите удобный временной слот:"
    override fun chooseSlotForTableInClub(tableNumber: Int, clubName: String) =
        "Выберите время для стола №$tableNumber в клубе \"$clubName\":"
    override val noAvailableSlots =
        "К сожалению, для выбранного стола нет доступных слотов на эту дату."
    override val confirmBookingPrompt = "Пожалуйста, подтвердите вашу бронь:"
    override fun bookingDetailsFormat(
        clubName: String,
        tableNumber: Int,
        guestCount: Int,
        date: String,
        timeSlot: String,
        guestName: String,
        guestPhone: String
    ) =
        """
        <b>Клуб:</b> $clubName
        <b>Стол:</b> №$tableNumber
        <b>Количество гостей:</b> $guestCount
        <b>Дата:</b> $date
        <b>Время:</b> $timeSlot
        <b>Имя:</b> $guestName
        <b>Телефон:</b> $guestPhone
        """.trimIndent()

    override val askGuestName = "На чье имя забронировать стол? (например, <b>Иван</b>)"
    override val askGuestNameExample = "(например, Иван)"
    override val invalidGuestName = "Имя не может быть пустым или слишком коротким. Пожалуйста, введите корректное имя."
    override val askGuestPhone = "Пожалуйста, введите ваш контактный номер телефона (например, <b>+79123456789</b>):"
    override val askGuestPhoneExample = "(например, +79123456789 или 89123456789)"
    override val invalidPhoneFormat =
        "Неверный формат номера телефона. Введите номер в формате +7XXXXXXXXXX или 8XXXXXXXXXX."
    override fun bookingSuccess(bookingId: Int, points: Int) =
        "✅ Бронь #$bookingId успешно создана! Вам начислено $points бонусных баллов. Спасибо!"
    override val bookingCancelledMessage = "Бронирование отменено."
    override val bookingAlreadyCancelled = "Эта бронь уже была отменена."
    override val bookingNotFound = "Бронь не найдена или у вас нет прав на это действие."
    override fun bookingCancellationConfirmed(bookingId: Int, clubName: String) =
        "Ваша бронь #$bookingId в клубе '$clubName' отменена."
    override val notAllDataCollectedError = "Не все данные для бронирования были собраны. Пожалуйста, начните заново."

    override val chooseVenueForInfo = "Выберите заведение для просмотра информации:"
    override val venueInfoButtonPosters = "Афиши"
    override val venueInfoButtonPhotos = "Фотоотчет"
    override val venueNotFoundInfo = "Информация о заведении не найдена."
    override fun venueDetails(
        clubTitle: String,
        clubDescription: String?,
        address: String?,
        phone: String?,
        workingHours: String?
    ) =
        """
        <b>$clubTitle</b>
        ${clubDescription?.let { "\n$it\n" } ?: "\nОписание пока не добавлено.\n"}
        ${address?.let { "\n📍 <b>Адрес:</b> $it" } ?: ""}
        ${phone?.let { "\n📞 <b>Телефон:</b> $it" } ?: ""}
        ${workingHours?.let { "\n⏰ <b>Время работы:</b> $it" } ?: ""}
        """.trimIndent()

    override val noActiveBookings = "У вас нет активных бронирований."
    override val myBookingsHeader = "<b>Ваши активные бронирования:</b>"
    override fun bookingItemFormat(
        bookingId: Int,
        clubName: String,
        tableNumber: Int,
        date: String,
        time: String,
        guestsCount: Int,
        status: String
    ) =
        "ID: $bookingId, Клуб: $clubName, Стол: №$tableNumber, Дата: $date, Время: $time, Гостей: $guestsCount, Статус: $status"
    override val chooseBookingToManage = "Выберите бронирование для управления:"
    override fun manageBookingPrompt(bookingId: Int, details: String) =
        "<b>Бронь #$bookingId:</b>\n$details\n\nЧто вы хотите сделать?"
    override val buttonChangeBooking = "Изменить бронь"
    override val buttonCancelBooking = "Отменить бронь"
    override val buttonRateBooking = "⭐ Оставить отзыв"
    override val changeBookingInfo =
        "Для изменения бронирования, пожалуйста, отмените текущее и создайте новое, или свяжитесь с администрацией клуба."

    override val askQuestionPrompt = "Напишите ваш вопрос, и мы постараемся ответить как можно скорее:"
    override val questionReceived = "Спасибо! Ваш вопрос принят. Мы скоро с вами свяжемся."

    override val helpText =
        """
        <b>Помощь по боту:</b>
        - Используйте кнопки главного меню для навигации.
        - /start или /book - начать новое бронирование.
        - /menu - показать главное меню.
        - /lang - сменить язык.
        Если у вас возникли проблемы, обратитесь в поддержку (контакты в описании клуба).
        """.trimIndent()

    override fun askForFeedbackPrompt(clubName: String, bookingId: Int) =
        "Пожалуйста, оцените ваше недавнее посещение клуба $clubName (бронь #$bookingId). Выберите оценку от 1 до 5 (⭐️):"

    override fun feedbackThanks(rating: Int) = "Спасибо за ваш отзыв ($rating ⭐)! Мы ценим ваше мнение."
    override val feedbackSkipped = "Жаль, что вы не оставили отзыв. Будем рады видеть вас снова!"

    override fun calendarDay(day: Int) = day.toString()
    override val calendarPrevMonth = "‹ Пред."
    override val calendarNextMonth = "След. ›"
    override fun calendarMonthYear(month: Month, year: Int): String {
        return "${month.getDisplayName(TextStyle.FULL_STANDALONE, Locale("ru"))} $year"
    }
    override val weekdaysShort = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

    override fun chooseTablePromptForClubAndDate(clubTitle: String, formattedDate: String) =
        "Выберите стол в клубе \"$clubTitle\" на $formattedDate:"
    override val notApplicable = "Н/Д" // Неприменимо или нет данных

    override fun bookingStatusToText(status: BookingStatus): String {
        return when (status) {
            BookingStatus.NEW -> "Новая"
            // BookingStatus.PENDING -> "В обработке" // PENDING was removed
            BookingStatus.CONFIRMED -> "Подтверждена"
            BookingStatus.CANCELLED -> "Отменена"
            BookingStatus.COMPLETED -> "Завершена"
            BookingStatus.AWAITING_FEEDBACK -> "Ожидает отзыва"
            BookingStatus.ARCHIVED -> "В архиве"
            // else -> "Неизвестный статус" // Optional: for safety if new statuses are added without updating strings
        }
    }
}

object EnglishStrings : LocalizedStrings {
    override val languageCode = BotConstants.ENGLISH_LANGUAGE_CODE
    override val languageName = "English 🇬🇧"

    override fun welcomeMessage(userName: String?) =
        userName?.let { "Welcome, @$it!\nPlease select an action:" } ?: "Welcome!\nPlease select an action:"

    override val chooseAction = "Please select an action:"
    override val startBookingCommand = "/book"
    override val menuCommand = "/menu"
    override val helpCommand = "/help"
    override val langCommand = "/lang"
    override val buttonBack = "⬅️ Back"
    override val buttonConfirm = "✅ Confirm"
    override val buttonCancel = "❌ Cancel"
    override val actionCancelled = "Action cancelled."
    override val errorMessageDefault =
        "⚠️ An error occurred. Please try again or start over with the /start command."
    override val unknownCommand =
        "I didn't quite understand that. Please use the menu buttons or the /start command."
    override val featureInDevelopment =
        "🛠 This feature is currently under development. Stay tuned for updates!"
    override fun stepTracker(current: Int, total: Int) = "<i>Step $current of $total</i>"

    override val menuVenueInfo = "Our Venues (INFO)"
    override val menuMyBookings = "My Bookings"
    override fun menuBookTableInClub(clubName: String) = "Book in $clubName"
    override val menuAskQuestion = "Ask a Question"
    override val menuOpenApp = "Open App"
    override val menuHelp = "❓ Help/FAQ"
    override val menuChangeLanguage = "Change Language"

    override val chooseLanguagePrompt = "Please select your language:"

    override val chooseClubPrompt = "Select a club to book:"
    override val chooseDatePrompt = "🗓 Select a date:"
    override val chooseTablePrompt = "Select a table:"
    override val tableLayoutInfo =
        "Below is the table layout. Click on the button with the number of an available table."
    override fun tableButtonText(tableNumber: Int, seats: Int) =
        "Table #$tableNumber (up to $seats ppl)"
    override val noAvailableTables =
        "Unfortunately, there are no tables available for online booking at the selected club for the chosen date."
    override val askPeopleCount = "How many guests will there be? (e.g., <b>5</b>)"
    override fun invalidPeopleCount(min: Int, max: Int) =
        "The number of guests must be between $min and $max. Please enter a correct number."
    override val chooseSlotPrompt = "Select a convenient time slot:"
    override fun chooseSlotForTableInClub(tableNumber: Int, clubName: String) =
        "Select a time for table #$tableNumber at \"$clubName\":"
    override val noAvailableSlots =
        "Unfortunately, there are no available slots for the selected table on this date."
    override val confirmBookingPrompt = "Please confirm your booking:"
    override fun bookingDetailsFormat(
        clubName: String,
        tableNumber: Int,
        guestCount: Int,
        date: String,
        timeSlot: String,
        guestName: String,
        guestPhone: String
    ) =
        """
        <b>Club:</b> $clubName
        <b>Table:</b> #$tableNumber
        <b>Guests:</b> $guestCount
        <b>Date:</b> $date
        <b>Time:</b> $timeSlot
        <b>Name:</b> $guestName
        <b>Phone:</b> $guestPhone
        """.trimIndent()

    override val askGuestName = "What name should the booking be under? (e.g., <b>John</b>)"
    override val askGuestNameExample = "(e.g., John)"
    override val invalidGuestName =
        "Name cannot be empty or too short. Please enter a proper name."
    override val askGuestPhone =
        "Please enter your contact phone number (e.g., <b>+12345678900</b>):"
    override val askGuestPhoneExample = "(e.g., +12345678900)"
    override val invalidPhoneFormat =
        "Invalid phone number format. Please enter a valid number."
    override fun bookingSuccess(bookingId: Int, points: Int) =
        "✅ Booking #$bookingId successfully created! You've earned $points loyalty points. Thank you!"
    override val bookingCancelledMessage = "Booking cancelled."
    override val bookingAlreadyCancelled = "This booking has already been cancelled."
    override val bookingNotFound =
        "Booking not found or you do not have permission for this action."
    override fun bookingCancellationConfirmed(bookingId: Int, clubName: String) =
        "Your booking #$bookingId at '$clubName' has been cancelled."
    override val notAllDataCollectedError =
        "Not all booking data was collected. Please start over."

    override val chooseVenueForInfo = "Select a venue to view information:"
    override val venueInfoButtonPosters = "Posters"
    override val venueInfoButtonPhotos = "Photo Report"
    override val venueNotFoundInfo = "Venue information not found."
    override fun venueDetails(
        clubTitle: String,
        clubDescription: String?,
        address: String?,
        phone: String?,
        workingHours: String?
    ) =
        """
        <b>$clubTitle</b>
        ${clubDescription?.let { "\n$it\n" } ?: "\nDescription not yet available.\n"}
        ${address?.let { "\n📍 <b>Address:</b> $it" } ?: ""}
        ${phone?.let { "\n📞 <b>Phone:</b> $it" } ?: ""}
        ${workingHours?.let { "\n⏰ <b>Working Hours:</b> $it" } ?: ""}
        """.trimIndent()

    override val noActiveBookings = "You have no active bookings."
    override val myBookingsHeader = "<b>Your active bookings:</b>"
    override fun bookingItemFormat(
        bookingId: Int,
        clubName: String,
        tableNumber: Int,
        date: String,
        time: String,
        guestsCount: Int,
        status: String
    ) =
        "ID: $bookingId, Club: $clubName, Table: #$tableNumber, Date: $date, Time: $time, Guests: $guestsCount, Status: $status"
    override val chooseBookingToManage = "Select a booking to manage:"
    override fun manageBookingPrompt(bookingId: Int, details: String) =
        "<b>Booking #$bookingId:</b>\n$details\n\nWhat would you like to do?"
    override val buttonChangeBooking = "Change Booking"
    override val buttonCancelBooking = "Cancel Booking"
    override val buttonRateBooking = "⭐ Leave Feedback"
    override val changeBookingInfo =
        "To change your booking, please cancel the current one and create a new one, or contact the club administration."

    override val askQuestionPrompt = "Write your question, and we will try to answer as soon as possible:"
    override val questionReceived =
        "Thank you! Your question has been received. We will contact you soon."

    override val helpText =
        """
        <b>Bot Help:</b>
        - Use the main menu buttons for navigation.
        - /start or /book - to start a new booking.
        - /menu - to show the main menu.
        - /lang - to change language.
        If you have any issues, please contact support (contacts in club descriptions).
        """.trimIndent()

    override fun askForFeedbackPrompt(clubName: String, bookingId: Int) =
        "Please rate your recent visit to $clubName (booking #$bookingId). Choose a rating from 1 to 5 (⭐️):"

    override fun feedbackThanks(rating: Int) =
        "Thank you for your feedback ($rating ⭐)! We appreciate your opinion."
    override val feedbackSkipped = "Sorry you didn't leave feedback. We hope to see you again soon!"

    override fun calendarDay(day: Int) = day.toString()
    override val calendarPrevMonth = "‹ Prev"
    override val calendarNextMonth = "Next ›"
    override fun calendarMonthYear(month: Month, year: Int): String {
        return "${month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.ENGLISH)} $year"
    }
    override val weekdaysShort = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

    override fun chooseTablePromptForClubAndDate(clubTitle: String, formattedDate: String) =
        "Select a table at \"$clubTitle\" for $formattedDate:"
    override val notApplicable = "N/A" // Not Applicable or No Data

    override fun bookingStatusToText(status: BookingStatus): String {
        return when (status) {
            BookingStatus.NEW -> "New"
            // BookingStatus.PENDING -> "Pending" // PENDING was removed
            BookingStatus.CONFIRMED -> "Confirmed"
            BookingStatus.CANCELLED -> "Cancelled"
            BookingStatus.COMPLETED -> "Completed"
            BookingStatus.AWAITING_FEEDBACK -> "Awaiting Feedback"
            BookingStatus.ARCHIVED -> "Archived"
            // else -> "Unknown Status" // Optional
        }
    }
}


object StringProviderFactory {
    private val providers = mapOf(
        BotConstants.RUSSIAN_LANGUAGE_CODE to RussianStrings,
        BotConstants.ENGLISH_LANGUAGE_CODE to EnglishStrings
    )

    fun get(languageCode: String?): LocalizedStrings {
        return providers[languageCode?.lowercase()] ?: providers[BotConstants.DEFAULT_LANGUAGE_CODE]!! // Fallback to default
    }

    fun allLanguages(): List<LocalizedStrings> = providers.values.toList()
}