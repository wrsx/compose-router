package ankers.compose.router.navigator

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import ankers.compose.router.ChildScreenOf
import ankers.compose.router.CombinedNavigation
import ankers.compose.router.NavEntry
import ankers.compose.router.NavEntryCreator
import ankers.compose.router.NavEntryRemoveListener
import ankers.compose.router.Route
import ankers.compose.router.Screen
import ankers.compose.router.rememberMutableStateListOf
import kotlinx.coroutines.flow.filterNotNull
import kotlin.reflect.KClass

internal class NavigatorProvider(
    private val navigationsByRoute: SnapshotStateMap<Route, MutableList<Screen>>,
    private val entryCreator: NavEntryCreator,
    private val entryRemoveListener: NavEntryRemoveListener = NavEntryRemoveListener { }
) {
    @Composable
    fun <T : Screen> provide(config: NavConfig, type: KClass<T>): CoreNavigator<T> {
        val navEntries = rememberMutableStateListOf<NavEntry<*>>()

        val navigator = when (config) {
            NavConfig.Stack -> StackNavigator<T>(
                removeListener = entryRemoveListener,
                entryCreator = entryCreator,
                navEntries = navEntries,
            )

            is NavConfig.Tab -> {
                val cache = rememberMutableStateListOf<NavEntry<*>>()

                TabNavigator(
                    config = config,
                    removeListener = entryRemoveListener,
                    entryCreator = entryCreator,
                    navEntries = navEntries,
                    cache = cache
                )
            }
        }.apply {
            navigationsByRoute[type]?.asReversed()?.forEach { to ->
                navigate(to as ChildScreenOf<T>)
            }
            navigationsByRoute.remove(type)
        }

        LaunchedEffect(navigator) {
            snapshotFlow { navigationsByRoute[type] }.filterNotNull().collect { navigations ->
                /** Entries get added in ascending order, but we navigate in descending order */
                navigations.asReversed().forEach { to ->
                    navigator.navigate(to as ChildScreenOf<T>)
                }
                navigationsByRoute.remove(type)
            }
        }

        return navigator
    }
}

@Composable
internal fun <T : Screen> rememberNavigatorImpl(
    type: KClass<T>,
    navConfig: NavConfig,
    coreNavigatorProvider: NavigatorProvider,
    navigationsByRoute: SnapshotStateMap<Route, MutableList<Screen>> = remember { mutableStateMapOf() },
    locked: MutableState<Boolean> = remember { mutableStateOf(false) },
    backHandlerProvider: @Composable (BackHandler, List<Route>) -> Unit,
): Navigator<T> {
    val coreNavigator = coreNavigatorProvider.provide(navConfig, type)

    val publicNavigator = remember {
        object : Navigator<T>() {
            override fun navigate(to: ChildScreenOf<T>) {
                if (locked.value) return
                coreNavigator.navigate(to)
            }

            override fun navigate(to: CombinedNavigation<out ChildScreenOf<T>, *>) {
                if (locked.value) return

                /** For each screen in the chain, add an entry in the nav registry.
                 * If parent is null, this must be a screen for this navigator */
                to.forEach { parent, current ->
                    navigationsByRoute.add(parent?.let { it::class } ?: type, current)
                }
            }

            @Composable
            override fun <C : ChildScreenOf<T>> rememberNavigator(
                type: KClass<C>,
                navConfig: NavConfig,
            ): Navigator<C> {
                return rememberNavigatorImpl(
                    type = type,
                    navConfig = navConfig,
                    navigationsByRoute = navigationsByRoute,
                    locked = locked,
                    backHandlerProvider = backHandlerProvider,
                    coreNavigatorProvider = coreNavigatorProvider
                )
            }

            override fun popToRoot(): Navigator<T> {
                coreNavigator.popToRoot()
                return this
            }

            override fun pop(count: Int): Navigator<T> {
                if (locked.value) return this
                coreNavigator.pop(count)
                return this
            }

            override fun removeIf(predicate: (NavEntry<*>) -> Boolean): Navigator<T> {
                coreNavigator.removeIf(predicate)
                return this
            }

            override val selected: NavEntry<*>?
                get() = coreNavigator.selected

            override val isEmpty: Boolean
                get() = coreNavigator.isEmpty

            override val currentScreen: Screen?
                get() = coreNavigator.currentScreen

            @Composable
            override fun BackHandler(routes: List<Route>) {
                backHandlerProvider(coreNavigator.backHandler, routes)
            }
        }
    }

    return publicNavigator
}

private fun SnapshotStateMap<Route, MutableList<Screen>>.add(key: Route, navigable: Screen) {
    set(key, getOrElse(key) { mutableListOf() }.apply {
        add(navigable)
    })
}