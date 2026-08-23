package ankers.compose.router

import ankers.compose.router.entry.EntryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EntryIdTest {
    @Test
    fun `child ids extend the parent path`() {
        val root = EntryId.root("root")
        assertEquals("root/3", root.child(3).toString())
        assertEquals("root/3/2", root.child(3).child(2).toString())
        assertEquals(root.child(3), root.child(3).child(2).parent)
        assertNull(root.parent)
    }

    @Test
    fun `prefix matching is segment-wise`() {
        val one = EntryId.parse("root/1")
        assertTrue(EntryId.parse("root/1/4").isDescendantOf(one))
        assertFalse(EntryId.parse("root/10").isDescendantOf(one))
        assertFalse(one.isDescendantOf(one))
        assertTrue(one.isOrDescendantOf(one))
    }

    @Test
    fun `parse round trips and equality is structural`() {
        val id = EntryId.parse("root/2/7")
        assertEquals(id, EntryId.root("root").child(2).child(7))
        assertEquals(id.hashCode(), EntryId.parse("root/2/7").hashCode())
    }

    @Test
    fun `root keys cannot contain separators`() {
        assertFailsWith<IllegalArgumentException> { EntryId.root("a/b") }
        assertFailsWith<IllegalArgumentException> { EntryId.root("") }
    }
}
