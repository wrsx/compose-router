package ankers.compose.router.navigator

import ankers.compose.router.ChildScreenOf
import ankers.compose.router.CombinedNavigation
import ankers.compose.router.Route
import ankers.compose.router.Screen
import ankers.compose.router.back.BackAction
import ankers.compose.router.entry.NavEntry

/**
 * Owns a set of entries and the operations on them. Typed by the screen whose children it owns, so only
 * [ChildScreenOf] [T] can be navigated to.
 *
 * The shell is closed; behaviour comes from the [NavigatorPolicy] chosen at creation. Operations only a stack can
 * express live on [StackNavigator].
 */
open class Navigator<T : Screen> internal constructor(internal val shell: NavigatorShell<*>) {

    /** Owned entries in navigator-defined order: a stack bottom to top, tabs in declaration order. */
    val entries: List<NavEntry<*>> get() = shell.owned.toList()

    val selected: NavEntry<*>? get() = shell.selected

    val isEmpty: Boolean get() = shell.owned.isEmpty()

    /** The entry back descends through: a promoted entry, else the selected entry unless it is covered. */
    val backPathEntry: NavEntry<*>? get() = shell.backPathEntry

    /** Deepest navigator on the back path with something to do; null when back has nothing to do here. */
    val backAction: BackAction? get() = shell.backAction

    val canGoBack: Boolean get() = backAction != null

    fun back() {
        backAction?.commit()
    }

    fun navigate(to: ChildScreenOf<T>) = shell.navigate(to)

    /** Pushes a typed path, delivering segments to nested navigators as they are created. */
    fun navigate(to: CombinedNavigation<out ChildScreenOf<T>, *>) = shell.navigateChain(to)

    fun pop(count: Int = 1) = shell.pop(count)

    fun popToRoot() = shell.popToRoot()

    /** The private state of [policy], which must be the policy this navigator was created with. */
    @Suppress("UNCHECKED_CAST")
    fun <S : Any> stateOf(policy: NavigatorPolicy<S>): S {
        require(shell.policy === policy) { "$this was created with ${shell.policy.key}, not ${policy.key}" }
        return shell.scope.state as S
    }

    /**
     * Runs [block] against this navigator's [PolicyScope]: how a custom policy exposes operations beyond navigate,
     * pop and back. [policy] must be the policy this navigator was created with.
     */
    @Suppress("UNCHECKED_CAST")
    fun <S : Any> mutate(policy: NavigatorPolicy<S>, block: (PolicyScope<S>) -> Unit) {
        require(shell.policy === policy) { "$this was created with ${shell.policy.key}, not ${policy.key}" }
        (shell as NavigatorShell<S>).mutate(block)
    }

    /** A text rendering of this navigator and everything nested under it. */
    fun describe(): String = shell.describe()

    override fun toString(): String = "Navigator(${shell.prefix})"
}

/** The navigator of [NavConfig.Stack]. */
class StackNavigator<T : Screen> internal constructor(
    private val stack: NavigatorShell<Unit>,
) : Navigator<T>(stack) {

    /** Pops back to the most recent entry of [route]; [inclusive] pops that entry as well. */
    fun popTo(route: Route, inclusive: Boolean = false) = stack.mutate { NavConfig.Stack.popTo(it, route, inclusive) }

    inline fun <reified R : ChildScreenOf<T>> popTo(inclusive: Boolean = false) = popTo(R::class, inclusive)

    /** Replaces the selected entry with [to]. */
    fun replace(to: ChildScreenOf<T>) = stack.mutate { NavConfig.Stack.replace(it, to) }

    /** Pushes [to] unless an equal screen is already on top. */
    fun navigate(to: ChildScreenOf<T>, singleTop: Boolean) =
        if (singleTop) stack.mutate { NavConfig.Stack.navigateSingleTop(it, to) } else navigate(to)
}
