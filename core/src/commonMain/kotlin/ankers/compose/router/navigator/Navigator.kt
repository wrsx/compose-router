package ankers.compose.router.navigator

import androidx.compose.runtime.Composable
import ankers.compose.router.ChildScreenOf
import ankers.compose.router.CombinedNavigation
import ankers.compose.router.NavEntry
import ankers.compose.router.Route
import ankers.compose.router.Screen
import kotlin.reflect.KClass

abstract class Navigator<T : Screen> {
    abstract fun navigate(to: ChildScreenOf<T>)

    abstract fun navigate(to: CombinedNavigation<out ChildScreenOf<T>, *>)

    abstract fun popToRoot(): Navigator<T>

    abstract fun pop(count: Int = 1): Navigator<T>

    abstract fun removeIf(predicate: (NavEntry<*>) -> Boolean): Navigator<T>

    abstract val selected: NavEntry<*>?
    abstract val isEmpty: Boolean
    abstract val currentScreen: Screen?

    @Composable
    internal abstract fun BackHandler(routes: List<Route>)

    @Composable
    abstract fun <C : ChildScreenOf<T>> rememberNavigator(
        type: KClass<C>,
        navConfig: NavConfig,
    ): Navigator<C>
}

internal interface CoreNavigator<T : Screen> {
    fun navigate(to: ChildScreenOf<T>)
    fun removeIf(predicate: (NavEntry<*>) -> Boolean)
    fun popToRoot()
    fun pop(count: Int = 1)
    val selected: NavEntry<*>?
    val isEmpty: Boolean
    val currentScreen: Screen?
    val backHandler: BackHandler
}