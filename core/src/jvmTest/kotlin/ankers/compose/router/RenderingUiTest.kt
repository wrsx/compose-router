package ankers.compose.router

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.compose.runtime.CompositionLocalProvider
import ankers.compose.router.back.LocalBackScope
import ankers.compose.router.back.BackEdge
import ankers.compose.router.back.BackTransition
import ankers.compose.router.navigator.StackNavigator
import ankers.compose.router.navigator.rememberNavigator
import ankers.compose.router.render.OverlayHost
import ankers.compose.router.render.PredictiveBackRenderer
import ankers.compose.router.render.RouterRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/** Real layouts, real animations, headless on the desktop target: what the bare harness cannot compose. */
@OptIn(ExperimentalTestApi::class)
class RenderingUiTest {

    @Test
    fun `crossfade keeps the outgoing entry hosted until the animation ends then releases it`() = runComposeUiTest {
        mainClock.autoAdvance = false
        val events = RecordingEvents()
        lateinit var nav: StackNavigator<Root>
        setContent {
            nav = rememberNavigator<Root>(events = events)
            Router(nav) {
                screen<A> { BasicText("A", Modifier.testTag("A")) }
                screen<B> { BasicText("B", Modifier.testTag("B")) }
            }
        }
        onNodeWithTag("A").assertExists()

        nav.navigate(B)
        mainClock.advanceTimeByFrame()
        onNodeWithTag("A").assertExists()
        onNodeWithTag("B").assertExists()
        mainClock.advanceTimeBy(1_000)
        onNodeWithTag("A").assertDoesNotExist()

        nav.back()
        mainClock.advanceTimeByFrame()
        onNodeWithTag("B").assertExists()
        assertEquals(emptyList(), events.retired())
        mainClock.advanceTimeBy(1_000)
        onNodeWithTag("B").assertDoesNotExist()
        assertEquals(listOf("Root/2"), events.retired())
    }

    @Test
    fun `rendering one entry from two places fails descriptively`() {
        val error = assertFails {
            runComposeUiTest {
                setContent {
                    val nav = rememberNavigator<Root>()
                    Router(nav, renderer = { entries.forEach { render(it) }; entries.forEach { render(it) } }) {
                        screen<A> { BasicText("A") }
                    }
                }
                waitForIdle()
            }
        }
        assertTrue("already rendered elsewhere" in (error.message ?: error.cause?.message ?: ""), error.toString())
    }

    @Test
    fun `renderEach keeps state and effects across a reorder`() = runComposeUiTest {
        val order = mutableStateListOf(1, 2, 3)
        val effects = mutableMapOf<Int, Int>()
        val remembered = mutableMapOf<Int, Int>()
        var instances = 0
        lateinit var nav: StackNavigator<Root>
        val renderer: RouterRenderer = {
            val byN = entries.associateBy { (it.screen as C).n }
            Column { renderEach(order.mapNotNull { byN[it] }) { render(it, active = true) } }
        }
        setContent {
            nav = rememberNavigator<Root>()
            Router(nav, renderer = renderer, start = C(1)) {
                screen<C> {
                    val n = it.screen.n
                    remembered[n] = remember { instances++ }
                    DisposableEffect(Unit) {
                        effects[n] = (effects[n] ?: 0) + 1
                        onDispose {}
                    }
                    BasicText("C$n", Modifier.testTag("C$n"))
                }
            }
        }
        nav.navigate(C(2))
        nav.navigate(C(3))
        waitForIdle()
        val before = remembered.toMap()

        order.clear()
        order.addAll(listOf(2, 1, 3))
        waitForIdle()

        onNodeWithTag("C1").assertExists()
        assertEquals(before, remembered.toMap())
        assertEquals(mapOf(1 to 1, 2 to 1, 3 to 1), effects)
    }

