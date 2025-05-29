package fsm

import ru.nsk.kstatemachine.statemachine.StateMachine
import ru.nsk.kstatemachine.state.IState
import ru.nsk.kstatemachine.state.finalState
import ru.nsk.kstatemachine.state.initialState
import ru.nsk.kstatemachine.state.state
import ru.nsk.kstatemachine.statemachine.createStateMachine

/**
 * Booking finite‑state machine: states, builder and helper to get full list of states.
 */
object FsmStates {

    /* ===================== lateinit references to each state ===================== */
    private lateinit var Initial: IState
    private lateinit var MainMenu: IState
    private lateinit var ChangeLanguage: IState
    private lateinit var ChooseClub: IState
    private lateinit var ChooseDate: IState
    private lateinit var ChooseTable: IState
    private lateinit var EnterPeople: IState
    private lateinit var ChooseSlot: IState

    private lateinit var EnterGuestName: IState
    private lateinit var EnterGuestPhone: IState
    private lateinit var ConfirmBooking: IState
    private lateinit var BookingFinished: IState

    private lateinit var ShowVenueList: IState
    private lateinit var ShowVenueDetails: IState
    private lateinit var ShowMyBookingList: IState

    private lateinit var ManageBookingOptions: IState
    private lateinit var AskFeedback: IState
    private lateinit var BookingActionFinished: IState

    private lateinit var AskQuestionInput: IState
    private lateinit var QuestionSent: IState
    private lateinit var ShowHelp: IState

    private lateinit var ActionCancelled: IState
    private lateinit var ErrorOccurred: IState

    /**
     * Build and return the StateMachine instance. Call this once at application start.
     */
    suspend fun build(scope: kotlinx.coroutines.CoroutineScope): StateMachine = createStateMachine(scope, name = "BookingFSM") {
        /* ======================== Main flow ======================== */
        Initial             = initialState("InitialLoadingState")
        MainMenu            = state("MainMenuState")
        ChangeLanguage      = state("ChangeLanguageState")
        ChooseClub          = state("ChooseClubState")
        ChooseDate          = state("ChooseDateState")
        ChooseTable         = state("ChooseTableState")
        EnterPeople         = state("EnterPeopleState")
        ChooseSlot          = state("ChooseSlotState")

        /* ============== Booking details & confirmation ============= */
        EnterGuestName      = state("EnterGuestNameState")
        EnterGuestPhone     = state("EnterGuestPhoneState")
        ConfirmBooking      = state("ConfirmBookingState")
        BookingFinished     = state("BookingFinishedState")

        /* ================= Venue info / listings =================== */
        ShowVenueList       = state("ShowVenueListState")
        ShowVenueDetails    = state("ShowVenueDetailsState")
        ShowMyBookingList   = state("ShowMyBookingListState")

        /* ================= Options & feedback ====================== */
        ManageBookingOptions  = state("ManageBookingOptionsState")
        AskFeedback           = state("AskFeedbackState")
        BookingActionFinished = state("BookingActionFinishedState")

        /* ==================== Help / Questions ===================== */
        AskQuestionInput    = state("AskQuestionInputState")
        QuestionSent        = state("QuestionSentState")
        ShowHelp            = state("ShowHelpState")

        /* =================== Final & Error states ================= */
        ActionCancelled     = state("ActionCancelledState")
        ErrorOccurred       = finalState("ErrorOccurredState")

        // TODO: add transitions and behaviour here
    }

    /**
     * Return list of all state references – useful for tests or analytics.
     */
    fun getAllStates(): List<IState> = listOf(
        Initial, MainMenu, ChangeLanguage, ChooseClub, ChooseDate, ChooseTable, EnterPeople, ChooseSlot,
        EnterGuestName, EnterGuestPhone, ConfirmBooking, BookingFinished, ShowVenueList, ShowVenueDetails,
        ShowMyBookingList, ManageBookingOptions, AskFeedback, BookingActionFinished, AskQuestionInput, QuestionSent,
        ShowHelp, ActionCancelled, ErrorOccurred
    )
}
