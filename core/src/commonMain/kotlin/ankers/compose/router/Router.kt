package ankers.compose.router

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import ankers.compose.router.back.PlatformBackScope
import ankers.compose.router.entry.EntryId
import ankers.compose.router.entry.NavEntry
import ankers.compose.router.events.HostEvents
import ankers.compose.router.events.HostState
import ankers.compose.router.host.OwnerEntry
import ankers.compose.router.host.constructStartDestination
import ankers.compose.router.host.rootLifecycleOwner
import ankers.compose.router.navigator.NavConfig
import ankers.compose.router.navigator.Navigator
import ankers.compose.router.navigator.NavigatorPolicy
import ankers.compose.router.navigator.NavigatorShell
import ankers.compose.router.navigator.RegisterChild
import ankers.compose.router.navigator.StackNavigator
import ankers.compose.router.navigator.rememberChildShell
import ankers.compose.router.navigator.rememberNavigator
import ankers.compose.router.events.NavigatorEvents
import ankers.compose.router.render.CrossfadeRenderer
import ankers.compose.router.render.RouterRenderScope
import ankers.compose.router.render.RouterRenderer
import kotlin.reflect.KClass

@DslMarker
annotation class NavigationDslMarker

/** The graph of a [Router]: one `screen` or `projected` registration per route. */
@NavigationDslMarker
class RouterScope<T : Screen> internal constructor() {
    internal class Registration(
        val route: Route,
        val projected: Boolean,
        val content: @Composable ScreenScope<Screen>.(NavEntry<Screen>) -> Unit,
    )

    internal val registrations = mutableListOf<Registration>()

    @PublishedApi
    @Suppress("UNCHECKED_CAST")
    internal fun <C : ChildScreenOf<T>> register(
        route: KClass<C>,
        projected: Boolean,
        content: @Composable ScreenScope<C>.(NavEntry<C>) -> Unit,
    ) {
        check(registrations.none { it.route == route }) { "${route.routeName} is registered twice" }
        // composable lambdas cannot be cast across types; adapt instead
        registrations += Registration(route, projected) { entry -> content(this as ScreenScope<C>, entry as NavEntry<C>) }
    }

    /** Registers the content for [C]. The first registration is the start destination. */
    inline fun <reified C : ChildScreenOf<T>> screen(noinline content: @Composable ScreenScope<C>.(NavEntry<C>) -> Unit) =
        register(C::class, projected = false, content)

    /** Registers [C] as an overlay rendered at the nearest [ankers.compose.router.render.OverlayHost] while owned here. */
    inline fun <reified C : ChildScreenOf<T>> projected(noinline content: @Composable ScreenScope<C>.(NavEntry<C>) -> Unit) =
        register(C::class, projected = true, content)
}

/** The scope of one entry's content. Nested navigators are created here, under the entry. */
@NavigationDslMarker
class ScreenScope<C : Screen> internal constructor(
    val entry: NavEntry<C>,
    internal val parent: NavigatorShell<*>,
) {
    @Composable
    fun rememberNavigator(events: NavigatorEvents = NavigatorEvents.Discard): StackNavigator<C> =
        rememberNavigator(NavConfig.Stack, events)

    @Composable
    fun rememberNavigator(policy: NavConfig.Stack, events: NavigatorEvents = NavigatorEvents.Discard): StackNavigator<C> =
        rememberChild(rememberChildShell(parent, entry, policy, events)) { StackNavigator(it) }

    /** A nested navigator with [policy]: a [NavConfig.Tab] or a custom [NavigatorPolicy]. */
    @Composable
    fun rememberNavigator(policy: NavigatorPolicy<*>, events: NavigatorEvents = NavigatorEvents.Discard): Navigator<C> =
        rememberChild(rememberChildShell(parent, entry, policy, events)) { Navigator(it) }

    @Composable
    private fun <S : Any, N : Navigator<*>> rememberChild(shell: NavigatorShell<S>, create: (NavigatorShell<S>) -> N): N {
        val navigator = rememberNavigator(shell, create)
        RegisterChild(parent, entry, navigator)
        return navigator
    }
}

