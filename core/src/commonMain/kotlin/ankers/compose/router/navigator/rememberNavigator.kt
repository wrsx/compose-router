package ankers.compose.router.navigator

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import ankers.compose.router.NavigationRoot
import ankers.compose.router.entry.EntryId
import ankers.compose.router.entry.NavEntry
import ankers.compose.router.events.NavigatorEvents
import ankers.compose.router.routeName
import kotlinx.coroutines.flow.filterNotNull
import kotlin.reflect.KClass

/** Creates the root navigator for [T] with the default [NavConfig.Stack] policy. */
@Composable
inline fun <reified T : NavigationRoot> rememberNavigator(
    key: String = T::class.simpleName ?: "root",
    events: NavigatorEvents = NavigatorEvents.Discard,
): StackNavigator<T> = rememberNavigator(T::class, NavConfig.Stack, key, events)

@Composable
inline fun <reified T : NavigationRoot> rememberNavigator(
    policy: NavConfig.Stack,
    key: String = T::class.simpleName ?: "root",
    events: NavigatorEvents = NavigatorEvents.Discard,
): StackNavigator<T> = rememberNavigator(T::class, policy, key, events)

/** Creates the root navigator for [T] with [policy]: a [NavConfig.Tab] or a custom [NavigatorPolicy]. */
@Composable
inline fun <reified T : NavigationRoot> rememberNavigator(
    policy: NavigatorPolicy<*>,
    key: String = T::class.simpleName ?: "root",
    events: NavigatorEvents = NavigatorEvents.Discard,
): Navigator<T> = rememberNavigator(T::class, policy, key, events)

@Composable
fun <T : NavigationRoot> rememberNavigator(
    type: KClass<T>,
    policy: NavConfig.Stack,
    key: String = type.routeName,
    events: NavigatorEvents = NavigatorEvents.Discard,
): StackNavigator<T> = rememberNavigator(rememberRootShell(policy, key, events)) { StackNavigator(it) }

@Composable
fun <T : NavigationRoot> rememberNavigator(
    type: KClass<T>,
    policy: NavigatorPolicy<*>,
    key: String = type.routeName,
    events: NavigatorEvents = NavigatorEvents.Discard,
): Navigator<T> = rememberNavigator(rememberRootShell(policy, key, events)) { Navigator(it) }

@Composable
internal fun <S : Any, N : Navigator<*>> rememberNavigator(shell: NavigatorShell<S>, create: (NavigatorShell<S>) -> N): N =
    remember(shell) { create(shell).also { shell.navigator = it } }

@Composable
internal fun <S : Any> rememberRootShell(
    policy: NavigatorPolicy<S>,
    key: String,
    events: NavigatorEvents,
): NavigatorShell<S> {
    val runtime = rememberNavigationRuntime(key)
    DisposableEffect(runtime, key) {
        check(runtime.roots.add(key)) { "two live root navigators share the key '$key'; pass distinct keys to rememberNavigator" }
        onDispose { runtime.roots.remove(key) }
    }
    return rememberShell(
        prefix = EntryId.root(key),
        policy = policy,
        runtime = runtime,
        events = events,
        parent = null,
    )
}

@Composable
internal fun <S : Any> rememberChildShell(
    parent: NavigatorShell<*>,
    entry: NavEntry<*>,
    policy: NavigatorPolicy<S>,
    events: NavigatorEvents,
): NavigatorShell<S> {
    val runtime = parent.runtime
    val childEvents = remember(entry.id, events) {
        val writer = checkNotNull(runtime.registry.writer(entry.id)) { "entry ${entry.id} is not registered" }
        writer.child() + events
    }
    return rememberShell(
        prefix = entry.id,
        policy = policy,
        runtime = runtime,
        events = childEvents,
        parent = parent,
    )
}

@Composable
private fun <S : Any> rememberShell(
    prefix: EntryId,
    policy: NavigatorPolicy<S>,
    runtime: NavigationRuntime,
    events: NavigatorEvents,
    parent: NavigatorShell<*>?,
): NavigatorShell<S> {
    val saver = remember(prefix, policy, runtime, events, parent) {
        listSaver<NavigatorShell<S>, Any?>(
            save = { it.snapshot().toSaveable() },
            restore = { NavigatorShell(prefix, policy, runtime, events, parent, NavigatorSnapshot.fromSaveable(it)) },
        )
    }
    val shell = rememberSaveable(prefix.toString(), policy.key, saver = saver) {
        NavigatorShell(prefix, policy, runtime, events, parent, restored = null)
    }
    LaunchedEffect(shell) {
        snapshotFlow { runtime.mailbox[prefix] }.filterNotNull().collect { shell.consumePending() }
    }
    return shell
}

/** Registers [child] as the single navigator under [entry]; a second one is an error. */
@Composable
internal fun RegisterChild(parent: NavigatorShell<*>, entry: NavEntry<*>, child: Navigator<*>) {
    DisposableEffect(parent, entry.id, child) {
        val existing = parent.children[entry.id]
        check(existing == null || existing === child) {
            "entry ${entry.id} already has a child navigator. An entry hosts at most one navigator; " +
                "model independent stacks as separate entries"
        }
        parent.children[entry.id] = child
        onDispose { if (parent.children[entry.id] === child) parent.children.remove(entry.id) }
    }
}
