package ankers.compose.router

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import ankers.compose.router.navigator.NavConfig
import ankers.compose.router.navigator.Navigator
import ankers.compose.router.navigator.StackNavigator
import ankers.compose.router.navigator.rememberNavigator
import ankers.compose.router.render.RouterRenderer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import androidx.compose.runtime.LaunchedEffect
import ankers.compose.router.back.BackEdge
import ankers.compose.router.back.BackTransition
import kotlin.test.assertFails
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RouterTest {
    @Test
    fun `the first registered route is the start destination`() = runTest {
        val composition = TestComposition(this)
        val events = RecordingEvents()
        val composed = Composed()
        composition.setContent {
            val nav = rememberNavigator<Root>(events = events)
            Router(nav, renderer = PlainRenderer) {
                screen<A> { composed.mark("A") }
                screen<B> { composed.mark("B") }
            }
        }
        composition.waitUntil { composed["A"] > 0 }
        assertEquals(0, composed["B"])
        assertEquals(listOf("entry Root/1 A", "hosted Root/1", "state Root/1 Active"), events.log)
    }

    @Test
    fun `push parks the previous entry and pop releases the popped one`() = runTest {
        val composition = TestComposition(this)
        val events = RecordingEvents()
        val composed = Composed()
        lateinit var nav: StackNavigator<Root>
        composition.setContent {
            nav = rememberNavigator<Root>(events = events)
            Router(nav, renderer = PlainRenderer) {
                screen<A> { composed.mark("A") }
                screen<B> { composed.mark("B") }
            }
        }
        composition.waitUntil { composed["A"] > 0 }

        nav.navigate(B)
        composition.waitUntil { composed["B"] > 0 }
        assertTrue("unhosted Root/1" in events.log)
        assertEquals(emptyList(), events.retired())

        nav.back()
        composition.waitUntil { "retired Root/2" in events.log }
        assertEquals(
            listOf("unhosted Root/2", "retired Root/2", "hosted Root/1", "state Root/1 Active"),
            events.log.dropWhile { it != "unhosted Root/2" },
        )
        assertFalse(nav.canGoBack)
    }

    @Test
    fun `a structural change retires the removed section and starts the new one`() = runTest {
        val composition = TestComposition(this)
        val events = RecordingEvents()
        val composed = Composed()
        var signedIn by mutableStateOf(true)
        lateinit var nav: Navigator<Root>
        composition.setContent {
            nav = rememberNavigator<Root>(events = events)
            Router(nav, renderer = PlainRenderer) {
                if (signedIn) screen<A> { composed.mark("A") } else screen<SignedOut> { composed.mark("out") }
                screen<B> { composed.mark("B") }
            }
        }
        composition.waitUntil { composed["A"] > 0 }

        signedIn = false
        composition.waitUntil { composed["out"] > 0 }
        assertEquals(listOf(SignedOut), nav.entries.map { it.screen })
        assertEquals(listOf("Root/1"), events.retired())

        signedIn = true
        composition.waitUntil { composed["A"] > 1 }
        assertEquals(listOf("Root/1", "Root/2"), events.retired())
        assertEquals("Root/3", nav.selected?.id.toString())
    }

    @Test
    fun `chained navigation reaches navigators that do not exist yet and back unwinds deepest first`() = runTest {
        val composition = TestComposition(this)
        val composed = Composed()
        lateinit var root: StackNavigator<Root>
        composition.setContent {
            root = rememberNavigator<Root>()
            Router(root, renderer = PlainRenderer) {
                screen<A> { composed.mark("A") }
                screen<Tabs> {
                    val tabs = rememberNavigator(NavConfig.Tab())
                    Router(tabs, renderer = PlainRenderer) {
                        screen<TabA> { composed.mark("TabA") }
                        screen<TabB> {
                            val stack = rememberNavigator()
                            Router(stack, renderer = PlainRenderer) {
                                screen<Deep> { composed.mark("Deep") }
                                screen<Deeper> { composed.mark("Deeper ${it.screen.id}") }
                            }
                        }
                    }
                }
            }
        }
        composition.waitUntil { composed["A"] > 0 }

        root.navigate(Tabs.then(TabB).then(Deep).then(Deeper("x")))
        composition.waitUntil { composed["Deeper x"] > 0 }
        assertEquals(0, composed["TabA"])
        val described = root.describe()
        assertTrue("Root/2/2/2 Deeper *" in described, described)
        assertTrue("Root/2/1 TabA" in described, described)

        root.back()
        composition.waitUntil { composed["Deep"] > 0 }
        root.back()
        composition.waitUntil { composed["TabA"] > 0 }
        root.back()
        composition.waitUntil { composed["A"] > 1 }
        assertFalse(root.canGoBack)
    }

    @Test
    fun `an entry may host one navigator`() = runTest {
        val composition = TestComposition(this)
        composition.setContent {
            val root = rememberNavigator<Root>()
            Router(root, renderer = PlainRenderer) {
                screen<Tabs> {
                    rememberNavigator()
                    rememberNavigator()
                }
            }
        }
        val error = assertFailsWith<IllegalStateException> { composition.waitUntil { false } }
        assertTrue("one navigator" in error.message!!, error.message)
    }

    @Test
    fun `navigation to a route outside the live graph is dropped`() = runTest {
        val composition = TestComposition(this)
        val diagnostics = mutableListOf<String>()
        val composed = Composed()
        lateinit var nav: StackNavigator<Root>
        composition.setContent {
            nav = rememberNavigator<Root>()
            nav.shell.runtime.registry.diagnostics = { diagnostics += it }
            Router(nav, renderer = PlainRenderer) {
                screen<A> { composed.mark("A") }
            }
        }
        composition.waitUntil { composed["A"] > 0 }
        nav.navigate(B)
        composition.frames(2)
        assertEquals(listOf(A), nav.entries.map { it.screen })
        assertEquals(1, diagnostics.size)
    }

    @Test
    fun `re-selecting a tab keeps its entry and its nested stack`() = runTest {
        val composition = TestComposition(this)
        val events = RecordingEvents()
        val composed = Composed()
        lateinit var tabs: Navigator<Tabs>
        composition.setContent {
            val root = rememberNavigator<Root>(events = events)
            Router(root, renderer = PlainRenderer) {
                screen<Tabs> {
                    tabs = rememberNavigator(NavConfig.Tab())
                    Router(tabs, renderer = PlainRenderer) {
                        screen<TabA> { composed.mark("TabA") }
                        screen<TabB> {
                            val stack = rememberNavigator()
                            Router(stack, renderer = PlainRenderer) {
                                screen<Deep> {
                                    composed.mark("Deep")
                                    androidx.compose.runtime.LaunchedEffect(Unit) { if (composed["Deeper"] == 0) stack.navigate(Deeper("y")) }
                                }
                                screen<Deeper> { composed.mark("Deeper") }
                            }
                        }
                    }
                }
            }
        }
        composition.waitUntil { composed["TabA"] > 0 }
        tabs.navigate(TabB)
        composition.waitUntil { composed["Deeper"] > 0 }
        tabs.navigate(TabA)
        composition.waitUntil { composed["TabA"] > 1 }
        tabs.navigate(TabB)
        composition.waitUntil { composed["Deeper"] > 1 }
        assertEquals(emptyList(), events.retired())
        assertEquals(1, composed["Deep"])
    }

    @Test
    fun `retiring a section releases descendants parked in other tabs`() = runTest {
        val composition = TestComposition(this)
        val events = RecordingEvents()
        val composed = Composed()
        var signedIn by mutableStateOf(true)
        lateinit var tabs: Navigator<Tabs>
        composition.setContent {
            val root = rememberNavigator<Root>(events = events)
            Router(root, renderer = PlainRenderer) {
                if (signedIn) {
                    screen<Tabs> {
                        tabs = rememberNavigator(NavConfig.Tab())
                        Router(tabs, renderer = PlainRenderer) {
                            screen<TabA> { composed.mark("TabA") }
                            screen<TabB> {
                                val stack = rememberNavigator()
                                Router(stack, renderer = PlainRenderer) {
                                    screen<Deep> { composed.mark("Deep") }
                                }
                            }
                        }
                    }
                } else {
                    screen<SignedOut> { composed.mark("out") }
                }
            }
        }
        composition.waitUntil { composed["TabA"] > 0 }
        tabs.navigate(TabB)
        composition.waitUntil { composed["Deep"] > 0 }
        tabs.navigate(TabA)
        composition.waitUntil { composed["TabA"] > 1 }

        signedIn = false
        composition.waitUntil { composed["out"] > 0 }
        assertEquals(setOf("Root/1", "Root/1/1", "Root/1/2", "Root/1/2/1"), events.retired().toSet())
    }

    @Test
    fun `coverage excludes an entry from the back path and promotion redirects it`() = runTest {
        val composition = TestComposition(this)
        val composed = Composed()
        var mode by mutableStateOf("plain")
        lateinit var root: StackNavigator<Root>
        lateinit var tabs: Navigator<Tabs>
        // one call site for every mode: an entry re-placed elsewhere would lose its composition
        val renderer: RouterRenderer = {
            val shown = if (mode == "plain") listOfNotNull(inlineSelected) else entries
            renderEach(shown) {
                render(
                    it,
                    handlesBack = when (mode) {
                        "covered" -> false
                        "promoted" -> if (it.screen == Tabs) true else null
                        else -> null
                    },
                )
            }
        }
        composition.setContent {
            root = rememberNavigator<Root>()
            Router(root, renderer = renderer) {
                screen<Tabs> {
                    tabs = rememberNavigator(NavConfig.Tab())
                    Router(tabs, renderer = PlainRenderer) {
                        screen<TabA> { composed.mark("TabA") }
                        screen<TabB> { composed.mark("TabB") }
                    }
                }
                screen<B> { composed.mark("B") }
            }
        }
        composition.waitUntil { composed["TabA"] > 0 }
        tabs.navigate(TabB)
        composition.waitUntil { composed["TabB"] > 0 }
        assertEquals(tabs, root.backAction?.target)

        mode = "covered"
        composition.waitUntil { root.backAction == null }

        mode = "plain"
        composition.frames(2)
        root.navigate(B)
        composition.waitUntil { composed["B"] > 0 }
        assertEquals(root, root.backAction?.target)

        mode = "promoted"
        composition.waitUntil { composed["TabB"] > 1 }
        assertEquals(tabs, root.backAction?.target)
    }

    @Test
    fun `a chain interrupted by a structural change is dropped with its section`() = runTest {
        val composition = TestComposition(this)
        val composed = Composed()
        var signedIn by mutableStateOf(true)
        lateinit var root: StackNavigator<Root>
        composition.setContent {
            root = rememberNavigator<Root>()
            Router(root, renderer = PlainRenderer) {
                if (signedIn) {
                    screen<Tabs> {
                        val tabs = rememberNavigator(NavConfig.Tab())
                        Router(tabs, renderer = PlainRenderer) {
                            screen<TabA> { composed.mark("TabA") }
                            screen<TabB> { composed.mark("TabB") }
                        }
                    }
                } else {
                    screen<SignedOut> { composed.mark("out") }
                }
            }
        }
        composition.waitUntil { composed["TabA"] > 0 }

        // sign out before the nested navigator can consume the segment addressed to the new Tabs entry
        signedIn = false
        composition.waitUntil { composed["out"] > 0 }
        signedIn = true
        composition.waitUntil { composed["TabA"] > 1 }
        root.navigate(Tabs.then(TabB))
        signedIn = false
        composition.waitUntil { composed["out"] > 1 }
        assertTrue(root.shell.runtime.mailbox.isEmpty(), root.shell.runtime.mailbox.keys.toString())

        signedIn = true
        composition.waitUntil { composed["TabA"] > 2 }
        composition.frames(5)
        assertEquals(0, composed["TabB"])
    }

    @Test
    fun `saved state of a released entry is dropped from the registry`() = runTest {
        val composed = Composed()
        lateinit var nav: StackNavigator<Root>
        val registry = SaveableStateRegistry(restoredValues = null, canBeSaved = { true })
        val composition = TestComposition(this)
        composition.setContent {
            CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                nav = rememberNavigator<Root>()
                Router(nav, renderer = PlainRenderer) {
                    screen<A> { composed.mark("A") }
                    screen<B> {
                        var n by rememberSaveable { mutableStateOf(0) }
                        SideEffect { if (n == 0) n = 7 }
                        composed.mark("B")
                    }
                }
            }
        }
        composition.waitUntil { composed["A"] > 0 }
        nav.navigate(B)
        composition.waitUntil { composed["B"] > 0 }
        composition.frames(2)
        assertTrue(registry.performSave().toString().contains("Root/2"))

        nav.back()
        composition.waitUntil { composed["A"] > 1 }
        assertFalse(registry.performSave().toString().contains("Root/2"))
    }

    @Test
    fun `destination content sees the captures of the current composition`() = runTest {
        val composition = TestComposition(this)
        val composed = Composed()
        var label by mutableStateOf("one")
        composition.setContent { Graph(label, composed) }
        composition.waitUntil { composed["A one"] > 0 }

        label = "two"
        composition.waitUntil { composed["A two"] > 0 }
    }

    @Test
    fun `re-selecting a tab restores the same entry objects`() = runTest {
        val composition = TestComposition(this)
        val composed = Composed()
        val events = RecordingEvents()
        lateinit var tabs: Navigator<Tabs>
        lateinit var deep: StackNavigator<TabB>
        composition.setContent {
            val root = rememberNavigator<Root>(events = events)
            Router(root, renderer = PlainRenderer) {
                screen<Tabs> {
                    tabs = rememberNavigator(NavConfig.Tab())
                    Router(tabs, renderer = PlainRenderer) {
                        screen<TabA> { composed.mark("TabA") }
                        screen<TabB> {
                            deep = rememberNavigator()
                            Router(deep, renderer = PlainRenderer) {
                                screen<Deep> { composed.mark("Deep") }
                                screen<Deeper> { composed.mark("Deeper") }
                            }
                        }
                    }
                }
            }
        }
        composition.waitUntil { composed["TabA"] > 0 }
        tabs.navigate(TabB)
        composition.waitUntil { composed["Deep"] > 0 }
        deep.navigate(Deeper("x"))
        composition.waitUntil { composed["Deeper"] > 0 }
        val before = deep.entries
        val created = events.of("entry ").size

        tabs.navigate(TabA)
        composition.waitUntil { composed["TabA"] > 1 }
        tabs.navigate(TabB)
        composition.waitUntil { composed["Deeper"] > 1 }
        assertEquals(before.map { it.id }, deep.entries.map { it.id })
        before.zip(deep.entries).forEach { (was, now) -> assertSame(was, now) }
        assertEquals(created, events.of("entry ").size)
    }

    @Test
    fun `a chain continues through a tab the policy reuses instead of creating`() = runTest {
        val composition = TestComposition(this)
        val composed = Composed()
        lateinit var tabs: Navigator<Tabs>
        lateinit var deep: StackNavigator<TabB>
        composition.setContent {
            val root = rememberNavigator<Root>()
            Router(root, renderer = PlainRenderer) {
                screen<Tabs> {
                    tabs = rememberNavigator(NavConfig.Tab())
                    Router(tabs, renderer = PlainRenderer) {
                        screen<TabA> { composed.mark("TabA") }
                        screen<TabB> {
                            deep = rememberNavigator()
                            Router(deep, renderer = PlainRenderer) {
                                screen<Deep> { composed.mark("Deep") }
                                screen<Deeper> { composed.mark("Deeper ${it.screen.id}") }
                            }
                        }
                    }
                }
            }
        }
        composition.waitUntil { composed["TabA"] > 0 }
        tabs.navigate(TabB)
        composition.waitUntil { composed["Deep"] > 0 }
        tabs.navigate(TabA)
        composition.waitUntil { composed["TabA"] > 1 }

        tabs.navigate(TabB.then(Deeper("y")))
        composition.waitUntil { composed["Deeper y"] > 0 }
        assertEquals(listOf(TabA, TabB), tabs.entries.map { it.screen })
        assertEquals(listOf(Deep, Deeper("y")), deep.entries.map { it.screen })
    }

    @Test
    fun `chains posted to the same unbuilt navigator are delivered in order`() = runTest {
        val composition = TestComposition(this)
        val composed = Composed()
        lateinit var tabs: Navigator<Tabs>
        lateinit var deep: StackNavigator<TabB>
        composition.setContent {
            val root = rememberNavigator<Root>()
            Router(root, renderer = PlainRenderer) {
                screen<Tabs> {
                    tabs = rememberNavigator(NavConfig.Tab())
                    Router(tabs, renderer = PlainRenderer) {
                        screen<TabA> { composed.mark("TabA") }
                        screen<TabB> {
                            deep = rememberNavigator()
                            Router(deep, renderer = PlainRenderer) {
                                screen<Deep> { composed.mark("Deep") }
                                screen<Deeper> { composed.mark("Deeper ${it.screen.id}") }
                            }
                        }
                    }
                }
            }
        }
        composition.waitUntil { composed["TabA"] > 0 }
        tabs.navigate(TabB.then(Deeper("p")))
        tabs.navigate(TabB.then(Deeper("q")))
        composition.waitUntil { composed["Deeper q"] > 0 }
        assertEquals(listOf(Deep, Deeper("p"), Deeper("q")), deep.entries.map { it.screen })
    }

    @Test
    fun `leaving the composition ends every entry`() = runTest {
        val composition = TestComposition(this)
        val composed = Composed()
        val events = RecordingEvents()
        lateinit var root: StackNavigator<Root>
        composition.setContent {
            root = rememberNavigator<Root>(events = events)
            Router(root, renderer = PlainRenderer) {
                screen<A> { composed.mark("A") }
                screen<Tabs> {
                    val tabs = rememberNavigator(NavConfig.Tab())
                    Router(tabs, renderer = PlainRenderer) {
                        screen<TabA> { composed.mark("TabA") }
                    }
                }
            }
        }
        composition.waitUntil { composed["A"] > 0 }
        root.navigate(Tabs)
        composition.waitUntil { composed["TabA"] > 0 }

        composition.dispose()
        val retired = events.retired()
        assertEquals(setOf("Root/1", "Root/2", "Root/2/1"), retired.toSet())
        assertTrue(retired.indexOf("Root/2/1") < retired.indexOf("Root/2"), retired.toString())
    }

    @Test
    fun `a navigator replaced at the same position gets its own graph`() = runTest {
        val composition = TestComposition(this)
        val composed = Composed()
        val diagnostics = mutableListOf<String>()
        var user by mutableStateOf("u1")
        lateinit var nav: Navigator<Root>
        composition.setContent {
            nav = rememberNavigator<Root>(NavConfig.Tab(), key = user)
            nav.shell.runtime.registry.diagnostics = { diagnostics += it }
            Router(nav, renderer = PlainRenderer) {
                screen<A> { composed.mark("A $user") }
                screen<B> { composed.mark("B $user") }
            }
        }
        composition.waitUntil { composed["A u1"] > 0 }
        nav.navigate(B)
        composition.waitUntil { composed["B u1"] > 0 }

        user = "u2"
        composition.waitUntil { composed["A u2"] > 0 }
        assertEquals(listOf(A), nav.entries.map { it.screen })
        nav.navigate(C(1))
        composition.frames(2)
        assertTrue(diagnostics.any { "not registered in the live graph" in it }, diagnostics.toString())
        assertEquals(listOf(A), nav.entries.map { it.screen })
    }

    @Test
    fun `a tab registered by supertype is one entry however it is navigated to`() = runTest {
        val composition = TestComposition(this)
        val composed = Composed()
        lateinit var nav: Navigator<Root>
        composition.setContent {
            nav = rememberNavigator<Root>(NavConfig.Tab())
            Router(nav, renderer = PlainRenderer) {
                screen<A> { composed.mark("A") }
                screen<Overlay> { composed.mark("overlay ${it.screen::class.simpleName}") }
            }
        }
        composition.waitUntil { composed["A"] > 0 }
        nav.navigate(Overlay1)
        composition.waitUntil { composed["overlay Overlay1"] > 0 }
        val first = nav.selected!!
        nav.navigate(Overlay2)
        composition.waitUntil { composed["overlay Overlay2"] > 0 }
        assertEquals(listOf(A, Overlay2), nav.entries.map { it.screen })
        assertNotEquals(first.id, nav.selected!!.id)

        nav.navigate(Overlay2)
        composition.frames(2)
        assertEquals(listOf(A, Overlay2), nav.entries.map { it.screen })
        nav.back()
        composition.waitUntil { composed["A"] > 1 }
        assertEquals(A, nav.selected?.screen)
    }

    @Test
    fun `a narrowed scope rejects a selection outside it and drops transitions that leave it`() = runTest {
        val composition = TestComposition(this)
        lateinit var nav: StackNavigator<Root>
        var narrowed: BackTransition? = BackTransition(ownerEntry("x/1", A), ownerEntry("x/2", B), 0f, BackEdge.None)
        var kept: BackTransition? = null
        composition.setContent {
            nav = rememberNavigator<Root>()
            Router(nav, renderer = {
                if (entries.size == 3) {
                    withEntries(listOf(entries[2])) { narrowed = transition }
                    withEntries(entries.take(2)) { kept = transition }
                }
                inlineSelected?.let { render(it) }
            }) {
                screen<A> {}
                screen<B> {}
                screen<C> {}
            }
        }
        nav.navigate(B)
        nav.navigate(C(1))
        composition.frames(2)
        nav.shell.transition = BackTransition(nav.entries[1], nav.entries[0], 0.5f, BackEdge.Left)
        composition.waitUntil { kept != null }
        assertNull(narrowed)
        assertEquals(nav.entries[1], kept?.outgoing)
        nav.shell.transition = null

        val second = TestComposition(this)
        second.setContent {
            val other = rememberNavigator<Root>(key = "other")
            Router(other, renderer = { if (entries.size == 2) withEntries(listOf(entries[0]), selected = entries[1]) {} }) {
                screen<A> {}
                screen<B> {}
            }
            LaunchedEffect(Unit) { other.navigate(B) }
        }
        val error = assertFails { second.waitUntil(frames = 5) { false } }
        assertTrue("not among the narrowed entries" in error.message.orEmpty(), error.toString())
    }

    @Test
    fun `navigation state survives a save and restore of the composition`() = runTest {
        val composed = Composed()
        lateinit var nav: StackNavigator<Root>
        val content: @Composable () -> Unit = {
            nav = rememberNavigator<Root>()
            Router(nav, renderer = PlainRenderer) {
                screen<A> { composed.mark("A") }
                screen<C> { composed.mark("C ${it.screen.n}") }
            }
        }

        // the same composable both times, as an app would re-run it after recreation
        var registry = SaveableStateRegistry(restoredValues = null, canBeSaved = { true })
        val app: @Composable () -> Unit = { CompositionLocalProvider(LocalSaveableStateRegistry provides registry) { content() } }

        val first = TestComposition(this)
        first.setContent(app)
        first.waitUntil { composed["A"] > 0 }
        nav.navigate(C(3))
        nav.navigate(C(4))
        first.waitUntil { composed["C 4"] > 0 }
        val saved = registry.performSave()
        first.dispose()

        registry = SaveableStateRegistry(restoredValues = saved, canBeSaved = { true })
        val second = TestComposition(this)
        second.setContent(app)
        second.waitUntil { composed["C 4"] > 1 }
        assertEquals(listOf("Root/1", "Root/2", "Root/3"), nav.entries.map { it.id.toString() })
        assertEquals(C(4), nav.selected?.screen)
        assertNotNull(nav.backAction)
    }

    @Test
    fun `a restored route rejected on first composition drops its saved payload`() = runTest {
        val composed = Composed()
        lateinit var nav: StackNavigator<Root>
        var declareB by mutableStateOf(true)
        val content: @Composable () -> Unit = {
            nav = rememberNavigator<Root>()
            Router(nav, renderer = PlainRenderer) {
                screen<A> { composed.mark("A") }
                if (declareB) screen<B> {
                    rememberSaveable { "sentinel-payload" }
                    composed.mark("B")
                }
            }
        }

        var registry = SaveableStateRegistry(restoredValues = null, canBeSaved = { true })
        val app: @Composable () -> Unit = { CompositionLocalProvider(LocalSaveableStateRegistry provides registry) { content() } }

        val first = TestComposition(this)
        first.setContent(app)
        first.waitUntil { composed["A"] > 0 }
        nav.navigate(B)
        first.waitUntil { composed["B"] > 0 }
        first.frames(2)
        val saved = registry.performSave()
        assertTrue(saved.toString().contains("sentinel-payload"))
        first.dispose()

        // the app comes back after process death without the route: the restored entry is rejected on the
        // Router's first composition, before the release listener has been installed
        declareB = false
        val marked = composed["A"]
        registry = SaveableStateRegistry(restoredValues = saved, canBeSaved = { true })
        val second = TestComposition(this)
        second.setContent(app)
        second.waitUntil { composed["A"] > marked }
        assertEquals(listOf("Root/1"), nav.entries.map { it.id.toString() })
        assertFalse(registry.performSave().toString().contains("sentinel-payload"))
    }
}

@Composable
private fun Graph(label: String, composed: Composed) {
    val nav = rememberNavigator<Root>()
    Router(nav, renderer = PlainRenderer) {
        screen<A> { composed.mark("A $label") }
    }
}

internal class Composed {
    val counts = mutableMapOf<String, Int>()
    @Composable
    fun mark(name: String) = SideEffect { counts[name] = (counts[name] ?: 0) + 1 }
    operator fun get(name: String) = counts[name] ?: 0
}
