package ankers.compose.router.navigator

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ankers.compose.router.CombinedNavigation
import ankers.compose.router.Route
import ankers.compose.router.Screen
import ankers.compose.router.back.BackAction
import ankers.compose.router.back.BackTransition
import ankers.compose.router.entry.EntryId
import ankers.compose.router.entry.NavEntry
import ankers.compose.router.events.NavigatorEvents
import ankers.compose.router.host.OwnerEntry
import ankers.compose.router.host.constructStartDestination
import ankers.compose.router.host.screenFromSaveable
import ankers.compose.router.host.toSaveable
import ankers.compose.router.routeName

// the closed half of a navigator: everything a policy may not touch
internal class NavigatorShell<S : Any>(
    val prefix: EntryId,
    val policy: NavigatorPolicy<S>,
    val runtime: NavigationRuntime,
    val events: NavigatorEvents,
    val parent: NavigatorShell<*>?,
    restored: NavigatorSnapshot?,
) {
    val owned = mutableStateListOf<NavEntry<*>>()
    var selected by mutableStateOf<NavEntry<*>?>(null)
        private set
    private var policyState by mutableStateOf(policy.initialState())
    private var counter = 0

    val children = mutableStateMapOf<EntryId, Navigator<*>>()
    var promoted by mutableStateOf<EntryId?>(null)
    val covered = mutableStateMapOf<EntryId, Unit>()
    var liveRoutes by mutableStateOf<List<Route>>(emptyList())
    var transition by mutableStateOf<BackTransition?>(null)

    lateinit var navigator: Navigator<*>

    val root: NavigatorShell<*> get() = parent?.root ?: this

    // a nested navigator dies with the entry hosting it: once that entry leaves its parent, nothing new may be
    // created beneath it and everything still rendered beneath it is retiring
    val alive: Boolean get() = parent == null || (parent.owned.any { it.id == prefix } && parent.alive)

    // bumped by every mutation; a back action resolved against an older version is stale
    var version = 0L
        private set

    private fun rejectIfRetiring(what: String): Boolean {
        if (alive) return false
        runtime.registry.diagnostics("dropped $what on $prefix: its section is retiring")
        return true
    }

    val scope: PolicyScope<S> = object : PolicyScope<S> {
        override val owned: List<NavEntry<*>> get() = this@NavigatorShell.owned.toList()
        override val selected: NavEntry<*>? get() = this@NavigatorShell.selected
        override var state: S
            get() = policyState
            set(value) {
                policyState = value
                version++
            }
        override val routes: List<Route> get() = liveRoutes

        override fun create(screen: Screen): NavEntry<*> {
            counter++
            val entry = runtime.create(screen, prefix.child(counter))
            entry.restoreState(null)
            runtime.registry.register(entry, events)
            this@NavigatorShell.owned.add(entry)
            version++
            return entry
        }

        override fun construct(route: Route): Screen? = constructStartDestination(route)

        override fun retire(entry: NavEntry<*>) {
            this@NavigatorShell.owned.remove(entry)
            if (this@NavigatorShell.selected == entry) this@NavigatorShell.selected = null
            runtime.registry.retire(entry.id)
            version++
        }

        override fun select(entry: NavEntry<*>?) {
            require(entry == null || entry in this@NavigatorShell.owned) { "cannot select ${entry?.id}: not owned by $prefix" }
            this@NavigatorShell.selected = entry
            version++
        }

        override fun move(entry: NavEntry<*>, toIndex: Int) {
            val list = this@NavigatorShell.owned
            val from = list.indexOf(entry)
            if (from < 0) return
            list.removeAt(from)
            list.add(toIndex.coerceIn(0, list.size), entry)
            version++
        }
    }

    init {
        if (restored != null) restore(restored)
    }

    private fun restore(snapshot: NavigatorSnapshot) {
        check(snapshot.policyKey == policy.key) { "navigator $prefix was saved with policy ${snapshot.policyKey}, not ${policy.key}" }
        counter = snapshot.counter
        policyState = policy.restoreState(snapshot.policyState)
        snapshot.screens.forEachIndexed { i, screen ->
            val id = EntryId.parse(snapshot.ids[i])
            // an entry the registry still holds keeps its identity: one owner object per id while the runtime lives
            val entry = runtime.registry.entry(id) ?: runtime.create(screen, id).also { it.restoreState(snapshot.entryStates.getOrNull(i)) }
            runtime.registry.register(entry, events)
            owned.add(entry)
        }
        selected = snapshot.selectedId?.let { id -> owned.firstOrNull { it.id.toString() == id } }
    }

    fun snapshot(): NavigatorSnapshot = NavigatorSnapshot(
        policyKey = policy.key,
        policyState = policy.saveState(policyState),
        counter = counter,
        screens = owned.map { it.screen },
        ids = owned.map { it.id.toString() },
        entryStates = owned.map { (it as? OwnerEntry<*>)?.saveState() },
        selectedId = selected?.id?.toString(),
    )

    fun mutate(block: (PolicyScope<S>) -> Unit) {
        if (rejectIfRetiring("mutation")) return
        block(scope)
    }

    fun navigate(to: Screen): NavEntry<*>? {
        if (rejectIfRetiring("navigate(${to::class.routeName})")) return null
        val live = liveRoutes
        if (live.isNotEmpty() && live.none { it.isInstance(to) }) {
            runtime.registry.diagnostics("dropped navigate(${to::class.routeName}) on $prefix: not registered in the live graph")
            return null
        }
        return policy.navigate(scope, to)
    }

    fun navigateChain(chain: CombinedNavigation<*, *>) = deliver(chain.segments, anchor = null)

    // chains addressed to this navigator, delivered in the order they were posted
    fun consumePending() {
        val pending = runtime.mailbox.remove(prefix) ?: return
        pending.forEach { deliver(it.segments, it.anchor) }
    }

    // navigates the leading segments owned here; posts the rest to the entry whose navigator owns them.
    // a segment equal to the selected entry reuses it, so a chain reads the same whether or not it names the start
    private fun deliver(segments: List<Pair<Screen?, Screen>>, anchor: Screen?) {
        var last: NavEntry<*>? = null
        segments.forEachIndexed { i, (parent, screen) ->
            if (parent == anchor) {
                last = selected?.takeIf { it.screen == screen } ?: navigate(screen)
            } else {
                val target = last?.takeIf { it.screen == parent }
                if (target == null) {
                    runtime.registry.diagnostics("dropped chain segment ${screen::class.routeName} on $prefix: ${parent!!::class.routeName} was not navigated to")
                } else {
                    runtime.post(target.id, PendingChain(parent!!, segments.drop(i)))
                }
                return
            }
        }
    }

    fun pop(count: Int) {
        if (rejectIfRetiring("pop")) return
        policy.pop(scope, count)
    }

    fun popToRoot() {
        if (rejectIfRetiring("popToRoot")) return
        policy.popToRoot(scope)
    }

    /** Retires owned entries whose route is no longer registered; returns them. */
    fun reconcile(routes: List<Route>): List<NavEntry<*>> {
        liveRoutes = routes
        if (!alive) return emptyList()
        val gone = owned.filter { entry -> routes.none { it.isInstance(entry.screen) } }
        gone.forEach(scope::retire)
        policy.onRoutes(scope, routes)
        policy.onRetired(scope, gone)
        return gone
    }

    val backPathEntry: NavEntry<*>?
        get() = promoted?.let { id -> owned.firstOrNull { it.id == id } }
            ?: selected?.takeUnless { it.id in covered }

    val backAction: BackAction?
        get() = backPathEntry?.let { children[it.id] }?.backAction ?: ownBackAction()

    private fun ownBackAction(): BackAction? {
        val step = policy.backStep(scope) ?: return null
        val resolved = version
        return object : BackAction {
            override val target: Navigator<*> get() = navigator
            override val outgoing = step.outgoing
            override val incoming = step.incoming
            override fun commit() {
                // stale: the navigator mutated since this action was resolved
                if (version != resolved) return
                if (rejectIfRetiring("back")) return
                step.apply()
            }
            override fun toString() = "BackAction(${target} ${outgoing.id} -> ${incoming.id})"
        }
    }

    fun describe(indent: String = ""): String = buildString {
        append(indent).append(prefix).append(" [").append(policy.key).append(']')
        selected?.let { append(" selected=").append(it.id) }
        policy.describe(policyState).takeIf { it.isNotEmpty() }?.let { append(' ').append(it) }
        append('\n')
        owned.forEach { entry ->
            append(indent).append("  ").append(entry.id).append(' ').append(entry.screen::class.routeName)
            if (entry == selected) append(" *")
            if (entry.id == promoted) append(" promoted")
            if (entry.id in covered) append(" covered")
            append('\n')
            children[entry.id]?.let { append(it.shell.describe("$indent    ")) }
        }
    }
}

internal class NavigatorSnapshot(
    val policyKey: String,
    val policyState: Any?,
    val counter: Int,
    val screens: List<Screen>,
    val ids: List<String>,
    val entryStates: List<Any?>,
    val selectedId: String?,
) {
    fun toSaveable(): List<Any?> = listOf(
        policyKey, policyState, counter,
        ArrayList(screens.map { it.toSaveable() }), ArrayList(ids), ArrayList(entryStates), selectedId,
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromSaveable(list: List<Any?>): NavigatorSnapshot = NavigatorSnapshot(
            policyKey = list[0] as String,
            policyState = list[1],
            counter = list[2] as Int,
            screens = (list[3] as List<Any>).map(::screenFromSaveable),
            ids = list[4] as List<String>,
            entryStates = list[5] as List<Any?>,
            selectedId = list[6] as String?,
        )
    }
}
