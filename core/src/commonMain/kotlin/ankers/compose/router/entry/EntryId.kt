package ankers.compose.router.entry

/**
 * The stable identity of an entry: a path whose first segment names the root and whose remaining segments are
 * navigator-local counters, e.g. `root/3/2`. Every descendant of an entry shares its prefix, which is how a
 * retiring section finds the entries beneath it, including parked ones. Compared segment by segment, so
 * `root/1` is never a prefix of `root/10`.
 */
class EntryId private constructor(val segments: List<String>) {
    init {
        require(segments.isNotEmpty()) { "an entry id needs at least a root segment" }
    }

    val parent: EntryId?
        get() = if (segments.size > 1) EntryId(segments.dropLast(1)) else null

    fun child(ordinal: Int): EntryId = EntryId(segments + ordinal.toString())

    fun isDescendantOf(ancestor: EntryId): Boolean =
        segments.size > ancestor.segments.size &&
            segments.subList(0, ancestor.segments.size) == ancestor.segments

    fun isOrDescendantOf(ancestor: EntryId): Boolean = this == ancestor || isDescendantOf(ancestor)

    override fun equals(other: Any?): Boolean = other is EntryId && other.segments == segments
    override fun hashCode(): Int = segments.hashCode()
    override fun toString(): String = segments.joinToString("/")

    companion object {
        fun root(key: String): EntryId {
            require(key.isNotEmpty() && '/' !in key) { "root key must be non-empty and contain no '/'" }
            return EntryId(listOf(key))
        }

        fun parse(value: String): EntryId = EntryId(value.split('/'))
    }
}
