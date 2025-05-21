package fsm

/** Входящие события из Telegram или внутренних вызовов */
sealed interface BookingEvent {
    object StartCmd              : BookingEvent          // /book
    data class ClubChosen(val id: Int)        : BookingEvent
    data class TableChosen(val id: Int)       : BookingEvent
    data class PeopleEntered(val count: Int)  : BookingEvent
    data class SlotChosen(val start: java.time.Instant,
                          val end  : java.time.Instant)  : BookingEvent
    object BackPressed           : BookingEvent          // «Назад»
    object CancelPressed         : BookingEvent
    object ConfirmPressed        : BookingEvent
}