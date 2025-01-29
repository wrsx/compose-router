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

internal class StackNavigator<T : Screen> internal constructor(
    private val removeListener: NavEntryRemoveListener,
    private val entryCreator: NavEntryCreator,
    private val navEntries: SnapshotStateList<NavEntry<*>>,
) : CoreNavigator<T> {
    override fun navigate(to: ChildScreenOf<T>) = push(to)

    private fun push(to: Screen) {
        navEntries += entryCreator.create(to)
    }

    override val currentScreen by derivedStateOf { navEntries.lastOrNull()?.screen }

    override fun removeIf(predicate: (NavEntry<*>) -> Boolean) {
        navEntries.forEach { item ->
            if (predicate(item)) {
                navEntries.remove(item)
                removeListener.onRemoved(item.id)
            }
        }
    }

    private val isAtRoot: Boolean
        get() = navEntries.size == 1

    override fun popToRoot() {
        if (isAtRoot) return

        while (!isAtRoot) navEntries.removeAt(navEntries.lastIndex)
    }

    override fun pop(count: Int) {
        repeat(count) {
            navEntries.removeAt(navEntries.lastIndex)
        }
    }

    override val selected: NavEntry<*>?
        get() = navEntries.lastOrNull()

    override val isEmpty: Boolean
        get() = navEntries.size == 0

    override val backHandler = object : BackHandler {
        override val enabled: Boolean
            get() = !isEmpty && !isAtRoot

        override fun onBack(routes: List<Route>) {
            pop()
        }
    }
}