import io.github.cdimascio.dotenv.dotenv
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.logging.LogLevel
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/* ───────── СЕКРЕТЫ ───────── */
private val dotEnv = dotenv { ignoreIfMissing = true }

private val TELEGRAM_BOT_TOKEN: String =
    System.getenv("TELEGRAM_BOT_TOKEN") ?: dotEnv["TELEGRAM_BOT_TOKEN"]
    ?: error("TELEGRAM_BOT_TOKEN not set")

private val GOOGLE_SCRIPT_URL: String =
    System.getenv("GOOGLE_SCRIPT_URL") ?: dotEnv["GOOGLE_SCRIPT_URL"]
    ?: error("GOOGLE_SCRIPT_URL not set")

/* удаляем невидимые символы, чтобы toHttpUrlOrNull() не вернул null */
private val BASE_SCRIPT_URL = GOOGLE_SCRIPT_URL.trim()

private val CLUB_CHANNEL_ID: Long =
    (System.getenv("CLUB_CHANNEL_ID") ?: dotEnv["CLUB_CHANNEL_ID"])
        ?.toLongOrNull()
        ?: error("CLUB_CHANNEL_ID not set or invalid")

private val API_SECRET_TOKEN: String? =
    System.getenv("API_SECRET_TOKEN") ?: dotEnv["API_SECRET_TOKEN"]

/* ───────── МОДЕЛИ ───────── */
enum class BookingStep {
    SELECT_DATE, SELECT_TABLE, ENTER_PEOPLE, ENTER_ARRIVAL_TIME,
    ENTER_GUEST_NAME, ENTER_CONTACT, CONFIRMED, ERROR
}

data class TableInfo(
    val tableName: String,
    val capacity: Int,
    val nominal: Int,
    val isFree: Boolean            // ← флаг «Нет/Да» из колонки I
)

data class BookingSession(
    var step: BookingStep,
    var date: String? = null,
    var availableTables: List<TableInfo>? = null,
    var selectedTable: TableInfo? = null,
    var people: Int? = null,
    var arrivalTime: String? = null,
    var guestName: String? = null,
    var contactInfo: String? = null,
    var calculatedCost: Int? = null
)

data class BookingDataPayload(
    val date: String?, val table: String?, val guestName: String?,
    val guestCount: Int?, val time: String?, val cost: Int?, val contact: String?
)

data class ApiResponse<T>(val status: String?, val data: T?, val message: String?)
data class ExistsResponse(val exists: Boolean)
data class FreeResponse(val isFree: Boolean)

/* ───────── ГЛОБАЛ ───────── */
private val sessions = mutableMapOf<Long, BookingSession>()

private val httpClient: OkHttpClient = OkHttpClient.Builder()
    .addInterceptor(HttpLoggingInterceptor().apply { setLevel(HttpLoggingInterceptor.Level.BODY) })
    .build()

private val gson = Gson()

/* ───────── main ───────── */
fun main() {
    println("DEBUG GOOGLE_SCRIPT_URL=$BASE_SCRIPT_URL")   // ← удобно при запуске


    val bot = bot {
        token = TELEGRAM_BOT_TOKEN
        logLevel = LogLevel.Network.Body
        dispatch {
            command("start") {
                sessions.remove(message.chat.id)
                bot.sendMessage(
                    ChatId.fromId(message.chat.id),
                    "Привет! Я бот для бронирования столиков.\n" +
                            "Введите дату в формате дд.мм.гггг, например 16.05.2025."
                )
                sessions[message.chat.id] = BookingSession(step = BookingStep.SELECT_DATE)
            }
            message { processUpdate(bot, message) }
        }
    }
    bot.startPolling()
}

/* ───────── HELPERS ───────── */
private fun Bot.sendLongMessage(chatId: Long, text: String, max: Int = 4000) {
    var pos = 0
    while (pos < text.length) {
        val chunk = text.substring(pos, minOf(pos + max, text.length))
        sendMessage(ChatId.fromId(chatId), chunk)
        pos += max
    }
}

