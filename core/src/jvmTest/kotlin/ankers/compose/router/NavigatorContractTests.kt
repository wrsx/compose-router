package ankers.compose.router

import ankers.compose.router.entry.EntryId
import ankers.compose.router.navigator.NavConfig
import ankers.compose.router.navigator.Navigator
import ankers.compose.router.navigator.NavigatorPolicy
import ankers.compose.router.navigator.NavigatorShell
import ankers.compose.router.navigator.NavigatorSnapshot
import ankers.compose.router.navigator.StackNavigator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Written once against the navigator contract and run per policy, the way koala runs one suite per dialect.
 * A custom policy inherits this to learn what the shell expects of it.
 */
internal abstract class NavigatorContractTests<S : Any> {
    abstract val policy: NavigatorPolicy<S>
    abstract fun navigator(shell: NavigatorShell<S>): Navigator<Root>

    protected val events = RecordingEvents()
    protected val runtime = testRuntime()
    protected val diagnostics = mutableListOf<String>().also { sink -> runtime.registry.diagnostics = { sink += it } }

    protected fun shell(restored: NavigatorSnapshot? = null): NavigatorShell<S> =
        NavigatorShell(EntryId.root("root"), policy, runtime, events, null, restored)

    protected fun create(restored: NavigatorSnapshot? = null): Navigator<Root> =
        shell(restored).let { shell -> navigator(shell).also { shell.navigator = it } }

    protected val Navigator<*>.ids get() = entries.map { it.id.toString() }
    protected val Navigator<*>.screens get() = entries.map { it.screen }

    @Test
    fun `starts empty with nothing to go back to`() {
        val nav = create()
        assertTrue(nav.isEmpty)
        assertNull(nav.selected)
        assertNull(nav.backAction)
        assertFalse(nav.canGoBack)
    }

    @Test
    fun `navigate creates an owned selected entry with the next id`() {
        val nav = create()
        nav.navigate(A)
        nav.navigate(B)
        assertEquals(listOf("root/1", "root/2"), nav.ids)
        assertEquals(B, nav.selected?.screen)
        assertEquals(listOf("entry root/1 A", "entry root/2 B"), events.of("entry "))
    }

    @Test
    fun `back commits the resolved step and a stale action is a no-op`() {
        val nav = create()
        nav.navigate(A)
        nav.navigate(B)
        val action = assertNotNull(nav.backAction)
        assertEquals(B, action.outgoing.screen)
        assertEquals(A, action.incoming.screen)

        nav.navigate(C(1))
        action.commit()
        assertEquals(C(1), nav.selected?.screen)

        val fresh = assertNotNull(nav.backAction)
        nav.back()
        assertEquals(fresh.incoming, nav.selected)
    }

    @Test
    fun `stateOf and mutate are gated on the policy the navigator was created with`() {
        val nav = create()
        nav.navigate(A)
        assertFailsWith<IllegalArgumentException> { nav.stateOf(OtherPolicy) }
        assertFailsWith<IllegalArgumentException> { nav.mutate(OtherPolicy) {} }
        nav.mutate(policy) { it.select(it.owned.first()) }
        assertEquals(A, nav.selected?.screen)
    }

    @Test
    fun `a back action is void once the navigator has mutated since it was resolved`() {
        val nav = create()
        nav.navigate(A)
        nav.navigate(B)
        val action = nav.backAction!!
        nav.navigate(C(1))
        nav.pop()
        assertEquals(B, nav.selected?.screen)
        action.commit()
        assertEquals(B, nav.selected?.screen)
        nav.back()
        assertEquals(A, nav.selected?.screen)
    }

    @Test
    fun `a snapshot restores entries selection and policy state and the counter continues`() {
        val first = create()
        first.navigate(A)
        first.navigate(B)
        first.navigate(A)
        val saved = first.shell.snapshot().toSaveable()

        val restored = create(NavigatorSnapshot.fromSaveable(saved))
        assertEquals(first.describe(), restored.describe())
        assertEquals(first.selected?.id, restored.selected?.id)

        restored.navigate(C(9))
        val next = first.ids.maxOf { it.substringAfterLast('/').toInt() } + 1
        assertEquals("root/$next", restored.entries.last { it.screen == C(9) }.id.toString())
    }

    @Test
    fun `describe names the policy the entries and the selection`() {
        val nav = create()
        nav.navigate(A)
        nav.navigate(B)
        val text = nav.describe()
        assertTrue(text.startsWith("root [${policy.key}] selected=root/2"), text)
        assertTrue("root/1 A" in text && "root/2 B *" in text, text)
    }

    @Test
    fun `navigation to a route missing from the live graph is dropped with a diagnostic`() {
        val nav = create()
        nav.shell.liveRoutes = listOf(A::class)
        nav.navigate(A)
        nav.navigate(B)
        assertEquals(listOf(A), nav.screens)
        assertEquals(1, diagnostics.size)
        assertTrue("B" in diagnostics.single(), diagnostics.single())
    }

    @Test
    fun `reconcile retires entries whose route left the graph and reselects`() {
        val nav = create()
        nav.navigate(A)
        nav.navigate(B)
        val gone = nav.shell.reconcile(listOf(A::class))
        assertEquals(listOf(B), gone.map { it.screen })
        assertEquals(listOf(A), nav.screens)
        assertEquals(A, nav.selected?.screen)
        assertEquals(listOf("root/2"), events.retired())
    }
}