    @Test
    fun `a narrowed scope renders the base beneath an overlay without re-rendering the overlay`() = runComposeUiTest {
        val events = RecordingEvents()
        lateinit var nav: StackNavigator<Root>
        val renderer: RouterRenderer = {
            val (overlays, rest) = entries.partition { it.screen == Sheet }
            Box {
                withEntries(rest, active = overlays.isEmpty()) { inlineSelected?.let { render(it) } }
                renderEach(overlays) { Box(Modifier.testTag("sheet")) { render(it) } }
            }
        }
        setContent {
            nav = rememberNavigator<Root>(events = events)
            Router(nav, renderer = renderer) {
                screen<A> { BasicText("A", Modifier.testTag("A")) }
                screen<Sheet> { BasicText("S", Modifier.testTag("S")) }
            }
        }
        nav.navigate(Sheet)
        waitForIdle()
        onNodeWithTag("A").assertExists()
        onNodeWithTag("S").assertExists()
        assertTrue("state Root/1 Inactive" in events.log, events.log.toString())
        assertTrue("state Root/2 Active" in events.log, events.log.toString())
    }

    @Test
    fun `projected entries render inside the OverlayHost and back pops them`() = runComposeUiTest {
        lateinit var nav: StackNavigator<Root>
        setContent {
            OverlayHost(overlay = { projected ->
                projected.forEach { Box(Modifier.testTag("overlay-${it.entry.id}")) { it.content() } }
            }) {
                nav = rememberNavigator<Root>()
                Router(nav) {
                    screen<A> { BasicText("A", Modifier.testTag("A")) }
                    projected<Sheet> { BasicText("S", Modifier.testTag("S")) }
                }
            }
        }
        nav.navigate(Sheet)
        waitForIdle()
        onNodeWithTag("A").assertExists()
        onNodeWithTag("overlay-Root/2").assertExists()
        onNodeWithTag("S").assertExists()
        assertTrue(nav.canGoBack)

        nav.back()
        waitForIdle()
        onNodeWithTag("S").assertDoesNotExist()
        onNodeWithTag("A").assertExists()
    }

    @Test
    fun `a projected entry animates out live and is released once its transition settles`() = runComposeUiTest {
        mainClock.autoAdvance = false
        val events = RecordingEvents()
        lateinit var nav: StackNavigator<Root>
        setContent {
            OverlayHost(overlay = { projected ->
                projected.forEach { item ->
                    key(item.entry.id) {
                        item.transition.AnimatedVisibility(visible = { it }, enter = fadeIn(), exit = fadeOut()) {
                            Box(Modifier.testTag("overlay")) { item.content() }
                        }
                    }
                }
            }) {
                nav = rememberNavigator<Root>(events = events)
                Router(nav) {
                    screen<A> { BasicText("A", Modifier.testTag("A")) }
                    projected<Sheet> { BasicText("S", Modifier.testTag("S")) }
                }
            }
        }
        nav.navigate(Sheet)
        mainClock.advanceTimeBy(1_000)
        onNodeWithTag("S").assertExists()

        nav.back()
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        onNodeWithTag("S").assertExists()
        assertTrue("state Root/2 Retiring" in events.log, events.log.toString())
        assertEquals(emptyList(), events.retired())

        mainClock.advanceTimeBy(1_000)
        onNodeWithTag("S").assertDoesNotExist()
        assertEquals(listOf("Root/2"), events.retired())
    }

    @Test
    fun `a projected entry follows a predictive back gesture and settles on cancel or commit`() = runComposeUiTest {
        mainClock.autoAdvance = false
        var shown = -1f
        lateinit var nav: StackNavigator<Root>
        setContent {
            OverlayHost(overlay = { projected ->
                projected.forEach { item ->
                    key(item.entry.id) {
                        val fraction by item.transition.animateFloat(transitionSpec = { tween(1_000, easing = LinearEasing) }) { if (it) 1f else 0f }
                        shown = fraction
                        Box(Modifier.testTag("overlay")) { item.content() }
                    }
                }
            }) {
                nav = rememberNavigator<Root>()
                Router(nav) {
                    screen<A> { BasicText("A", Modifier.testTag("A")) }
                    projected<Sheet> { BasicText("S", Modifier.testTag("S")) }
                }
            }
        }
        nav.navigate(Sheet)
        mainClock.advanceTimeBy(2_000)
        assertEquals(1f, shown)

        val action = nav.backAction!!
        nav.shell.transition = BackTransition(action.outgoing, action.incoming, 0.5f, BackEdge.Left)
        mainClock.advanceTimeBy(100)
        assertEquals(0.5f, shown, 0.05f)
        onNodeWithTag("S").assertExists()

        nav.shell.transition = null
        mainClock.advanceTimeBy(2_000)
        assertEquals(1f, shown)

        nav.shell.transition = BackTransition(action.outgoing, action.incoming, 0.75f, BackEdge.Left)
        mainClock.advanceTimeBy(100)
        assertEquals(0.25f, shown, 0.05f)
        action.commit()
        nav.shell.transition = null
        mainClock.advanceTimeBy(2_000)
        onNodeWithTag("S").assertDoesNotExist()
        onNodeWithTag("A").assertExists()
    }

