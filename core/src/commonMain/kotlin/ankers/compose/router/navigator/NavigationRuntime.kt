package ankers.compose.router.navigator

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModelStore
import ankers.compose.router.Screen
import ankers.compose.router.entry.EntryId
import ankers.compose.router.entry.EntryRegistry
import ankers.compose.router.host.OwnerEntry
import ankers.compose.router.host.OwnerPlatform
import ankers.compose.router.host.screenSerializer

/**
 * Everything one navigation tree retains across recomposition and, on Android, configuration changes: the entry
 * registry, the ViewModel stores, and pending chained navigations. Entries restored after recreation re-attach
 * to their stores by id.
 */
class NavigationRuntime(private val platform: OwnerPlatform) {
    private val stores = HashMap<EntryId, ViewModelStore>()
    internal val registry = EntryRegistry(onReleased = ::released)
    internal val mailbox = mutableStateMapOf<EntryId, List<PendingChain>>()
    internal val roots = mutableSetOf<String>()

    internal fun create(screen: Screen, id: EntryId): OwnerEntry<*> {
        screenSerializer(screen)
        return OwnerEntry(id, screen, stores.getOrPut(id) { ViewModelStore() }, platform)
    }

    // the rest of a chain, addressed to the navigator hosted by one entry; queued in order, dropped with that entry
    internal fun post(to: EntryId, pending: PendingChain) {
        mailbox[to] = mailbox[to].orEmpty() + pending
    }

    private fun released(id: EntryId) {
        stores.remove(id)
        mailbox.remove(id)
    }

    /** Ends the tree for good: releases every entry deepest-first, so observers see each `retired()`, then forgets everything. */
    fun clear() {
        registry.releaseAll()
        stores.values.forEach { it.clear() }
        stores.clear()
        mailbox.clear()
        roots.clear()
    }
}

/** Segments still to deliver, starting with those owned by the navigator under the entry whose screen is [anchor]. */
internal class PendingChain(val anchor: Screen, val segments: List<Pair<Screen?, Screen>>)

/** Provides the retained runtime for a root navigator. Platforms decide how it survives recreation. */
@Composable
expect fun rememberNavigationRuntime(rootKey: String): NavigationRuntime