/* ───────── ОБРАБОТКА АПДЕЙТОВ ───────── */
private fun processUpdate(bot: Bot, message: Message) {
    val chatId = message.chat.id
    val text = message.text?.trim() ?: return

    println("DEBUG processUpdate: step=${sessions[chatId]?.step} text='$text'")
    /* ➊ команду (/start /help …) не валидируем как дату */
    if (text.startsWith("/")) return

    val session = sessions.getOrPut(chatId) { BookingSession(step = BookingStep.SELECT_DATE) }

    if (text.equals("назад", true)) { handleBack(bot, chatId, session); return }

    when (session.step) {
        BookingStep.SELECT_DATE        -> handleDateSelection(bot, chatId, session, text)
        BookingStep.SELECT_TABLE       -> handleTableSelection(bot, chatId, session, text)
        BookingStep.ENTER_PEOPLE       -> handlePeopleInput(bot, chatId, session, text)
        BookingStep.ENTER_ARRIVAL_TIME -> handleTimeInput(bot, chatId, session, text)
        BookingStep.ENTER_GUEST_NAME   -> handleNameInput(bot, chatId, session, text)
        BookingStep.ENTER_CONTACT      -> handleContactInput(bot, chatId, session, text)
        BookingStep.CONFIRMED          -> bot.sendMessage(ChatId.fromId(chatId),
            "Бронирование уже подтверждено. /start для нового.")
        BookingStep.ERROR              -> {
            bot.sendMessage(ChatId.fromId(chatId),
                "Произошла ошибка. Начните заново с /start.")
            sessions.remove(chatId)
        }
    }
}

/* ───────── 1. ВЫБОР ДАТЫ ───────── */
private fun handleDateSelection(bot: Bot, chatId: Long, session: BookingSession, text: String) {
    println("DEBUG handleDateSelection: parsing text='$text'")
    val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    val parsed = runCatching { LocalDate.parse(text, fmt) }.getOrNull()
    if (parsed == null) {
        bot.sendMessage(ChatId.fromId(chatId),
            "Неверный формат даты. Введите дд.мм.гггг или «Назад».")
        return
    }
    session.date = text

    when (val exists = checkSheetExists(text)) {
        is ApiResult.Error   -> bot.sendMessage(ChatId.fromId(chatId),
            "Ошибка проверки даты: ${exists.message}")
        is ApiResult.Success -> {
            if (!exists.data) {
                bot.sendMessage(ChatId.fromId(chatId),
                    "Лист с датой $text не найден. Введите другую дату.")
                session.date = null; return
            }
            when (val tables = getTableDataFromSheet(text)) {
                is ApiResult.Error -> bot.sendMessage(ChatId.fromId(chatId),
                    "Не удалось получить столы: ${tables.message}")
                is ApiResult.Success -> {
                    val freeTables = tables.data.filter { it.isFree }
                    if (freeTables.isEmpty()) {
                        bot.sendMessage(ChatId.fromId(chatId),
                            "На $text нет свободных столов. Попробуйте другой день.")
                        session.date = null; return
                    }
                    session.availableTables = freeTables
                    session.step = BookingStep.SELECT_TABLE
                    val list = freeTables.joinToString("\n") {
                        "- ${it.tableName} (вмест: ${it.capacity}, цена: ${it.nominal})"
                    }
                    bot.sendLongMessage(chatId, """
                        Дата: $text
                        Свободные столы:
                        $list
                        
                        Введите название столика.
                    """.trimIndent())
                }
            }
        }
    }
}

/* ───────── 2. ВЫБОР СТОЛА ───────── */
private fun handleTableSelection(bot: Bot, chatId: Long, session: BookingSession, text: String) {
    val chosen = session.availableTables?.find { it.tableName.equals(text, true) }
    if (chosen == null) {
        val names = session.availableTables?.joinToString(", ") { it.tableName } ?: "Нет данных"
        bot.sendMessage(ChatId.fromId(chatId),
            "Стол \"$text\" не найден. Выберите: $names или «Назад».")
        return
    }

    when (val free = isTableFree(session.date!!, chosen.tableName)) {
        is ApiResult.Error   -> bot.sendMessage(ChatId.fromId(chatId),
            "Ошибка проверки стола: ${free.message}")
        is ApiResult.Success -> {
            if (!free.data) {
                bot.sendMessage(ChatId.fromId(chatId),
                    "Стол \"${chosen.tableName}\" уже занят. Выберите другой или «Назад».")
                return
            }
            session.selectedTable = chosen
            session.step = BookingStep.ENTER_PEOPLE
            bot.sendMessage(ChatId.fromId(chatId),
                "Стол: ${chosen.tableName}\nВведите количество гостей или «Назад».")
        }
    }
}