    @Test
    fun `a projected sheet owned by a nested navigator escapes to the root and is removed with its section`() = runComposeUiTest {
        val events = RecordingEvents()
        var signedIn by mutableStateOf(true)
        lateinit var deep: StackNavigator<Tabs>
        setContent {
            OverlayHost(overlay = { projected -> projected.forEach { Box(Modifier.testTag("overlay")) { it.content() } } }) {
                val root = rememberNavigator<Root>(events = events)
                Router(root) {
                    if (signedIn) {
                        screen<Tabs> {
                            deep = rememberNavigator()
                            Router(deep) {
                                screen<TabA> { BasicText("deep", Modifier.testTag("deep")) }
                                projected<TabC> { BasicText("local", Modifier.testTag("local")) }
                            }
                        }
                    } else {
                        screen<SignedOut> { BasicText("out", Modifier.testTag("out")) }
                    }
                }
            }
        }
        waitForIdle()
        deep.navigate(TabC)
        waitForIdle()
        onNodeWithTag("local").assertExists()
        onNodeWithTag("deep").assertExists()

        signedIn = false
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        onNodeWithTag("local").assertDoesNotExist()
        onNodeWithTag("out").assertExists()
        assertEquals(setOf("Root/1", "Root/1/1", "Root/1/2"), events.retired().toSet())
    }

    @Test
    fun `the predictive back renderer previews the incoming entry and commit pops`() = runComposeUiTest {
        mainClock.autoAdvance = false
        lateinit var nav: StackNavigator<Root>
        setContent {
            nav = rememberNavigator<Root>()
            Router(nav, renderer = PredictiveBackRenderer) {
                screen<A> { BasicText("A", Modifier.testTag("A")) }
                screen<B> { BasicText("B", Modifier.testTag("B")) }
            }
        }
        nav.navigate(B)
        mainClock.advanceTimeBy(2_000)
        onNodeWithTag("A").assertDoesNotExist()

        val action = nav.backAction!!
        nav.shell.transition = BackTransition(action.outgoing, action.incoming, 0.5f, BackEdge.Left)
        mainClock.advanceTimeBy(100)
        onNodeWithTag("A").assertExists()
        onNodeWithTag("B").assertExists()

        action.commit()
        nav.shell.transition = null
        mainClock.advanceTimeBy(2_000)
        onNodeWithTag("B").assertDoesNotExist()
        onNodeWithTag("A").assertExists()
    }

    @Test
    fun `a structurally removed screen crossfades out without throwing`() = runComposeUiTest {
        mainClock.autoAdvance = false
        var signedIn by mutableStateOf(true)
        setContent {
            val nav = rememberNavigator<Root>()
            Router(nav) {
                if (signedIn) screen<A> { BasicText("A", Modifier.testTag("A")) } else screen<SignedOut> { BasicText("out", Modifier.testTag("out")) }
            }
        }
        onNodeWithTag("A").assertExists()
        signedIn = false
        // one frame to re-synchronize the graph, one to start rendering the new start destination
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        onNodeWithTag("A").assertExists()
        onNodeWithTag("out").assertExists()
        mainClock.advanceTimeBy(1_000)
        onNodeWithTag("A").assertDoesNotExist()
    }

