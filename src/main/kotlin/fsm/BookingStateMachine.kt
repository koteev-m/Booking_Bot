package fsm

import BookingDeps      // см. ниже
import org.hildan.krossbow.statemachine.*  // пакет библиотеки
import org.hildan.krossbow.statemachine.dsl.stateMachine

/**
 * Конструирует StateMachine; зависимости (репо + телеграм-API) передаём в конструктор.
 */
fun buildBookingMachine(deps: BookingDeps) =
    stateMachine<BookingState, BookingEvent> {

        /** стартовая точка */
        initial(BookingState.Start)

        /*--------- /book ---------*/
        state<BookingState.Start> {
            on<BookingEvent.StartCmd> {
                deps.bot.sendChooseClubKeyboard(it.chatId)
                transitionTo(BookingState.ChooseClub(DraftBooking()))
            }
        }

        /*--------- выбор клуба ---------*/
        state<BookingState.ChooseClub> {
            on<BookingEvent.ClubChosen> { ev ->
                val draft = state.draft.apply { clubId = ev.id }
                val tables = deps.tablesRepo.listByClub(ev.id)
                deps.bot.sendChooseTableKeyboard(ev.chatId, tables)
                transitionTo(BookingState.ChooseTable(draft, tables))
            }
            on<BookingEvent.BackPressed> { transitionTo(BookingState.Start) }
        }

        /*--------- выбор стола ---------*/
        state<BookingState.ChooseTable> {
            on<BookingEvent.TableChosen> { ev ->
                val draft = state.draft.apply { tableId = ev.id }
                deps.bot.askPeopleCount(ev.chatId)
                transitionTo(BookingState.EnterPeople(draft))
            }
            on<BookingEvent.BackPressed> {
                deps.bot.sendChooseClubKeyboard(it.chatId)
                transitionTo(BookingState.ChooseClub(state.draft))
            }
        }

        /*--------- количество гостей ---------*/
        state<BookingState.EnterPeople> {
            on<BookingEvent.PeopleEntered> { ev ->
                val draft = state.draft.apply { guests = ev.count }
                deps.bot.sendChooseSlotKeyboard(ev.chatId, draft.clubId!!, draft.tableId!!)
                transitionTo(BookingState.ChooseSlot(draft))
            }
            on<BookingEvent.BackPressed> {
                val tables = deps.tablesRepo.listByClub(state.draft.clubId!!)
                deps.bot.sendChooseTableKeyboard(it.chatId, tables)
                transitionTo(BookingState.ChooseTable(state.draft, tables))
            }
        }

        /*--------- временной слот ---------*/
        state<BookingState.ChooseSlot> {
            on<BookingEvent.SlotChosen> { ev ->
                val draft = state.draft.apply {
                    slotStart = ev.start; slotEnd = ev.end
                }
                deps.bot.sendConfirmMessage(ev.chatId, draft)
                transitionTo(BookingState.Confirm(draft))
            }
            on<BookingEvent.BackPressed> {
                deps.bot.askPeopleCount(it.chatId)
                transitionTo(BookingState.EnterPeople(state.draft))
            }
        }

        /*--------- подтверждение ---------*/
        state<BookingState.Confirm> {
            on<BookingEvent.ConfirmPressed> { ev ->
                deps.bookingsRepo.create(state.draft.toBooking())
                deps.bot.sendSuccessMessage(ev.chatId)
                transitionTo(BookingState.Finished)
            }
            on<BookingEvent.BackPressed> {
                deps.bot.sendChooseSlotKeyboard(it.chatId,
                    state.draft.clubId!!, state.draft.tableId!!)
                transitionTo(BookingState.ChooseSlot(state.draft))
            }
            on<BookingEvent.CancelPressed> {
                deps.bot.sendCancelledMessage(it.chatId)
                transitionTo(BookingState.Cancelled)
            }
        }

        /*--------- выходы ---------*/
        state<BookingState.Finished> {
            onAnyEvent { transitionTo(BookingState.Start) }
        }
        state<BookingState.Cancelled> {
            onAnyEvent { transitionTo(BookingState.Start) }
        }
    }