/* ───────── 3. КОЛ-ВО ГОСТЕЙ ───────── */
private fun handlePeopleInput(bot: Bot, chatId: Long, session: BookingSession, text: String) {
    val count = text.toIntOrNull()
    if (count == null || count <= 0) {
        bot.sendMessage(ChatId.fromId(chatId),
            "Введите корректное число гостей (>0) или «Назад».")
        return
    }

    session.people = count
    val info = session.selectedTable!!
    session.calculatedCost = if (count <= info.capacity)
        info.nominal else info.nominal + 5000 * (count - info.capacity)

    session.step = BookingStep.ENTER_ARRIVAL_TIME
    bot.sendMessage(ChatId.fromId(chatId), """
        Гостей: $count
        Стоимость: ${session.calculatedCost}₽
        Введите время прибытия (ЧЧ:ММ) или «Назад».
    """.trimIndent())
}

/* ───────── 4. ВРЕМЯ ───────── */
private fun handleTimeInput(bot: Bot, chatId: Long, session: BookingSession, text: String) {
    if (!Regex("^([01]?\\d|2[0-3]):([0-5]\\d)$").matches(text)) {
        bot.sendMessage(ChatId.fromId(chatId),
            "Неверный формат времени. Введите ЧЧ:ММ или «Назад».")
        return
    }
    session.arrivalTime = text
    session.step = BookingStep.ENTER_GUEST_NAME
    bot.sendMessage(ChatId.fromId(chatId),
        "Время прибытия: $text\nВведите имя гостя или «Назад».")
}

/* ───────── 5. ИМЯ ───────── */
private fun handleNameInput(bot: Bot, chatId: Long, session: BookingSession, text: String) {
    if (text.isBlank()) {
        bot.sendMessage(ChatId.fromId(chatId),
            "Имя не может быть пустым. Введите имя или «Назад».")
        return
    }
    session.guestName = text
    session.step = BookingStep.ENTER_CONTACT
    bot.sendMessage(ChatId.fromId(chatId),
        "Имя гостя: $text\nВведите телефон или @username, либо «Назад».")
}

/* ───────── 6. КОНТАКТ ───────── */
private fun handleContactInput(bot: Bot, chatId: Long, session: BookingSession, text: String) {
    if (text.isBlank()) {
        bot.sendMessage(ChatId.fromId(chatId),
            "Контакт не может быть пустым. Введите данные или «Назад».")
        return
    }
    session.contactInfo = text

    val payload = BookingDataPayload(
        date = session.date,
        table = session.selectedTable?.tableName,
        guestName = session.guestName,
        guestCount = session.people,
        time = session.arrivalTime,
        cost = session.calculatedCost,
        contact = session.contactInfo
    )

    when (val result = updateGoogleSheet(payload)) {
        is ApiResult.Error   -> bot.sendMessage(ChatId.fromId(chatId),
            "Не удалось записать бронирование: ${result.message}")
        is ApiResult.Success -> {
            session.step = BookingStep.CONFIRMED
            bot.sendMessage(ChatId.fromId(chatId), """
                ✅ Бронирование подтверждено!
                Дата: ${session.date}
                Стол: ${session.selectedTable?.tableName}
                Гостей: ${session.people}
                Время: ${session.arrivalTime}
                Стоимость: ${session.calculatedCost}₽
                Гость: ${session.guestName}
                Контакт: ${session.contactInfo}
                
                Для нового бронирования отправьте /start.
            """.trimIndent())
            sendClubNotification(bot, session)
            sessions.remove(chatId)
        }
    }
}