internal class StackNavigatorTests : NavigatorContractTests<Unit>() {
    override val policy = NavConfig.Stack
    override fun navigator(shell: NavigatorShell<Unit>) = StackNavigator<Root>(shell)

    @Test
    fun `pop removes from the top and never empties the navigator`() {
        val nav = create()
        nav.navigate(A)
        nav.navigate(B)
        nav.navigate(C(1))
        nav.pop()
        assertEquals(listOf(A, B), nav.screens)
        nav.pop(5)
        assertEquals(listOf(A), nav.screens)
        assertEquals(listOf("root/3", "root/2"), events.retired())
    }

    @Test
    fun `popTo stops at the route exclusive or inclusive`() {
        val nav = create() as StackNavigator<Root>
        nav.navigate(A)
        nav.navigate(B)
        nav.navigate(C(1))
        nav.navigate(C(2))
        nav.popTo<B>()
        assertEquals(listOf(A, B), nav.screens)
        nav.navigate(C(3))
        nav.popTo<B>(inclusive = true)
        assertEquals(listOf(A), nav.screens)
    }

    @Test
    fun `replace swaps the top entry`() {
        val nav = create() as StackNavigator<Root>
        nav.navigate(A)
        nav.navigate(B)
        nav.replace(C(1))
        assertEquals(listOf(A, C(1)), nav.screens)
        assertEquals(listOf("root/2"), events.retired())
    }

    @Test
    fun `singleTop does not push an equal screen twice`() {
        val nav = create() as StackNavigator<Root>
        nav.navigate(C(1))
        nav.navigate(C(1), singleTop = true)
        nav.navigate(C(2), singleTop = true)
        assertEquals(listOf(C(1), C(2)), nav.screens)
    }

    @Test
    fun `popToRoot keeps the first entry`() {
        val nav = create()
        nav.navigate(A)
        nav.navigate(B)
        nav.navigate(C(1))
        nav.popToRoot()
        assertEquals(listOf(A), nav.screens)
        assertNull(nav.backAction)
    }
}

internal abstract class TabNavigatorContractTests(backPress: NavConfig.Tab.BackPress) : NavigatorContractTests<NavConfig.Tab.History>() {
    override val policy = NavConfig.Tab(backPress)
    override fun navigator(shell: NavigatorShell<NavConfig.Tab.History>) = Navigator<Root>(shell)

    protected fun tabs(): Navigator<Root> = create().also { it.shell.reconcile(listOf(A::class, B::class, C::class)) }

    @Test
    fun `selecting a tab again reuses its entry and releases nothing`() {
        val nav = tabs()
        nav.navigate(A)
        nav.navigate(B)
        nav.navigate(A)
        assertEquals(listOf("root/1", "root/2"), nav.ids)
        assertEquals(A, nav.selected?.screen)
        assertEquals(emptyList(), events.retired())
    }

    @Test
    fun `owned tabs follow declaration order not selection order`() {
        val nav = tabs()
        nav.navigate(C(1))
        nav.navigate(A)
        assertEquals(listOf(A, C(1)), nav.screens)
        assertEquals(A, nav.entries.first().screen)
    }

    @Test
    fun `popToRoot returns to the first declared tab`() {
        val nav = tabs()
        nav.navigate(B)
        nav.navigate(C(1))
        nav.navigate(A)
        nav.navigate(C(1))
        nav.popToRoot()
        assertEquals(A, nav.selected?.screen)
        assertNull(nav.backAction)
    }
}

internal class TabHistoryBackTests : TabNavigatorContractTests(NavConfig.Tab.BackPress.Stack) {
    @Test
    fun `back walks the history then lands on the first tab`() {
        val nav = tabs()
        nav.navigate(A)
        nav.navigate(C(1))
        nav.navigate(B)
        nav.back()
        assertEquals(C(1), nav.selected?.screen)
        nav.back()
        assertEquals(A, nav.selected?.screen)
        assertNull(nav.backAction)
    }

    @Test
    fun `back from a non-first tab with no history goes to the first tab`() {
        val nav = tabs()
        nav.navigate(B)
        assertEquals(B, nav.selected?.screen)
        nav.back()
        assertEquals(A, nav.selected?.screen)
    }

    @Test
    fun `pop steps history and affects ownership of nothing`() {
        val nav = tabs()
        nav.navigate(A)
        nav.navigate(B)
        nav.pop()
        assertEquals(A, nav.selected?.screen)
        assertEquals(listOf(A, B), nav.screens)
    }
}

internal class TabFirstBackTests : TabNavigatorContractTests(NavConfig.Tab.BackPress.First) {
    @Test
    fun `back always returns to the first tab and then has nothing to do`() {
        val nav = tabs()
        nav.navigate(A)
        nav.navigate(B)
        nav.navigate(C(1))
        assertEquals(A, nav.backAction?.incoming?.screen)
        nav.back()
        assertEquals(A, nav.selected?.screen)
        assertNull(nav.backAction)
    }

    @Test
    fun `back reaches the first tab through the policy while pop steps history`() {
        val nav = tabs()
        nav.navigate(A)
        nav.navigate(B)
        nav.navigate(C(1))
        nav.pop()
        assertEquals(B, nav.selected?.screen)
        nav.back()
        assertEquals(A, nav.selected?.screen)
    }
}

private object OtherPolicy : NavigatorPolicy<Unit> by NavConfig.Stack {
    override val key = "other"
}
