package ankers.compose.router

import ankers.compose.router.entry.EntryRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntryRegistryTest {
    private val events = RecordingEvents()
    private val registry = EntryRegistry()

    private fun register(path: String) = ownerEntry(path, A).also { registry.register(it, events) }

    @Test
    fun `an entry may be hosted from one place at a time`() {
        register("root/1")
        registry.host(id("root/1"))
        val error = assertFailsWith<IllegalStateException> { registry.host(id("root/1")) }
        assertTrue("one slot" in error.message!! || "one place" in error.message!!)
        registry.unhost(id("root/1"))
        registry.host(id("root/1"))
    }

    @Test
    fun `a parked leaf is released as soon as it is retired`() {
        register("root/1")
        registry.retire(id("root/1"))
        assertEquals(listOf("root/1"), events.retired())
        assertFalse(registry.contains(id("root/1")))
    }

    @Test
    fun `a hosted entry is released only when its host goes`() {
        register("root/1")
        registry.host(id("root/1"))
        registry.retire(id("root/1"))
        assertEquals(emptyList(), events.retired())
        assertFalse(registry.isOwned(id("root/1")))
        registry.unhost(id("root/1"))
        assertEquals(listOf("root/1"), events.retired())
    }

    @Test
    fun `retiring a prefix releases parked descendants but keeps ancestors of a hosted one`() {
        register("root/1")
        register("root/1/1")
        register("root/1/2")
        register("root/1/2/1")
        registry.host(id("root/1/2/1")) // e.g. a projected sheet still animating out

        registry.retire(id("root/1"))

        assertEquals(listOf("root/1/1"), events.retired())
        registry.unhost(id("root/1/2/1"))
        assertEquals(listOf("root/1/1", "root/1/2/1", "root/1/2", "root/1"), events.retired())
    }

    @Test
    fun `prefix retirement is segment-aware`() {
        register("root/1")
        register("root/10")
        registry.retire(id("root/1"))
        assertEquals(listOf("root/1"), events.retired())
        assertTrue(registry.contains(id("root/10")))
    }

    @Test
    fun `re-registering a restored entry keeps its writer and marks it owned`() {
        val first = register("root/1")
        registry.retire(id("root/1"))
        assertEquals(1, events.retired().size)
        val again = ownerEntry("root/1", B)
        registry.register(again, events)
        registry.register(again, events)
        assertEquals(2, events.of("entry ").size)
        assertTrue(registry.isOwned(id("root/1")))
        assertEquals(again, registry.entry(id("root/1")))
        assertTrue(first.id == again.id)
    }

    @Test
    fun `an entry born beneath a retiring ancestor is released with it`() {
        register("root/1")
        registry.host(id("root/1"))
        registry.retire(id("root/1"))
        register("root/1/1")
        assertFalse(registry.isOwned(id("root/1/1")))
        registry.unhost(id("root/1"))
        assertEquals(listOf("root/1/1", "root/1"), events.retired())
    }

    @Test
    fun `releaseAll ends every entry deepest first`() {
        register("root/1")
        register("root/1/1")
        register("root/2")
        registry.host(id("root/1/1"))
        registry.releaseAll()
        assertEquals(listOf("root/1/1", "root/1", "root/2"), events.retired())
    }
}