    @Test
    fun `a screen registered by supertype keeps its content while it exits after a structural change`() = runComposeUiTest {
        mainClock.autoAdvance = false
        var showOverlays by mutableStateOf(true)
        lateinit var nav: StackNavigator<Root>
        setContent {
            nav = rememberNavigator<Root>()
            Router(nav) {
                screen<A> { BasicText("A", Modifier.testTag("A")) }
                if (showOverlays) screen<Overlay> { BasicText("overlay", Modifier.testTag("overlay")) }
            }
        }
        nav.navigate(Overlay1)
        mainClock.advanceTimeBy(1_000)
        onNodeWithTag("overlay").assertExists()

        showOverlays = false
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        onNodeWithTag("overlay").assertExists()
        mainClock.advanceTimeBy(1_000)
        onNodeWithTag("overlay").assertDoesNotExist()
        onNodeWithTag("A").assertExists()
    }

    @Test
    fun `a projected entry is capped by its owning entry, not by where it is composed`() = runComposeUiTest {
        lateinit var deep: StackNavigator<Tabs>
        lateinit var root: StackNavigator<Root>
        val renderer: RouterRenderer = {
            renderEach(entries) { render(it, active = false) }
            projectedEntries.forEach { projectToRoot(it) }
        }
        setContent {
            OverlayHost(overlay = { projected -> projected.forEach { Box(Modifier.testTag("overlay")) { it.content() } } }) {
                root = rememberNavigator<Root>()
                Router(root, renderer = renderer) {
                    screen<Tabs> {
                        deep = rememberNavigator()
                        Router(deep) {
                            screen<TabA> { BasicText("deep") }
                            projected<TabC> { BasicText("sheet", Modifier.testTag("sheet")) }
                        }
                    }
                }
            }
        }
        waitForIdle()
        deep.navigate(TabC)
        waitForIdle()
        onNodeWithTag("sheet").assertExists()
        val sheet = deep.selected as LifecycleOwner
        val tabs = root.selected as LifecycleOwner
        assertEquals(Lifecycle.State.STARTED, tabs.lifecycle.currentState)
        assertEquals(Lifecycle.State.STARTED, sheet.lifecycle.currentState)
    }

    @Test
    fun `a navigator under a retiring section rejects navigation during the exit and nothing is orphaned`() = runComposeUiTest {
        mainClock.autoAdvance = false
        val events = RecordingEvents()
        val diagnostics = mutableListOf<String>()
        var signedIn by mutableStateOf(true)
        lateinit var deep: StackNavigator<Tabs>
        setContent {
            val root = rememberNavigator<Root>(events = events)
            root.shell.runtime.registry.diagnostics = { diagnostics += it }
            Router(root) {
                if (signedIn) {
                    screen<Tabs> {
                        deep = rememberNavigator()
                        Router(deep) {
                            screen<TabA> { BasicText("deep", Modifier.testTag("deep")) }
                            screen<TabB> { BasicText("deeper", Modifier.testTag("deeper")) }
                        }
                    }
                } else {
                    screen<SignedOut> { BasicText("out", Modifier.testTag("out")) }
                }
            }
        }
        mainClock.advanceTimeBy(1_000)
        onNodeWithTag("deep").assertExists()

        signedIn = false
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        onNodeWithTag("deep").assertExists()
        deep.navigate(TabB)
        mainClock.advanceTimeByFrame()
        assertTrue(diagnostics.any { "retiring" in it }, diagnostics.toString())
        assertEquals(listOf("Root/1/1"), deep.entries.map { it.id.toString() })

        mainClock.advanceTimeBy(2_000)
        onNodeWithTag("deep").assertDoesNotExist()
        val created = events.of("entry ").map { it.split(" ")[1] }.toSet() - "Root/2"
        assertEquals(created, events.retired().toSet())
    }

