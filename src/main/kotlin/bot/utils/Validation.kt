package bot.utils

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * Утилитарные функции быстрой валидации пользовательского ввода.
 */
object Validation {

    /* ─────── Имя гостя ─────── */
    fun isValidGuestName(name: String?): Boolean =
        name != null &&
                name.isNotBlank() &&
                name.length in 2..50 &&
                name.none { it.isISOControl() }

    /* ─────── Номер телефона ─────── */
    private val PHONE_REGEX = Regex("^\\+?[0-9\\s\\-()]{7,20}$")
    fun isValidPhoneNumber(phone: String?): Boolean =
        phone != null && PHONE_REGEX.matches(phone)

    /* ─────── Количество гостей ─────── */
    fun isValidGuestCount(count: Int?, minGuests: Int = 1, maxGuests: Int = 20): Boolean =
        count != null && count in minGuests..maxGuests

    /* ─────── E-mail ─────── */
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")
    fun isValidEmail(email: String?): Boolean =
        email != null && EMAIL_REGEX.matches(email) && email.length <= 254

    /* ─────── Дата брони ─────── */
    fun isValidDate(dateStr: String?, maxDaysAhead: Long = 365): Boolean {
        if (dateStr == null) return false
        return runCatching {
            val date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
            val today = LocalDate.now()
            !date.isBefore(today) &&
                    abs(date.toEpochDay() - today.toEpochDay()) <= maxDaysAhead
        }.getOrDefault(false)
    }

    /* ─────── Время брони ─────── */
    fun isValidTime(
        timeStr: String?,
        start: LocalTime = LocalTime.of(18, 0),
        end: LocalTime = LocalTime.of(6, 0),
        endAfterMidnight: Boolean = true
    ): Boolean {
        if (timeStr == null) return false
        return runCatching {
            val t = LocalTime.parse(timeStr, DateTimeFormatter.ISO_LOCAL_TIME)
            if (!endAfterMidnight) {
                t in start..end
            } else {
                t >= start || t < end
            }
        }.getOrDefault(false)
    }

    /* ─────── Telegram Chat ID ─────── */
    fun isValidChatId(chatId: Long?): Boolean =
        chatId != null && chatId > 0
}