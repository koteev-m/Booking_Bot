package bot.facade

object BotConstants {
    const val DEFAULT_LANGUAGE_CODE = "ru" // Русский по умолчанию
    const val ENGLISH_LANGUAGE_CODE = "en"
    const val RUSSIAN_LANGUAGE_CODE = "ru"

    const val MAX_GUESTS_DEFAULT = 20 // Максимальное количество гостей по умолчанию
    const val MIN_GUESTS_DEFAULT = 1  // Минимальное количество гостей

    // Добавлено для TelegramBotFacadeImpl
    const val MAX_CLUBS_ON_MAIN_MENU_KEYBOARD = 2
    const val CALENDAR_MONTHS_AHEAD_LIMIT = 3 // Например, показывать календарь на 3 месяца вперед

    // Callback Data Prefixes - должны быть языконезависимыми
    const val CB_PREFIX_SELECT_LANG = "select_lang:"
    const val CB_PREFIX_BOOK_CLUB = "book_club:"
    const val CB_PREFIX_CHOOSE_TABLE = "table:"
    const val CB_PREFIX_CHOOSE_DATE_CAL = "cal_date:"
    const val CB_PREFIX_CAL_MONTH_CHANGE = "cal_month:" // prev_YYYY-MM или next_YYYY-MM
    const val CB_PREFIX_CHOOSE_SLOT = "slot:"
    const val CB_PREFIX_CONFIRM_BOOKING = "confirm_booking"
    const val CB_PREFIX_CANCEL_ACTION = "cancel_action"
    const val CB_PREFIX_BACK_TO = "back_to:"

    const val CB_PREFIX_VENUE_INFO_SHOW = "venue_info:"
    const val CB_PREFIX_VENUE_POSTERS = "venue_posters:"
    const val CB_PREFIX_VENUE_PHOTOS = "venue_photos:"

    const val CB_PREFIX_MY_BOOKINGS_LIST = "my_bookings_list"
    const val CB_PREFIX_MANAGE_BOOKING = "manage_booking:"
    const val CB_PREFIX_DO_CANCEL_BOOKING = "do_cancel_bk:"
    const val CB_PREFIX_DO_CHANGE_BOOKING = "do_change_bk:"
    const val CB_PREFIX_RATE_BOOKING = "rate_bk:" // Для обратной связи

    const val CB_MAIN_MENU_VENUE_INFO = "main_menu_venue_info"
    const val CB_MAIN_MENU_MY_BOOKINGS = "main_menu_my_bookings"
    const val CB_MAIN_MENU_ASK_QUESTION = "main_menu_ask_question"
    const val CB_MAIN_MENU_OPEN_APP = "main_menu_open_app" // Пока не используется активно
    const val CB_MAIN_MENU_HELP = "main_menu_help"
    const val CB_MAIN_MENU_CHANGE_LANG = "main_menu_change_lang"

    const val TABLE_LAYOUT_PLACEHOLDER_URL = "https://placehold.co/600x400/E0E0E0/555555?text=Floor+Plan%0A(Table+Numbers+Here)"
    const val POINTS_PER_GUEST = 10
    const val POINTS_FOR_FEEDBACK = 50
}