/**
 * Renders [navigator] through [renderer] with the graph declared in [config].
 *
 * [config] is re-declared on every composition, so content always sees the captures of the current composition.
 * Whenever the declared routes change, entries of removed routes are retired, the policy reconciles selection, and
 * an empty navigator gets its start destination — [start], or the first registered route constructed without
 * arguments.
 */
@Composable
fun <T : Screen> Router(
    navigator: Navigator<T>,
    renderer: RouterRenderer = CrossfadeRenderer,
    start: ChildScreenOf<T>? = null,
    config: RouterScope<T>.() -> Unit,
) {
    val shell = navigator.shell
    val registry = shell.runtime.registry
    val routes = remember(shell) { mutableListOf<Route>() }
    // content for entries whose route left the graph, kept while a host still renders them
    val retiring = remember(shell) { mutableMapOf<EntryId, RouterScope.Registration>() }
    val stateHolder = rememberSaveableStateHolder()

    val declared = RouterScope<T>().apply(config).registrations
    check(declared.isNotEmpty()) { "a Router must register at least one route" }
    val previous = remember(shell) { mutableListOf<RouterScope.Registration>() }

    fun List<RouterScope.Registration>.forScreen(screen: Screen): RouterScope.Registration? =
        firstOrNull { it.route == screen::class } ?: firstOrNull { it.route.isInstance(screen) }

    fun registrationFor(entry: NavEntry<*>): RouterScope.Registration? = retiring[entry.id] ?: declared.forScreen(entry.screen)

    // reconciled in the same pass that sees the change, so the renderer never hosts an entry this frame retires;
    // a hosted entry of a removed route keeps the content it was declared with until its host goes
    val declaredRoutes = declared.map { it.route }
    if (declaredRoutes != routes) {
        val gone = shell.reconcile(declaredRoutes)
        gone.forEach { entry ->
            if (registry.isHosted(entry.id)) {
                previous.forScreen(entry.screen)?.let { retiring[entry.id] = it }
            } else if (!registry.contains(entry.id)) {
                // released inside this pass — a restored route rejected before the release listener below
                // exists. Drop its payload here or it stays in every future save.
                stateHolder.removeState(entry.id.toString())
            }
        }
        routes.clear()
        routes += declaredRoutes
    }
    previous.clear()
    previous += declared

    // saved state lives as long as its entry
    DisposableEffect(registry, stateHolder, shell) {
        val listener = { id: EntryId -> if (id.parent == shell.prefix) stateHolder.removeState(id.toString()) }
        registry.releaseListeners += listener
        onDispose { registry.releaseListeners -= listener }
    }

    if (navigator.isEmpty) {
        val first = start ?: constructStartDestination(routes.first())
            ?: error("start destination ${routes.first().routeName} needs arguments; pass start = ... to Router")
        shell.navigate(first)
    }
    // a chain addressed here lands in the pass that created the start destination, so the first frame is the target
    shell.consumePending()

    // rebuilt each composition: it carries this composition's declarations to every host, projected ones included
    val renderEntry: @Composable (NavEntry<*>, Boolean, Boolean?) -> Unit = { entry, active, handlesBack ->
        EntryHost(
            navigator = navigator,
            entry = entry,
            active = active,
            handlesBack = handlesBack,
            stateHolder = stateHolder,
            registration = registrationFor(entry)
                ?: error("route ${entry.screen::class.routeName} is not registered in the Router of ${shell.prefix}"),
            onUnhosted = { id -> retiring.remove(id) },
        )
    }

    val scope = RouterRenderScope(
        entries = navigator.entries,
        selected = navigator.selected,
        transition = shell.transition,
        activeCap = true,
        projected = { entry -> registrationFor(entry)?.projected == true },
        renderEntry = renderEntry,
    )

    if (shell.parent == null) {
        PlatformBackScope(navigator) { scope.renderer() }
    } else {
        scope.renderer()
    }
}

