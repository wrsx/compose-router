package ankers.compose.router

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.ViewModelProvider.Companion.VIEW_MODEL_KEY
import androidx.savedstate.read
import ankers.compose.router.host.SCREEN_SAVED_STATE_KEY
import ankers.compose.router.host.constructStartDestination
import ankers.compose.router.host.decodeScreen
import ankers.compose.router.host.encodeScreen
import ankers.compose.router.host.screen
import ankers.compose.router.host.screenArgs
import ankers.compose.router.navigator.NavConfig
import ankers.compose.router.navigator.NavigatorShell
import ankers.compose.router.navigator.StackNavigator
import ankers.compose.router.entry.EntryId
import ankers.compose.router.events.NavigatorEvents
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@Serializable data class WithDefaults(val n: Int = 1, val name: String = "x") : ChildScreenOf<Root>
@Serializable data class Required(val n: Int) : ChildScreenOf<Root>
object NotSerializable : ChildScreenOf<Root>

class ScreenSerializationTest {

    @Test
    fun `an object screen keeps its identity through encoding`() {
        val restored = decodeScreen(A::class, encodeScreen(A))
        assertSame(A, restored)
    }

    @Test
    fun `a data class screen round trips by value`() {
        assertEquals(Deeper("id-7"), decodeScreen(Deeper::class, encodeScreen(Deeper("id-7"))))
        assertEquals(C(3), decodeScreen(C::class, encodeScreen(C(3))))
    }

    @Test
    fun `start destinations are decoded from an empty state`() {
        assertSame(A, constructStartDestination(A::class))
        assertEquals(WithDefaults(), constructStartDestination(WithDefaults::class))
        assertNull(constructStartDestination(Required::class))
        assertNull(constructStartDestination(NotSerializable::class))
    }

    @Test
    fun `a SavedStateHandle seeded by an entry hands back the screen`() {
        val handle = SavedStateHandle(mapOf(SCREEN_SAVED_STATE_KEY to screenArgs(Deeper("id-9")).read { getSavedState(SCREEN_SAVED_STATE_KEY) }))
        assertEquals(Deeper("id-9"), handle.screen<Deeper>())
    }

    @Test
    fun `createSavedStateHandle on an entry carries the screen as default args`() {
        val entry = ownerEntry("root/1", C(5))
        val extras = MutableCreationExtras(entry.defaultViewModelCreationExtras).apply { this[VIEW_MODEL_KEY] = "vm" }
        val handle = extras.createSavedStateHandle()
        assertEquals(C(5), handle.screen<C>())
    }

    @Test
    fun `navigating to a screen that is not serializable fails at once`() {
        val shell = NavigatorShell(EntryId.root("root"), NavConfig.Stack, testRuntime(), NavigatorEvents.Discard, null, null)
        val nav = StackNavigator<Root>(shell).also { shell.navigator = it }
        nav.navigate(A)
        val error = assertFailsWith<IllegalStateException> { nav.navigate(NotSerializable) }
        assertTrue("NotSerializable is not @Serializable" in error.message!!, error.message)
        assertEquals(listOf("root/1"), nav.entries.map { it.id.toString() })
    }
}
