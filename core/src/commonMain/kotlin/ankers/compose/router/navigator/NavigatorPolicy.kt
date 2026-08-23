package ankers.compose.router.navigator

import ankers.compose.router.Route
import ankers.compose.router.Screen
import ankers.compose.router.entry.EntryId
import ankers.compose.router.entry.NavEntry

/**
 * Decides how `navigate`, `pop`, and back mutate a navigator's owned entries, selection, and private state.
 *
 * A policy is a reusable specification, not a stateful instance: its private state [S] is held and saved by the
 * shell, and every mutation goes through the four operations on [PolicyScope]. The shell owns everything else —
 * ids, hosting, child navigators, events, back resolution.
 */
interface NavigatorPolicy<S : Any> {
    /** Persisted with the navigator so a restored navigator can be matched to its policy. */
    val key: String

    fun initialState(): S

    /** A saveable representation of [state]: primitives, strings, and lists of them. */
    fun saveState(state: S): Any?

    fun restoreState(saved: Any?): S

    /** Navigates to [to]; returns the entry now standing for it — created or reused — or null if rejected. */
    fun navigate(scope: PolicyScope<S>, to: Screen): NavEntry<*>?

    /** Every policy gives this a meaning: a stack removes entries, a tab navigator steps its history. */
    fun pop(scope: PolicyScope<S>, count: Int)

    fun popToRoot(scope: PolicyScope<S>)

    /** What back would do right now, or null if nothing. The shell wraps it into a stale-safe [BackAction]. */
    fun backStep(scope: PolicyScope<S>): BackStep?

    /** Entries were retired by the router (their route left the graph); reconcile history and selection. */
    fun onRetired(scope: PolicyScope<S>, retired: List<NavEntry<*>>)

    /** The router's routes are known, in declaration order. A tab policy ensures its first tab exists here. */
    fun onRoutes(scope: PolicyScope<S>, routes: List<Route>) {}

    fun describe(state: S): String = ""
}

/** The only mutations available to a policy. */
interface PolicyScope<S : Any> {
    val owned: List<NavEntry<*>>
    val selected: NavEntry<*>?
    var state: S

    /** Registered routes in declaration order, once the router has synchronized; empty before that. */
    val routes: List<Route>

    /** Creates an owned entry at the end of [owned]. The shell assigns the id and notifies observers. */
    fun create(screen: Screen): NavEntry<*>

    fun retire(entry: NavEntry<*>)

    fun select(entry: NavEntry<*>?)

    fun move(entry: NavEntry<*>, toIndex: Int)

    /** An argument-free screen for [route] where the platform can construct one; null otherwise. */
    fun construct(route: Route): Screen?

    fun owned(id: EntryId): NavEntry<*>? = owned.firstOrNull { it.id == id }
}

/** A policy's local back step. [apply] performs it; the shell guards it against staleness. */
class BackStep(
    val outgoing: NavEntry<*>,
    val incoming: NavEntry<*>,
    val apply: () -> Unit,
)
