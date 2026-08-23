package ankers.compose.router.navigator

import ankers.compose.router.Route
import ankers.compose.router.Screen
import ankers.compose.router.entry.EntryId
import ankers.compose.router.entry.NavEntry

/** The shipped navigator policies. */
object NavConfig {

    /** A stack of entries, duplicates permitted. Back pops the top entry. */
    object Stack : NavigatorPolicy<Unit> {
        override val key: String = "stack"
        override fun initialState() = Unit
        override fun saveState(state: Unit): Any? = null
        override fun restoreState(saved: Any?) = Unit

        override fun navigate(scope: PolicyScope<Unit>, to: Screen): NavEntry<*> =
            scope.create(to).also { scope.select(it) }

        override fun pop(scope: PolicyScope<Unit>, count: Int) {
            // never pops the last entry; an empty navigator would just re-create its start destination
            val removable = (scope.owned.size - 1).coerceAtLeast(0)
            repeat(count.coerceIn(0, removable)) {
                scope.retire(scope.owned.last())
            }
            scope.select(scope.owned.lastOrNull())
        }

        override fun popToRoot(scope: PolicyScope<Unit>) {
            pop(scope, scope.owned.size - 1)
        }

        override fun backStep(scope: PolicyScope<Unit>): BackStep? {
            val owned = scope.owned
            if (owned.size < 2) return null
            val outgoing = owned.last()
            val incoming = owned[owned.lastIndex - 1]
            return BackStep(outgoing, incoming) {
                scope.retire(outgoing)
                scope.select(incoming)
            }
        }

        override fun onRetired(scope: PolicyScope<Unit>, retired: List<NavEntry<*>>) {
            if (scope.selected == null || scope.selected in retired) scope.select(scope.owned.lastOrNull())
        }

        internal fun popTo(scope: PolicyScope<Unit>, route: Route, inclusive: Boolean) {
            val index = scope.owned.indexOfLast { route.isInstance(it.screen) }
            if (index < 0) return
            val keep = if (inclusive) index else index + 1
            pop(scope, scope.owned.size - keep)
        }

        internal fun replace(scope: PolicyScope<Unit>, to: Screen) {
            val current = scope.owned.lastOrNull()
            val entry = scope.create(to)
            if (current != null) scope.retire(current)
            scope.select(entry)
        }

        internal fun navigateSingleTop(scope: PolicyScope<Unit>, to: Screen): NavEntry<*> {
            val top = scope.owned.lastOrNull()
            return if (top != null && top.screen == to) top.also { scope.select(it) } else navigate(scope, to)
        }
    }

    /**
     * One entry per tab, retained while unselected. Tabs are ordered by route declaration; the first declared tab
     * is the first tab. A private history of selections drives back per [backPress].
     */
    class Tab(val backPress: BackPress = BackPress.Stack) : NavigatorPolicy<Tab.History> {

        enum class BackPress {
            /** Back returns to the first tab when it is not selected. */
            First,

            /** Back steps through selection history, ending at the first tab. */
            Stack,
        }

        class History(val ids: List<EntryId>) {
            override fun toString(): String = ids.joinToString(" > ")
        }

        override val key: String = "tab:${backPress.name.lowercase()}"
        override fun initialState() = History(emptyList())
        override fun saveState(state: History): Any? = ArrayList(state.ids.map { it.toString() })

        @Suppress("UNCHECKED_CAST")
        override fun restoreState(saved: Any?) =
            History((saved as? List<String>).orEmpty().map { EntryId.parse(it) })

        override fun navigate(scope: PolicyScope<Tab.History>, to: Screen): NavEntry<*> {
            val route = scope.routeOf(to)
            val existing = scope.owned.firstOrNull { scope.routeOf(it.screen) == route }
            val entry = when {
                existing == null -> scope.create(to).also { order(scope, it) }
                existing.screen == to -> existing
                // the same tab with different arguments: replaced in place, its history position kept
                else -> {
                    val index = scope.owned.indexOf(existing)
                    scope.retire(existing)
                    scope.create(to).also { scope.move(it, index) }
                }
            }
            val replaced = existing?.id?.takeIf { it != entry.id }
            scope.state = History(scope.state.ids.filter { it != entry.id && it != replaced } + entry.id)
            scope.select(entry)
            return entry
        }

        // a tab is its registered route; before the graph is known, its class
        private fun PolicyScope<*>.routeOf(screen: Screen): Route = routes.firstOrNull { it.isInstance(screen) } ?: screen::class

        override fun pop(scope: PolicyScope<Tab.History>, count: Int) {
            repeat(count) { stepBack(scope)?.apply?.invoke() }
        }

        override fun popToRoot(scope: PolicyScope<Tab.History>) {
            val first = scope.owned.firstOrNull() ?: return
            scope.state = History(listOf(first.id))
            scope.select(first)
        }

        override fun backStep(scope: PolicyScope<Tab.History>): BackStep? = when (backPress) {
            BackPress.Stack -> stepBack(scope)
            BackPress.First -> toFirst(scope)
        }

        override fun onRetired(scope: PolicyScope<Tab.History>, retired: List<NavEntry<*>>) {
            val gone = retired.map { it.id }.toSet()
            scope.state = History(scope.state.ids.filter { it !in gone })
            if (scope.selected == null || scope.selected in retired) {
                scope.select(scope.state.ids.lastOrNull()?.let { scope.owned(it) } ?: scope.owned.firstOrNull())
            }
        }

        // the first declared tab always exists once the graph is known, so back has somewhere to land
        override fun onRoutes(scope: PolicyScope<Tab.History>, routes: List<Route>) {
            val first = routes.firstOrNull() ?: return
            if (scope.owned.none { first.isInstance(it.screen) }) {
                val screen = scope.construct(first) ?: return
                val entry = scope.create(screen)
                if (scope.selected == null) {
                    scope.state = History(listOf(entry.id))
                    scope.select(entry)
                }
            }
            scope.owned.toList().forEach { order(scope, it) }
        }

        override fun describe(state: History): String = "history=[${state.ids.joinToString(" ")}]"

        private fun stepBack(scope: PolicyScope<Tab.History>): BackStep? {
            val selected = scope.selected ?: return null
            val ids = scope.state.ids
            val previous = ids.dropLast(1).lastOrNull()?.let { scope.owned(it) }
            return when {
                previous != null -> BackStep(selected, previous) {
                    scope.state = History(ids.dropLast(1))
                    scope.select(previous)
                }
                else -> toFirst(scope)
            }
        }

        private fun toFirst(scope: PolicyScope<Tab.History>): BackStep? {
            val selected = scope.selected ?: return null
            val first = scope.owned.firstOrNull() ?: return null
            if (first == selected) return null
            return BackStep(selected, first) {
                scope.state = History(listOf(first.id))
                scope.select(first)
            }
        }

        // keep owned tabs in route declaration order
        private fun order(scope: PolicyScope<Tab.History>, entry: NavEntry<*>) {
            val routes = scope.routes
            if (routes.isEmpty()) return
            val rank = { e: NavEntry<*> -> routes.indexOfFirst { it.isInstance(e.screen) }.let { if (it < 0) Int.MAX_VALUE else it } }
            val target = scope.owned.filter { it != entry }.count { rank(it) < rank(entry) }
            scope.move(entry, target)
        }
    }
}