/* ───────── «НАЗАД» ───────── */
private fun handleBack(bot: Bot, chatId: Long, session: BookingSession) {
    when (session.step) {
        BookingStep.SELECT_DATE, BookingStep.ERROR -> bot.sendMessage(
            ChatId.fromId(chatId),
            "Вы в начале. /start — чтобы начать заново."
        )

        BookingStep.SELECT_TABLE -> {
            session.step = BookingStep.SELECT_DATE
            session.date = null
            session.availableTables = null
            bot.sendMessage(ChatId.fromId(chatId),
                "Возврат к выбору даты. Введите дату (дд.мм.гггг).")
        }

        BookingStep.ENTER_PEOPLE -> {
            session.step = BookingStep.SELECT_TABLE
            session.selectedTable = null
            val list = session.availableTables?.joinToString("\n") {
                "- ${it.tableName} (вмест: ${it.capacity}, цена: ${it.nominal})"
            } ?: "Нет данных"
            bot.sendMessage(ChatId.fromId(chatId),
                "Возврат к выбору стола:\n$list")
        }

        BookingStep.ENTER_ARRIVAL_TIME -> {
            session.step = BookingStep.ENTER_PEOPLE
            session.people = null
            session.calculatedCost = null
            bot.sendMessage(ChatId.fromId(chatId),
                "Возврат к количеству гостей.")
        }

        BookingStep.ENTER_GUEST_NAME -> {
            session.step = BookingStep.ENTER_ARRIVAL_TIME
            session.arrivalTime = null
            bot.sendMessage(ChatId.fromId(chatId),
                "Возврат к времени прибытия (ЧЧ:ММ).")
        }

        BookingStep.ENTER_CONTACT -> {
            session.step = BookingStep.ENTER_GUEST_NAME
            session.guestName = null
            bot.sendMessage(ChatId.fromId(chatId),
                "Возврат к имени гостя.")
        }

        BookingStep.CONFIRMED -> bot.sendMessage(
            ChatId.fromId(chatId),
            "Бронирование уже подтверждено. /start для нового."
        )
    }
}

/* ───────── УВЕДОМЛЕНИЕ В КАНАЛ ───────── */
private fun sendClubNotification(bot: Bot, session: BookingSession) {
    val msg = """
        🔔 Новое бронирование!
        Дата: ${session.date}
        Стол: ${session.selectedTable?.tableName}
        Гостей: ${session.people}
        Время: ${session.arrivalTime}
        Стоимость: ${session.calculatedCost}₽
        Гость: ${session.guestName}
        Контакт: ${session.contactInfo}
    """.trimIndent()
    runCatching { bot.sendMessage(ChatId.fromId(CLUB_CHANNEL_ID), msg) }
        .onFailure { println("Не удалось отправить уведомление: ${it.message}") }
}

/* ───────── API RESULT ───────── */
sealed class ApiResult<out T> {
    data class Success<out T>(val data: T) : ApiResult<T>()
    data class Error(val message: String) : ApiResult<Nothing>()
}

/* ───────── 1. checkSheetExists ───────── */
private fun checkSheetExists(date: String): ApiResult<Boolean> {
    // 1. Выводим сырое значение и обрезанное
    println("🔍 DEBUG raw GOOGLE_SCRIPT_URL='${GOOGLE_SCRIPT_URL}'")
    println("🔍 DEBUG BASE_SCRIPT_URL='${BASE_SCRIPT_URL}'")

    // 2. Проверяем, что URL вообще парсится
    val trimmed = BASE_SCRIPT_URL.trim()
    val parsedUrl = trimmed.toHttpUrlOrNull()
    println("🔍 DEBUG trimmed.toHttpUrlOrNull() = $parsedUrl")

    // 3. Строим полный URL с параметрами
    val url = parsedUrl
        ?.newBuilder()
        ?.addQueryParameter("action", "checkSheetExists")
        ?.addQueryParameter("date", date)
        ?.apply { API_SECRET_TOKEN?.let { addQueryParameter("token", it) } }
        ?.build()
    println("🔍 DEBUG built URL = $url")

    // 4. Если не получилось — возвращаем ошибку URL
    if (url == null) {
        return ApiResult.Error("Неверный URL скрипта")
    }

    // 5. Делаем запрос и парсим ответ
    val request = Request.Builder().url(url).get().build()
    return httpClient.safeCall(request) { rawBody ->
        // Пробуем десериализовать JSON вида {"status":"success","data":{"exists":true},"message":null}
        val type = object : TypeToken<ApiResponse<ExistsResponse>>() {}.type
        val resp: ApiResponse<ExistsResponse>? = runCatching {
            gson.fromJson<ApiResponse<ExistsResponse>>(rawBody, type)
        }.getOrNull()

        if (resp != null && resp.status == "success" && resp.data != null) {
            ApiResult.Success(resp.data.exists)
        } else {
            // Если JSON неожиданный, но rawBody содержит простой true/false
            when (rawBody.trim().lowercase()) {
                "true"  -> ApiResult.Success(true)
                "false" -> ApiResult.Success(false)
                else    -> ApiResult.Error("Неожиданный ответ: $rawBody")
            }
        }
    }
}

