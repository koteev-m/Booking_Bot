package fsm

import db.repositaries.BookingsRepo
import db.repositaries.ClubsRepo
import db.repositaries.TablesRepo
import db.repositaries.UsersRepo
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