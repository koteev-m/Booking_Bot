package bot

import bot.facade.TelegramBotFacadeImpl
import fsm.FsmStates
import db.repositaries.ClubsRepoImpl
import db.repositaries.TablesRepoImpl
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.logging.LogLevel as TelegramLogLevel
import db.repositaries.BookingsRepoImpl
import db.repositaries.UsersRepoImpl
import fsm.FsmDeps
import fsm.FsmHandler
import kotlinx.coroutines.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

val appCoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
val logger: Logger = LoggerFactory.getLogger("com.example.bot.Main")

fun migrateDatabase(jdbcUrl: String, user: String, password: String) {
    logger.info("Running database migrations via Flyway…")
    Flyway.configure()
        .dataSource(jdbcUrl, user, password)
        .locations("classpath:db/migration")
        .load()
        .migrate()
    logger.info("Database migration complete!")
}

/**
 * Application entry-point.
 *
 * We keep `suspend` so that we can call the suspend version of `FsmStates.build()`
 * which expects a `CoroutineScope` and internally invokes suspend APIs.
 */
suspend fun main() {
    logger.info("==============================================")
    logger.info("Starting Telegram Bot Application…")
    logger.info("Version: ${System.getenv("APP_VERSION") ?: "N/A"}")
    logger.info("==============================================")

    /* ─── ENV ─── */
    val botToken = System.getenv("TELEGRAM_BOT_TOKEN") ?: run {
        logger.error("FATAL: TELEGRAM_BOT_TOKEN not set in environment variables!")
        exitProcess(1)
    }
    val dbHost          = System.getenv("POSTGRES_HOST") ?: "localhost"
    val dbPort          = System.getenv("POSTGRES_PORT")?.toIntOrNull() ?: 5432
    val dbName          = System.getenv("POSTGRES_DB") ?: "club_booking_bot_db_v2"
    val dbUser          = System.getenv("POSTGRES_USER") ?: "clubadmin"
    val dbPassword      = System.getenv("POSTGRES_PASSWORD") ?: "supersecret"
    val defaultTzStr    = System.getenv("DEFAULT_TIMEZONE") ?: "Europe/Moscow"
    val defaultTimeZone = runCatching { ZoneId.of(defaultTzStr) }
        .getOrElse {
            logger.warn("Invalid DEFAULT_TIMEZONE '$defaultTzStr', defaulting to Europe/Moscow. ${it.message}")
            ZoneId.of("Europe/Moscow")
        }

    val telegramLogLevelEnv = System.getenv("TELEGRAM_LOG_LEVEL") ?: "Error"
    val telegramLogLevel = runCatching {
        TelegramLogLevel.valueOf(telegramLogLevelEnv.uppercase())
    }.getOrElse {
        logger.warn("Invalid TELEGRAM_LOG_LEVEL '$telegramLogLevelEnv', defaulting to Error.")
        TelegramLogLevel.Error
    }

    logger.info("Configuration: DB_HOST=$dbHost, DB_PORT=$dbPort, DB_NAME=$dbName, DEFAULT_TIMEZONE=$defaultTimeZone, TELEGRAM_LOG_LEVEL=$telegramLogLevel")

    /* ─── 1. Flyway ─── */
    val jdbcUrl = "jdbc:postgresql://$dbHost:$dbPort/$dbName"
    migrateDatabase(jdbcUrl, dbUser, dbPassword)

    /* ─── 2. Exposed ─── */
    Database.connect(jdbcUrl, driver = "org.postgresql.Driver", user = dbUser, password = dbPassword)
    logger.info("Database (Exposed) connected.")

    /* ─── 3. Telegram bot ─── */
    val telegramBot = bot {
        this.token           = botToken
        this.logLevel        = telegramLogLevel
        this.dispatchTimeout = TimeUnit.SECONDS.toMillis(60)
    }
    logger.info("Telegram bot instance configured.")

    /* ─── 4. Repositories & FSM deps ─── */
    val bookingsRepo = BookingsRepoImpl()
    val usersRepo    = UsersRepoImpl()
    val clubsRepo    = ClubsRepoImpl()
    val tablesRepo   = TablesRepoImpl(bookingsRepo)

    val botFacade = TelegramBotFacadeImpl(telegramBot, defaultTimeZone)
    val fsmDeps   = FsmDeps(botFacade, usersRepo, clubsRepo, tablesRepo, bookingsRepo, defaultTimeZone)
    val fsmHandler = FsmHandler(fsmDeps)
    logger.info("FSM dependencies and handler initialized.")

    /* ─── 5. Build & start FSM ─── */
    val bookingFsm = FsmStates.build(appCoroutineScope)   // suspend call
    bookingFsm.start()                                   // suspend as well
    logger.info("Booking FSM started.")

    /* ─── 6. Updates listener ─── */
    telegramBot.setUpdatesListener(
        onError = { error ->
            logger.error("Telegram updates listener polling error: ${error.message}", error.exception)
        },
        onUpdate = { updates ->
            updates.forEach { update ->
                appCoroutineScope.launch(CoroutineName("Update-${update.updateId}")) {
                    val updateIdForLog = update.updateId
                    try {
                        logger.debug("Received update (ID: {})", updateIdForLog)
                        fsmHandler.handleUpdate(update)
                    } catch (e: Throwable) {
                        logger.error("Critical error during update (ID: $updateIdForLog): ${e.message}", e)
                        update.message?.chat?.id?.let { cid ->
                            launch {
                                telegramBot.sendMessage(ChatId.fromId(cid), "⚠️ Internal error. Please try again later.")
                            }
                        }
                    }
                }
            }
        }
    )

    /* ─── 7. Start polling ─── */
    try {
        telegramBot.startPolling()
        logger.info("Bot started polling. Running in $defaultTimeZone time-zone.")
    } catch (e: Exception) {
        logger.error("FATAL: Failed to start Telegram polling: ${e.message}", e)
        exitProcess(1)
    }
}

