/* ───────── 2. getTableData ───────── */
private fun getTableDataFromSheet(date: String): ApiResult<List<TableInfo>> {
    val url = BASE_SCRIPT_URL.toHttpUrlOrNull()?.newBuilder()
        ?.addQueryParameter("action", "getTableData")
        ?.addQueryParameter("date", date)
        ?.apply { API_SECRET_TOKEN?.let { addQueryParameter("token", it) } }
        ?.build() ?: return ApiResult.Error("Неверный URL скрипта")

    val req = Request.Builder().url(url).get().build()
    return httpClient.safeCall(req) { raw ->
        val type = object : TypeToken<ApiResponse<List<TableInfo>>>() {}.type
        val resp: ApiResponse<List<TableInfo>> = gson.fromJson(raw, type)
        if (resp.status == "success" && resp.data != null)
            ApiResult.Success(resp.data)
        else ApiResult.Error(resp.message ?: "Ошибка API")
    }
}

/* ───────── 3. isTableFree ───────── */
private fun isTableFree(date: String, table: String): ApiResult<Boolean> {
    val url = BASE_SCRIPT_URL.toHttpUrlOrNull()?.newBuilder()
        ?.addQueryParameter("action", "isTableFree")
        ?.addQueryParameter("date", date)
        ?.addQueryParameter("table", table)
        ?.apply { API_SECRET_TOKEN?.let { addQueryParameter("token", it) } }
        ?.build() ?: return ApiResult.Error("Неверный URL скрипта")

    val req = Request.Builder().url(url).get().build()
    return httpClient.safeCall(req) { raw ->
        val type = object : TypeToken<ApiResponse<FreeResponse>>() {}.type
        runCatching { gson.fromJson<ApiResponse<FreeResponse>>(raw, type) }.getOrNull()
            ?.let { if (it.status == "success" && it.data != null)
                return@safeCall ApiResult.Success(it.data.isFree) }

        when (raw.trim().lowercase()) {
            "true"  -> ApiResult.Success(true)
            "false" -> ApiResult.Success(false)
            else    -> ApiResult.Error("Неожиданный ответ: $raw")
        }
    }
}

/* ───────── 4. updateBooking ───────── */
private fun updateGoogleSheet(payload: BookingDataPayload): ApiResult<String> {
    val url = BASE_SCRIPT_URL.toHttpUrlOrNull()?.newBuilder()
        ?.addQueryParameter("action", "updateBooking")
        ?.apply { API_SECRET_TOKEN?.let { addQueryParameter("token", it) } }
        ?.build() ?: return ApiResult.Error("Неверный URL скрипта")

    val body = gson.toJson(payload)
        .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

    val req = Request.Builder().url(url).post(body).build()
    return httpClient.safeCall(req) { raw ->
        val type = object : TypeToken<ApiResponse<Unit>>() {}.type
        val resp: ApiResponse<Unit> = gson.fromJson(raw, type)
        if (resp.status == "success")
            ApiResult.Success(resp.message ?: "OK")
        else ApiResult.Error(resp.message ?: "Ошибка API")
    }
}

/* ───────── safeCall ───────── */
private inline fun <T> OkHttpClient.safeCall(
    request: Request,
    onBody: (String) -> ApiResult<T>
): ApiResult<T> = try {
    newCall(request).execute().use { resp ->
        if (!resp.isSuccessful) ApiResult.Error("HTTP ${resp.code}") else {
            val body = resp.body?.string() ?: return ApiResult.Error("Пустой ответ")
            onBody(body)
        }
    }
} catch (e: IOException) {
    ApiResult.Error("Сетевая ошибка: ${e.message}")
} catch (e: JsonSyntaxException) {
    ApiResult.Error("Ошибка JSON: ${e.message}")
} catch (e: Exception) {
    ApiResult.Error("Ошибка: ${e.message}")
}