// the live host of one entry; emits each state once, whichever effect observes it first
private class HostHandle {
    private var events: HostEvents? = null
    private var emitted: HostState? = null

    fun begin(events: HostEvents, state: HostState) {
        this.events = events
        this.state(state)
    }

    fun state(state: HostState) {
        val events = events ?: return
        if (emitted == state) return
        emitted = state
        events.state(state)
    }

    fun end() {
        events?.unhosted()
        events = null
        emitted = null
    }
}

@Composable
private fun <T : Screen> EntryHost(
    navigator: Navigator<T>,
    entry: NavEntry<*>,
    active: Boolean,
    handlesBack: Boolean?,
    stateHolder: SaveableStateHolder,
    registration: RouterScope.Registration,
    onUnhosted: (EntryId) -> Unit,
) {
    val shell = navigator.shell
    val registry = shell.runtime.registry
    check(entry.id.parent == shell.prefix) { "entry ${entry.id} does not belong to the navigator of ${shell.prefix}" }
    check(registry.contains(entry.id)) { "entry ${entry.id} has been released and can no longer be rendered" }

    val retiring = entry !in shell.owned || !shell.alive
    val hostState = when {
        retiring -> HostState.Retiring
        handlesBack == false -> HostState.Covered
        active -> HostState.Active
        else -> HostState.Inactive
    }
    val currentState by rememberUpdatedState(hostState)
    val host = remember(entry.id) { HostHandle() }

    DisposableEffect(entry.id) {
        host.begin(registry.host(entry.id), currentState)
        onDispose {
            host.end()
            registry.unhost(entry.id)
            onUnhosted(entry.id)
        }
    }
    DisposableEffect(hostState) {
        host.state(hostState)
        onDispose {}
    }

    DisposableEffect(handlesBack, entry.id) {
        when (handlesBack) {
            true -> {
                check(shell.promoted == null || shell.promoted == entry.id) {
                    "navigator ${shell.prefix} already promotes ${shell.promoted}; at most one entry may handle back"
                }
                shell.promoted = entry.id
            }
            false -> shell.covered[entry.id] = Unit
            null -> {}
        }
        onDispose {
            if (handlesBack == true && shell.promoted == entry.id) shell.promoted = null
            if (handlesBack == false) shell.covered.remove(entry.id)
        }
    }

    // nested entries are capped at their parent entry, wherever they are composed; root entries at what the platform provides
    val parentOwner: LifecycleOwner? = entry.id.parent?.let { registry.entry(it) } ?: rootLifecycleOwner()
    if (entry is OwnerEntry<*> && parentOwner != null) {
        DisposableEffect(entry, parentOwner) {
            val observer = LifecycleEventObserver { source, _ -> entry.parent(source.lifecycle.currentState) }
            parentOwner.lifecycle.addObserver(observer)
            onDispose { parentOwner.lifecycle.removeObserver(observer) }
        }
    }

    val owners = buildList<ProvidedValue<*>> {
        if (entry is ViewModelStoreOwner) add(LocalViewModelStoreOwner provides entry)
        if (entry is LifecycleOwner) add(LocalLifecycleOwner provides entry)
        if (entry is SavedStateRegistryOwner) add(LocalSavedStateRegistryOwner provides entry)
    }

    PlatformBackScope(shell.root.navigator) {
        CompositionLocalProvider(*owners.toTypedArray()) {
            stateHolder.SaveableStateProvider(entry.id.toString()) {
                @Suppress("UNCHECKED_CAST")
                val screenScope = remember(entry) { ScreenScope(entry as NavEntry<Screen>, shell) }
                registration.content(screenScope, entry as NavEntry<Screen>)
            }
        }
    }
}
