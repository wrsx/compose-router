package ankers.compose.router.navigator

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import ankers.compose.router.ChildScreenOf
import ankers.compose.router.NavEntry
import ankers.compose.router.NavEntryCreator
import ankers.compose.router.NavEntryRemoveListener
import ankers.compose.router.Route
import ankers.compose.router.Screen
import ankers.compose.router.isRoute
import kotlin.reflect.KClass

internal class TabNavigator<T : Screen> internal constructor(
    private val config: NavConfig.Tab,
    private val removeListener: NavEntryRemoveListener,
    private val entryCreator: NavEntryCreator,
    private val navEntries: SnapshotStateList<NavEntry<*>>,
    private val cache: SnapshotStateList<NavEntry<*>>,
) : CoreNavigator<T> {
    override fun navigate(to: ChildScreenOf<T>) {
        removeIf { it.screen.isRoute(to) }

        val cachedEntry = cache.firstOrNull { it.screen.isRoute(to) }

        if (cachedEntry != null) {
            navEntries.add(cachedEntry)
        } else {
            val newEntry = entryCreator.create(to)
            cache.add(newEntry)
            navEntries.add(newEntry)
        }
    }

    override fun removeIf(predicate: (NavEntry<*>) -> Boolean) {
        val toRemove = navEntries.filter { predicate(it) }

        toRemove.forEach { item ->
            navEntries.remove(item)
            removeListener.onRemoved(item.id)
        }
    }

    override fun popToRoot() {
        if (isAtRoot) return

        while (!isAtRoot) navEntries.removeAt(navEntries.lastIndex)
    }

    override fun pop(count: Int) {
        repeat(count) {
            navEntries.removeAt(navEntries.lastIndex)
        }
    }

    private val isAtRoot: Boolean
        get() = navEntries.size == 1

    override val currentScreen by derivedStateOf { navEntries.lastOrNull()?.screen }

    override val selected: NavEntry<*>?
        get() = navEntries.lastOrNull()

    override val isEmpty: Boolean
        get() = navEntries.size == 0

    private fun clearToRoute(route: Route) {
        navEntries.clear()

        val cached = cache.firstOrNull { route.isInstance(it.screen) }
        if (cached != null) navEntries.add(cached)
    }

    override val backHandler = object : BackHandler {
        override val enabled: Boolean
            get() = !isEmpty && !isAtRoot

        override fun onBack(routes: List<Route>) {
            val firstRoute = routes.first()

            if (config.backPress == NavConfig.Tab.BackPress.First) {
                clearToRoute(routes.first())
            } else {
                pop()
                val currentScreen = selected

                val notAtFirst = !firstRoute.isInstance(currentScreen?.screen)

                /**
                 * If theres only one item in the back history and we're not
                 * at the first tab, set up the history such that the next
                 * onBack will take us to the first tab
                 */
                if (isAtRoot && notAtFirst) {
                    clearToRoute(firstRoute)

                    /** Re-add the screen we popped to */
                    navEntries.add(currentScreen!!)
                }
            }
        }
    }
}