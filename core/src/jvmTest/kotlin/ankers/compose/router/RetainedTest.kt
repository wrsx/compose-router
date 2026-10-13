package ankers.compose.router

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.lifecycle.SavedStateHandle
import ankers.compose.router.entry.NavEntry
import ankers.compose.router.host.retained
import ankers.compose.router.host.retainedValue
import ankers.compose.router.host.screen
import ankers.compose.router.navigator.StackNavigator
import ankers.compose.router.navigator.rememberNavigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RetainedTest {
    private class Probe(val scope: CoroutineScope) : AutoCloseable {
        var closed = false
        override fun close() { closed = true }
    }

    private class Counter(val handle: SavedStateHandle) {
        var count: Int
            get() = handle["count"] ?: 0
            set(value) { handle["count"] = value }
    }

    @Composable
    private fun retainedProbe(): Probe = retained { Probe(coroutineScope) }

    @Test
    fun `a parked entry keeps its value`() = runTest {
        val composition = TestComposition(this)
        val composed = Composed()
        var probe: Probe? = null
        lateinit var nav: StackNavigator<Root>
        composition.setContent {
            nav = rememberNavigator<Root>()
            Router(nav, renderer = PlainRenderer) {
                screen<A> {
                    probe = retainedProbe()
                    composed.mark("A")
                }
                screen<B> { composed.mark("B") }
            }
        }
        composition.waitUntil { composed["A"] > 0 }
        val first = probe!!

        nav.navigate(B)
        composition.waitUntil { composed["B"] > 0 }
        nav.back()
        composition.waitUntil { composed["A"] > 1 }

        assertSame(first, probe)
        assertFalse(first.closed)
        assertTrue(first.scope.isActive)
    }

    @Test
    fun `releasing an entry cancels its value's scope and closes it`() = runTest {
        val composition = TestComposition(this)
        val composed = Composed()
        var probe: Probe? = null
        lateinit var nav: StackNavigator<Root>
        composition.setContent {
            nav = rememberNavigator<Root>()
            Router(nav, renderer = PlainRenderer) {
                screen<A> { composed.mark("A") }
                screen<B> {
                    probe = retainedProbe()
                    composed.mark("B")
                }
            }
        }
        composition.waitUntil { composed["A"] > 0 }
        nav.navigate(B)
        composition.waitUntil { composed["B"] > 0 }
        val popped = probe!!
        assertFalse(popped.closed)

        nav.back()
        composition.waitUntil { popped.closed }
        assertFalse(popped.scope.isActive)
    }

    @Test
    fun `an entry retains one value per type unless keyed, and each entry its own`() = runTest {
        val composition = TestComposition(this)
        val composed = Composed()
        val byEntry = mutableMapOf<Int, List<Probe>>()
        lateinit var nav: StackNavigator<Root>
        composition.setContent {
            nav = rememberNavigator<Root>()
            Router(nav, renderer = PlainRenderer) {
                screen<A> { composed.mark("A") }
                screen<C> { entry ->
                    byEntry[entry.screen.n] = listOf(
                        retained { Probe(coroutineScope) },
                        retainedProbe(),
                        retained(key = "second") { Probe(coroutineScope) },
                    )
                    composed.mark("C ${entry.screen.n}")
                }
            }
        }
        composition.waitUntil { composed["A"] > 0 }
        nav.navigate(C(1))
        composition.waitUntil { composed["C 1"] > 0 }
        nav.navigate(C(2))
        composition.waitUntil { composed["C 2"] > 0 }

        val (direct, nested, keyed) = byEntry.getValue(1)
        assertSame(direct, nested)
        assertNotSame(direct, keyed)
        assertNotSame(direct, byEntry.getValue(2)[0])
    }

    @Test
    fun `naming another entry as owner shares its value for as long as that entry lives`() = runTest {
        val composition = TestComposition(this)
        val composed = Composed()
        lateinit var list: NavEntry<*>
        var listProbe: Probe? = null
        var overlayProbe: Probe? = null
        lateinit var nav: StackNavigator<Root>
        composition.setContent {
            nav = rememberNavigator<Root>()
            Router(nav, renderer = PlainRenderer) {
                screen<A> { entry ->
                    list = entry
                    listProbe = retainedProbe()
                    composed.mark("A")
                }
                screen<B> {
                    overlayProbe = retained(owner = list) { Probe(coroutineScope) }
                    composed.mark("B")
                }
                screen<C> { composed.mark("C") }
            }
        }
        composition.waitUntil { composed["A"] > 0 }
        nav.navigate(B)
        composition.waitUntil { composed["B"] > 0 }
        val shared = listProbe!!
        assertSame(shared, overlayProbe)

        nav.back()
        composition.waitUntil { composed["A"] > 1 }
        assertFalse(shared.closed)

        nav.replace(C(1))
        composition.waitUntil { shared.closed }
    }

    @Test
    fun `a restored entry recreates its value with the saved state it had`() = runTest {
        val composed = Composed()
        var counter: Counter? = null
        lateinit var nav: StackNavigator<Root>
        val content: @Composable () -> Unit = {
            nav = rememberNavigator<Root>()
            Router(nav, renderer = PlainRenderer) {
                screen<A> { composed.mark("A") }
                screen<C> {
                    counter = retained { Counter(savedStateHandle) }
                    composed.mark("C")
                }
            }
        }
        var registry = SaveableStateRegistry(restoredValues = null, canBeSaved = { true })
        val app: @Composable () -> Unit = {
            CompositionLocalProvider(LocalSaveableStateRegistry provides registry) { content() }
        }

        val first = TestComposition(this)
        first.setContent(app)
        first.waitUntil { composed["A"] > 0 }
        nav.navigate(C(3))
        first.waitUntil { composed["C"] > 0 }
        val before = counter!!
        before.count = 5
        val saved = registry.performSave()
        first.dispose()

        registry = SaveableStateRegistry(restoredValues = saved, canBeSaved = { true })
        val second = TestComposition(this)
        second.setContent(app)
        second.waitUntil { composed["C"] > 1 }
        val after = counter!!
        assertNotSame(before, after)
        assertEquals(5, after.count)
        assertEquals(C(3), after.handle.screen<C>())
    }

    @Test
    fun `retained outside an entry fails with a reason`() = runTest {
        val composition = TestComposition(this)
        composition.setContent { retainedProbe() }
        val error = assertFails { composition.waitUntil(frames = 2) { false } }
        assertTrue("needs an entry" in error.message.orEmpty(), error.toString())
    }

    @Test
    fun `a key holds one type, local classes need a key, and a released entry retains nothing`() {
        class Local

        val entry = ownerEntry("Root/1", A)
        retainedValue(entry, String::class, "shared") { "text" }
        val collision = assertFails { retainedValue(entry, Int::class, "shared") { 1 } }
        assertTrue("give each type its own key" in collision.message.orEmpty(), collision.toString())

        val unnamed = assertFails { retainedValue(entry, Local::class, key = null) { Local() } }
        assertTrue("needs a key" in unnamed.message.orEmpty(), unnamed.toString())
        retainedValue(entry, Local::class, "local") { Local() }

        val probe = retainedValue(entry, Probe::class, key = null) { Probe(coroutineScope) }
        entry.release()
        assertTrue(probe.closed)
        val released = assertFails { retainedValue(entry, Probe::class, key = null) { Probe(coroutineScope) } }
        assertTrue("has been released" in released.message.orEmpty(), released.toString())
    }
}