//@file:Suppress("unused")
//
///*  Kotlin 2.1.20 • Ktor 3.1.2 • Coroutines 1.10.2 • Serialization 2.1.0  */
//
//import io.github.cdimascio.dotenv.dotenv
//import com.github.kotlintelegrambot.Bot
//import com.github.kotlintelegrambot.bot
//import com.github.kotlintelegrambot.dispatch
//import com.github.kotlintelegrambot.dispatcher.callbackQuery
//import com.github.kotlintelegrambot.dispatcher.command
//import com.github.kotlintelegrambot.dispatcher.message
//import com.github.kotlintelegrambot.entities.ChatId
//import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
//import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
//import com.github.kotlintelegrambot.logging.LogLevel
//import io.ktor.client.*
//import io.ktor.client.call.*
//import io.ktor.client.engine.cio.*
//import io.ktor.client.plugins.*
//import io.ktor.client.plugins.contentnegotiation.*
//import io.ktor.client.request.*
//import io.ktor.http.*
//import io.ktor.serialization.kotlinx.json.*
//import kotlinx.coroutines.*
//import kotlinx.coroutines.Dispatchers.IO
//import kotlinx.serialization.Serializable
//import kotlinx.serialization.builtins.ListSerializer
//import kotlinx.serialization.json.Json
//import kotlinx.serialization.json.jsonArray
//import kotlinx.serialization.json.jsonObject
//import kotlinx.serialization.json.jsonPrimitive
//
///* ─── .env ─── */
//private val env = dotenv { ignoreIfMissing = true }
//private val TELEGRAM_BOT_TOKEN = env["TELEGRAM_BOT_TOKEN"] ?: error("token?")
//private val GOOGLE_SCRIPT_URL  = env["GOOGLE_SCRIPT_URL"]  ?: error("script url?")
//private val SECRET_TOKEN       = env["SECRET_TOKEN"]       ?: error("secret token?")
//private val CLUB_CHANNEL_ID    = env["CLUB_CHANNEL_ID"]?.toLongOrNull()
//    ?: error("channel id?")
//
///* ─── Ktor-result ─── */
//sealed interface KtResult<out T> {
//    data class Success<out T>(val value: T) : KtResult<T>
//    data class Failure(val reason: String, val cause: Throwable? = null) : KtResult<Nothing>
//}
//
///* ─── Ktor-client ─── */
//private val ktor = HttpClient(CIO) {
//    install(ContentNegotiation) {
//        json(Json { ignoreUnknownKeys = true; explicitNulls = false })
//    }
//    install(HttpRedirect) { /* оставляем дефолт: редиректы только GET/HEAD */ }
//}
//
///* ─── DTO ─── */
//@Serializable data class TableInfo(val tableName:String,val capacity:Int,val nominal:Int,val isFree:Boolean)
//@Serializable data class ApiResponse<T>(val status:String,val data:T?=null,val message:String?=null)
//@Serializable data class BookingPayload(
//    val date:String,val table:String,val guestName:String,
//    val guestCount:Int,val time:String,val cost:Int,val contact:String)
//
///* ─── Runtime ─── */
//enum class BookingStep { SELECT_DATE,SELECT_TABLE,ENTER_PEOPLE,ENTER_ARRIVAL_TIME,ENTER_GUEST_NAME,ENTER_CONTACT,CONFIRMED }
//
//data class BookingSession(
//    var step:BookingStep = BookingStep.SELECT_DATE,
//    var date:String?=null,
//    var tables:List<TableInfo>?=null,
//    var table:TableInfo?=null,
//    var guests:Int?=null,
//    var time:String?=null,
//    var name:String?=null,
//    var contact:String?=null,
//    var cost:Int?=null
//)
//
//private val sessions = mutableMapOf<Long, BookingSession>()
//
///* ═══════════════════ MAIN ═══════════════════ */
//fun main() = runBlocking {
//    val bot = bot {
//        token = TELEGRAM_BOT_TOKEN
//        logLevel = LogLevel.Network.Body
//
//        dispatch {
//            /* /start */
//            command("start") {
//                val id = message.chat.id
//                sessions[id] = BookingSession()
//
//                when (val res = fetchDates()) {
//                    is KtResult.Failure -> bot.sendMessage(ChatId.fromId(id),"❌ ${res.reason}")
//                    is KtResult.Success -> {
//                        if (res.value.isEmpty()) {
//                            bot.sendMessage(ChatId.fromId(id),"Нет доступных дат."); return@command
//                        }
//                        val rows = res.value.chunked(3).map { c ->
//                            c.map { d -> InlineKeyboardButton.CallbackData(d,"date:$d") } }
//                        bot.sendMessage(ChatId.fromId(id),"Выберите дату:",
//                            replyMarkup = InlineKeyboardMarkup.create(rows))
//                    }
//                }
//            }
//
//            /* callbacks */
//            callbackQuery {
//                val chat = callbackQuery.from.id
//                val data = callbackQuery.data ?: return@callbackQuery
//                val s    = sessions.getOrPut(chat) { BookingSession() }
//
//                when {
//                    data.startsWith("date:")  -> onDate(bot, chat, s, data.removePrefix("date:"))
//                    data.startsWith("table:") -> onTable(bot, chat, s, data.removePrefix("table:"))
//                }
//            }
//
//            /* text */
//            message {
//                val id  = message.chat.id
//                val txt = message.text ?: return@message
//                if (txt.startsWith("/")) return@message
//                val s = sessions[id] ?: return@message
//
//                when (s.step) {
//                    BookingStep.ENTER_PEOPLE       -> onPeople(bot,id,s,txt)
//                    BookingStep.ENTER_ARRIVAL_TIME -> onTime(bot,id,s,txt)
//                    BookingStep.ENTER_GUEST_NAME   -> onName(bot,id,s,txt)
//                    BookingStep.ENTER_CONTACT      -> onContact(bot,id,s,txt)
//                    else -> {}
//                }
//            }
//        }
//    }
//    bot.startPolling()
//}
//
///* ─── callbacks ─── */
//private fun onDate(bot:Bot,id:Long,s:BookingSession,date:String){
//    s.date=date; s.step=BookingStep.SELECT_TABLE
//    GlobalScope.launch(IO){
//        when(val res=fetchTables(date)){
//            is KtResult.Failure-> bot.sendMessage(ChatId.fromId(id),"❌ ${res.reason}")
//            is KtResult.Success->{
//                val free=res.value.filter{it.isFree}
//                if(free.isEmpty()){bot.sendMessage(ChatId.fromId(id),"На $date свободных столов нет.");sessions.remove(id);return@launch}
//                s.tables=free
//                val rows=free.chunked(3).map{c->c.map{t->InlineKeyboardButton.CallbackData("${t.tableName} (${t.capacity})","table:${t.tableName}")}}
//                bot.sendMessage(ChatId.fromId(id),"Выберите стол:",replyMarkup=InlineKeyboardMarkup.create(rows))
//            }
//        }
//    }
//}
//
//private fun onTable(bot:Bot,id:Long,s:BookingSession,name:String){
//    val t=s.tables?.find{it.tableName==name}?:run{bot.sendMessage(ChatId.fromId(id),"Стол не найден.");sessions.remove(id);return}
//    s.table=t; s.step=BookingStep.ENTER_PEOPLE
//    bot.sendMessage(ChatId.fromId(id),"Сколько гостей будет?")
//}
//
///* ─── text ─── */
//private fun onPeople(bot:Bot,id:Long,s:BookingSession,txt:String){
//    val n=txt.toIntOrNull()?:0; if(n<=0){bot.sendMessage(ChatId.fromId(id),"Введите число гостей >0");return}
//    s.guests=n; val t=s.table!!; s.cost=if(n<=t.capacity)t.nominal else t.nominal+5000*(n-t.capacity)
//    s.step=BookingStep.ENTER_ARRIVAL_TIME; bot.sendMessage(ChatId.fromId(id),"Время прибытия (ЧЧ:ММ)?")
//}
//
//private fun onTime(bot:Bot,id:Long,s:BookingSession,txt:String){
//    if(!Regex("^([01]?\\d|2[0-3]):[0-5]\\d$").matches(txt)){bot.sendMessage(ChatId.fromId(id),"Формат HH:MM");return}
//    s.time=txt; s.step=BookingStep.ENTER_GUEST_NAME; bot.sendMessage(ChatId.fromId(id),"Имя гостя?")
//}
//
//private fun onName(bot:Bot,id:Long,s:BookingSession,txt:String){
//    if(txt.isBlank()){bot.sendMessage(ChatId.fromId(id),"Имя не может быть пустым");return}
//    s.name=txt; s.step=BookingStep.ENTER_CONTACT; bot.sendMessage(ChatId.fromId(id),"Контакт?")
//}
//
//private fun onContact(bot:Bot,id:Long,s:BookingSession,txt:String){
//    if(txt.isBlank()){bot.sendMessage(ChatId.fromId(id),"Контакт не может быть пустым");return}
//    s.contact=txt; s.step=BookingStep.CONFIRMED
//
//    GlobalScope.launch(IO){
//        val p=BookingPayload(s.date!!,s.table!!.tableName,s.name!!,s.guests!!,s.time!!,s.cost!!,s.contact!!)
//        when(val res=sendBooking(p)){
//            is KtResult.Failure-> bot.sendMessage(ChatId.fromId(id),"❌ ${res.reason}")
//            is KtResult.Success->{
//                val msg="✅ Бронь сохранена!\n${summary(s)}"
//                bot.sendMessage(ChatId.fromId(id),msg)
//                bot.sendMessage(ChatId.fromId(CLUB_CHANNEL_ID), "🔔 Новая бронь!\n${summary(s)}")
//                sessions.remove(id)
//            }
//        }
//    }
//}
//
///* ───── HTTP helpers ───── */
//
//private fun url(action: String) = URLBuilder(GOOGLE_SCRIPT_URL).apply {
//    parameters["secretToken"] = SECRET_TOKEN
//    parameters["action"]      = action
//}
//
///* 1. Список дат */
//suspend fun fetchDates(): KtResult<List<String>> = try {
//    val raw = ktor.get(url("getAvailableDates").build()).body<String>()
//    val json = Json.parseToJsonElement(raw).jsonObject
//    if (json["status"]?.jsonPrimitive?.content == "success") {
//        val list = json["data"]!!.jsonArray.map { it.jsonPrimitive.content }
//        KtResult.Success(list)
//    } else {
//        KtResult.Failure(json["message"]?.jsonPrimitive?.content ?: "API error")
//    }
//} catch (e: Exception) {
//    KtResult.Failure(e.localizedMessage ?: "HTTP error", e)
//}
//
///* 2. Таблицы для выбранной даты */
//suspend fun fetchTables(date: String): KtResult<List<TableInfo>> = try {
//    val u = url("getTableData").apply { parameters["date"] = date }.build()
//    val raw = ktor.get(u).body<String>()
//    val ser = ApiResponse.serializer(ListSerializer(TableInfo.serializer()))
//    val resp: ApiResponse<List<TableInfo>> = Json.decodeFromString(ser, raw)
//    if (resp.status == "success" && resp.data != null) {
//        KtResult.Success(resp.data)
//    } else {
//        KtResult.Failure(resp.message ?: "API error")
//    }
//} catch (e: Exception) {
//    KtResult.Failure(e.localizedMessage ?: "HTTP error", e)
//}
//
///* 3. Сохранение брони */
//suspend fun sendBooking(p: BookingPayload): KtResult<Unit> {
//    return try {
//        /* 1-я попытка — POST /exec */
//        val firstUrl = url("updateBooking").apply { parameters["devMode"] = "false" }.build()
//
//        val r1 = ktor.post(firstUrl) {
//            contentType(ContentType.Application.Json)
//            setBody(p)
//            expectSuccess = false          // нужны 30x-коды
//        }
//
//        /* если редиректа нет — разбираем ответ как обычно */
//        if (r1.status == HttpStatusCode.OK) {
//            val body = r1.body<ApiResponse<Unit>>()
//            return if (body.status == "success")
//                KtResult.Success(Unit) else KtResult.Failure(body.message ?: "API error")
//        }
//
//        /* 2-й шаг: получили 302 → дёргаем Location уже GET-ом */
//        if (r1.status.value in listOf(301, 302, 303, 307, 308)) {
//            val loc = r1.headers[HttpHeaders.Location]
//                ?: return KtResult.Failure("302 без Location-заголовка")
//
//            val r2 = ktor.get(loc) { expectSuccess = false }
//
//            if (r2.status == HttpStatusCode.OK) {
//                val body = r2.body<ApiResponse<Unit>>()
//                return if (body.status == "success")
//                    KtResult.Success(Unit) else KtResult.Failure(body.message ?: "API error")
//            }
//            return KtResult.Failure("HTTP ${r2.status.value}")
//        }
//
//        KtResult.Failure("HTTP ${r1.status.value}")      // неожиданный код
//    } catch (e: Exception) {
//        KtResult.Failure(e.localizedMessage ?: "HTTP error", e)
//    }
//}
//
///* ─── util ─── */
//private fun summary(s:BookingSession)= """
//    Дата: ${s.date}
//    Стол: ${s.table?.tableName}
//    Гостей: ${s.guests}
//    Время: ${s.time}
//    Стоимость: ${s.cost}₽
//    Имя: ${s.name}
//    Контакт: ${s.contact}
//""".trimIndent()