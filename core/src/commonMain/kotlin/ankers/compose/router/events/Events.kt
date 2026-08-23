package ankers.compose.router.events

import ankers.compose.router.entry.NavEntry

/**
 * What a host is currently doing with an entry. The host folds the renderer's `active` and `handlesBack`
 * arguments and the entry's ownership into one of these; platform bindings map them to lifecycle caps.
 */
enum class HostState {
    /** Selected, settled, uncovered. */
    Active,

    /** Rendered but transitioning, peeked, or otherwise not in front. */
    Inactive,

    /** Rendered beneath an overlay; excluded from back resolution. */
    Covered,

    /** No longer owned; still rendered while an exit transition completes. */
    Retiring,
}

/**
 * Observes one host of one entry. Observe-only: the library never reads anything back out of a writer,
 * which is what makes [plus] safe.
 */
interface HostEvents {
    fun state(state: HostState)

    /** Terminal for this host. */
    fun unhosted()

    operator fun plus(other: HostEvents): HostEvents = CombinedHostEvents(this, other)

    object Discard : HostEvents {
        override fun state(state: HostState) {}
        override fun unhosted() {}
        override fun plus(other: HostEvents): HostEvents = other
    }
}

/** Observes one entry for its whole life. Returned per entry by [NavigatorEvents.entry]. */
interface EntryEvents {
    /** A host began rendering the entry. */
    fun hosted(): HostEvents

    /** A navigator was created under the entry. */
    fun child(): NavigatorEvents

    /** Released: not owned by any navigator and nothing beneath it is hosted. Terminal. */
    fun retired()

    operator fun plus(other: EntryEvents): EntryEvents = CombinedEntryEvents(this, other)

    object Discard : EntryEvents {
        override fun hosted(): HostEvents = HostEvents.Discard
        override fun child(): NavigatorEvents = NavigatorEvents.Discard
        override fun retired() {}
        override fun plus(other: EntryEvents): EntryEvents = other
    }
}

/**
 * Observes one navigator. Pass one to `rememberNavigator(events = ...)`; the platform binding combines it with
 * its own. Writers compose with [plus] and [Discard] is the unit, so a partial observer is
 * `object : NavigatorEvents by NavigatorEvents.Discard { ... }`.
 */
interface NavigatorEvents {
    /** An entry was created by the factory: pushed, or reconstructed after process death. */
    fun entry(entry: NavEntry<*>): EntryEvents

    operator fun plus(other: NavigatorEvents): NavigatorEvents = CombinedNavigatorEvents(this, other)

    object Discard : NavigatorEvents {
        override fun entry(entry: NavEntry<*>): EntryEvents = EntryEvents.Discard
        override fun plus(other: NavigatorEvents): NavigatorEvents = other
    }
}

private class CombinedHostEvents(private val lhs: HostEvents, private val rhs: HostEvents) : HostEvents {
    override fun state(state: HostState) {
        lhs.state(state)
        rhs.state(state)
    }

    override fun unhosted() {
        lhs.unhosted()
        rhs.unhosted()
    }
}

private class CombinedEntryEvents(private val lhs: EntryEvents, private val rhs: EntryEvents) : EntryEvents {
    override fun hosted(): HostEvents = lhs.hosted() + rhs.hosted()
    override fun child(): NavigatorEvents = lhs.child() + rhs.child()
    override fun retired() {
        lhs.retired()
        rhs.retired()
    }
}

private class CombinedNavigatorEvents(private val lhs: NavigatorEvents, private val rhs: NavigatorEvents) : NavigatorEvents {
    override fun entry(entry: NavEntry<*>): EntryEvents = lhs.entry(entry) + rhs.entry(entry)
}
