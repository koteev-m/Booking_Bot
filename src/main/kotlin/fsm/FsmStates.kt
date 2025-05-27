package fsm // Corrected package

import ru.nsk.kstatemachine.IState
import ru.nsk.kstatemachine.initialState
import kotlin.getValue
import ru.nsk.kstatemachine.finalState as createFinalState
import ru.nsk.kstatemachine.state as createState

object FsmStates {
    val Initial by lazy { initialState("InitialLoadingState") }
    val MainMenu by lazy { createState("MainMenuState") }
    val ChangeLanguage by lazy { createState("ChangeLanguageState") }

    val ChooseClub by lazy { createState("ChooseClubState") }
    val ChooseDate by lazy { createState("ChooseDateState") }
    val ChooseTable by lazy { createState("ChooseTableState") }
    val EnterPeople by lazy { createState("EnterPeopleState") }
    val ChooseSlot by lazy { createState("ChooseSlotState") }
    val EnterGuestName by lazy { createState("EnterGuestNameState") }
    val EnterGuestPhone by lazy { createState("EnterGuestPhoneState") }
    val ConfirmBooking by lazy { createState("ConfirmBookingState") }
    val BookingFinished by lazy { createFinalState("BookingFinishedState") }

    val ShowVenueList by lazy { createState("ShowVenueListState") }
    val ShowVenueDetails by lazy { createState("ShowVenueDetailsState") }

    val ShowMyBookingsList by lazy { createState("ShowMyBookingsListState") }
    val ManageBookingOptions by lazy { createState("ManageBookingOptionsState") }
    val AskFeedback by lazy { createState("AskFeedbackState") }
    val BookingActionFinished by lazy { createFinalState("BookingActionFinishedState") }


    val AskQuestionInput by lazy { createState("AskQuestionInputState") }
    val QuestionSent by lazy { createFinalState("QuestionSentState") }

    val ShowHelp by lazy { createState("ShowHelpState") }

    val ActionCancelled by lazy { createFinalState("ActionCancelledState") }
    val ErrorOccurred by lazy { createFinalState("ErrorOccurredState") }

    fun getAllStates(): List<IState> = listOf(
        Initial, MainMenu, ChangeLanguage, ChooseClub, ChooseDate, ChooseTable, EnterPeople, ChooseSlot,
        EnterGuestName, EnterGuestPhone, ConfirmBooking, BookingFinished, ShowVenueList, ShowVenueDetails,
        ShowMyBookingsList, ManageBookingOptions, AskFeedback, BookingActionFinished, AskQuestionInput, QuestionSent,
        ShowHelp, ActionCancelled, ErrorOccurred
    )
}