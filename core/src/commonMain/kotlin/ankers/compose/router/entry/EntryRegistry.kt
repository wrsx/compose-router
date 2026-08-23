package ankers.compose.router.entry

import ankers.compose.router.events.EntryEvents
import ankers.compose.router.events.HostEvents
import ankers.compose.router.events.HostState
import ankers.compose.router.events.NavigatorEvents
import ankers.compose.router.host.OwnerEntry

/**
 * The retained record of every live entry under one root: its observer, whether a navigator still owns it, and
 * whether a host is rendering it. Drives each entry's lifecycle from hosting and releases it when retired.
 *
 * An entry is released when no navigator owns it and no live host has an id under its prefix — parked leaves at
 * once, ancestors of a still-hosted descendant only after that descendant exits.
 */
class EntryRegistry internal constructor(private val onReleased: (EntryId) -> Unit = {}) {
    internal val releaseListeners = mutableListOf<(EntryId) -> Unit>()

    private class Record(var entry: OwnerEntry<*>, val writer: EntryEvents) {
        var owned = true
        var hosted = false
    }

    private val records = LinkedHashMap<EntryId, Record>()

    /** Receives diagnostics such as dropped navigations. Defaults to stdout. */
    var diagnostics: (String) -> Unit = { println("compose-router: $it") }

    internal fun register(entry: OwnerEntry<*>, events: NavigatorEvents): EntryEvents {
        val existing = records[entry.id]
        if (existing != null) {
            existing.entry = entry
            existing.owned = true
            return existing.writer
        }
        val writer = events.entry(entry)
        val record = Record(entry, writer)
        // born beneath a retiring ancestor: released as soon as nothing hosts it
        if (records.any { (key, r) -> !r.owned && entry.id.isDescendantOf(key) }) record.owned = false
        records[entry.id] = record
        return writer
    }

    internal fun writer(id: EntryId): EntryEvents? = records[id]?.writer

    internal fun entry(id: EntryId): OwnerEntry<*>? = records[id]?.entry

    internal fun contains(id: EntryId): Boolean = id in records

    internal fun isOwned(id: EntryId): Boolean = records[id]?.owned == true

    internal fun isHosted(id: EntryId): Boolean = records[id]?.hosted == true

    /** Begins a host. Fails when the entry is already hosted elsewhere: an entry lives in one place at a time. */
    internal fun host(id: EntryId): HostEvents {
        val record = checkNotNull(records[id]) { "entry $id is not registered: it was released or never created" }
        check(!record.hosted) {
            "entry $id is already rendered elsewhere. An entry may be hosted from one place at a time; " +
                "keep each entry in one slot and animate slots, not scenes"
        }
        record.hosted = true
        val entry = record.entry
        val owner = object : HostEvents {
            override fun state(state: HostState) = entry.host(state)
            override fun unhosted() = entry.unhosted()
        }
        return owner + record.writer.hosted()
    }

    internal fun unhost(id: EntryId) {
        val record = records[id] ?: return
        record.hosted = false
        releaseReady()
    }

    /** Marks [id] and everything beneath it unowned, then releases whatever nothing hosts any more. */
    internal fun retire(id: EntryId) {
        records.forEach { (key, record) -> if (key.isOrDescendantOf(id)) record.owned = false }
        releaseReady()
    }

    /** Releases every entry, deepest first, whether or not anything still hosts it. */
    internal fun releaseAll() {
        records.values.forEach { it.owned = false; it.hosted = false }
        releaseReady()
    }

    private fun hostedUnder(prefix: EntryId): Boolean =
        records.any { (key, record) -> record.hosted && key.isOrDescendantOf(prefix) }

    private fun releaseReady() {
        // deepest first so a parent never observes a released child out of order
        val ready = records
            .filter { (key, record) -> !record.owned && !hostedUnder(key) }
            .keys
            .sortedByDescending { it.segments.size }
        ready.forEach { key ->
            val record = records.remove(key) ?: return@forEach
            record.entry.release()
            onReleased(key)
            releaseListeners.toList().forEach { it(key) }
            record.writer.retired()
        }
    }
}
