package fsm

import db.Booking
import db.BookingStatus
import db.TableInfo
import java.time.Instant

/** Черновик, который заполняем по ходу диалога */
data class DraftBooking(
    var clubId      : Int?        = null,
    var tableId     : Int?        = null,
    var guests      : Int?        = null,
    var slotStart   : Instant?    = null,
    var slotEnd     : Instant?    = null,
)

sealed interface BookingState {
    object Start                          : BookingState
    data class ChooseClub(val draft: DraftBooking)              : BookingState
    data class ChooseTable(
        val draft : DraftBooking,
        val tables: List<TableInfo>
    ) : BookingState
    data class EnterPeople(val draft: DraftBooking)             : BookingState
    data class ChooseSlot(val draft: DraftBooking)              : BookingState
    data class Confirm(val draft: DraftBooking)                 : BookingState
    object Finished                       : BookingState
    object Cancelled                      : BookingState
}

/** Перевод заполненного DraftBooking в сущность БД */
fun DraftBooking.toBooking(userId: Int? = null): Booking =
    Booking(
        id          = 0,                     // будет проставлен в репо
        clubId      = clubId!!,
        tableId     = tableId!!,
        userId      = userId,               // заполняем в handler-е, если нужно
        guestsCount = guests!!,
        dateStart   = slotStart!!,
        dateEnd     = slotEnd!!,
        status      = BookingStatus.NEW,
        comment     = null,
        createdAt   = Instant.now()
    )