    @Test
    fun `entries beneath a retiring section retire with it while the exit runs`() = runComposeUiTest {
        mainClock.autoAdvance = false
        val events = RecordingEvents()
        var signedIn by mutableStateOf(true)
        lateinit var deep: StackNavigator<Tabs>
        setContent {
            val root = rememberNavigator<Root>(events = events)
            Router(root) {
                if (signedIn) {
                    screen<Tabs> {
                        deep = rememberNavigator()
                        Router(deep) { screen<TabA> { BasicText("deep", Modifier.testTag("deep")) } }
                    }
                } else {
                    screen<SignedOut> { BasicText("out", Modifier.testTag("out")) }
                }
            }
        }
        mainClock.advanceTimeBy(1_000)
        val nested = deep.selected as LifecycleOwner
        assertEquals(Lifecycle.State.RESUMED, nested.lifecycle.currentState)

        signedIn = false
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        onNodeWithTag("deep").assertExists()
        assertTrue("state Root/1/1 Retiring" in events.log, events.log.toString())
        assertEquals(Lifecycle.State.CREATED, nested.lifecycle.currentState)
    }

    @Test
    fun `the pane beneath a selected projection is covered`() = runComposeUiTest {
        val events = RecordingEvents()
        lateinit var nav: StackNavigator<Root>
        setContent {
            OverlayHost(overlay = { projected -> projected.forEach { Box(Modifier.testTag("overlay")) { it.content() } } }) {
                nav = rememberNavigator<Root>(events = events)
                Router(nav) {
                    screen<A> { BasicText("A", Modifier.testTag("A")) }
                    projected<Sheet> { BasicText("S", Modifier.testTag("S")) }
                }
            }
        }
        waitForIdle()
        val a = nav.selected as LifecycleOwner
        assertEquals(Lifecycle.State.RESUMED, a.lifecycle.currentState)

        nav.navigate(Sheet)
        waitForIdle()
        assertEquals(Lifecycle.State.CREATED, a.lifecycle.currentState)
        assertTrue("state Root/1 Covered" in events.log, events.log.toString())
        assertEquals("Root/2", nav.backAction?.outgoing?.id.toString())

        nav.back()
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        assertEquals(Lifecycle.State.RESUMED, a.lifecycle.currentState)
    }

    @Test
    fun `projected content keeps the back scope of the router that projected it`() = runComposeUiTest {
        var seen: Any? = "unset"
        lateinit var nav: StackNavigator<Root>
        setContent {
            OverlayHost(overlay = { projected -> projected.forEach { Box { it.content() } } }) {
                CompositionLocalProvider(LocalBackScope provides "activity") {
                    nav = rememberNavigator<Root>()
                    Router(nav) {
                        screen<A> { BasicText("A") }
                        projected<Sheet> { seen = LocalBackScope.current }
                    }
                }
            }
        }
        nav.navigate(Sheet)
        waitForIdle()
        assertEquals("activity", seen)
    }

    @Test
    fun `a slot based list detail renderer keeps both panes across layout changes`() = runComposeUiTest {
        var wide by mutableStateOf(true)
        var detailInstances = 0
        lateinit var nav: StackNavigator<Root>
        val renderer: RouterRenderer = {
            val list = entries.firstOrNull()
            val detail = entries.getOrNull(1)
            val isWide = wide
            Row(Modifier.fillMaxSize()) {
                AnimatedVisibility(visible = isWide || detail == null, modifier = Modifier.weight(1f)) {
                    list?.let { render(it, active = isWide || detail == null) }
                }
                AnimatedContent(targetState = detail, modifier = Modifier.weight(1f)) { d -> d?.let { render(it) } }
            }
        }
        setContent {
            nav = rememberNavigator<Root>()
            Router(nav, renderer = renderer) {
                screen<A> { BasicText("list", Modifier.testTag("list")) }
                screen<C> {
                    remember { detailInstances++ }
                    BasicText("detail", Modifier.testTag("detail"))
                }
            }
        }
        nav.navigate(C(1))
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        onNodeWithTag("list").assertExists()
        onNodeWithTag("detail").assertExists()

        wide = false
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        onNodeWithTag("list").assertDoesNotExist()
        onNodeWithTag("detail").assertExists()

        wide = true
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        onNodeWithTag("list").assertExists()
        assertEquals(1, detailInstances)
    }
}
