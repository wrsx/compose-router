package ankers.compose.router

import kotlin.test.Test
import kotlin.test.assertEquals

class ChainSegmentsTest {
    @Test
    fun `siblings flatten in push order on this navigator`() {
        val chain = A.then(B).then(C(1))
        assertEquals(listOf(null to A, null to B, null to C(1)), chain.segments)
    }

    @Test
    fun `children are addressed to the screen that hosts their navigator`() {
        val chain = Tabs.then(TabB).then(Deep).then(Deeper("x"))
        assertEquals(
            listOf(null to Tabs, Tabs to TabB, TabB to Deep, TabB to Deeper("x")),
            chain.segments,
        )
    }

    @Test
    fun `a sibling after a child stays on the child's navigator`() {
        val chain = Tabs.then(TabA).then(TabB)
        assertEquals(listOf(null to Tabs, Tabs to TabA, Tabs to TabB), chain.segments)
    }
}
