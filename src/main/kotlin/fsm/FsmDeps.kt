package fsm

import db.BookingsRepo
import db.ClubsRepo
import db.TablesRepo
import db.UsersRepo
import java.time.ZoneId



data class FsmDeps(
    val bot: BotFacade,
    val usersRepo: UsersRepo,
    val clubsRepo: ClubsRepo,
    val tablesRepo: TablesRepo,
    val bookingsRepo: BookingsRepo,
    // val slotsRepo: SlotsRepo, // TODO: Implement for actual slot fetching
    // val feedbackRepo: FeedbackRepo // TODO: Implement for storing feedback separately
    val defaultZoneId: ZoneId)