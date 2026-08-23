package ankers.compose.router

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import ankers.compose.router.events.HostState
import ankers.compose.router.host.JvmOwnerPlatform
import ankers.compose.router.host.OwnerEntry
import ankers.compose.router.host.screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OwnerEntryTest {
    class PlainVm : ViewModel() {
        var cleared = false
        override fun onCleared() { cleared = true }
    }

    class HandleVm(val handle: SavedStateHandle) : ViewModel()

    private fun entry() = OwnerEntry(id("root/1"), C(7), ViewModelStore(), JvmOwnerPlatform).also { it.restoreState(null) }

    @Test
    fun `lifecycle is the lower of the host cap and the parent cap`() {
        val entry = entry()
        assertEquals(Lifecycle.State.CREATED, entry.lifecycle.currentState)
        entry.host(HostState.Active)
        assertEquals(Lifecycle.State.RESUMED, entry.lifecycle.currentState)
        entry.host(HostState.Inactive)
        assertEquals(Lifecycle.State.STARTED, entry.lifecycle.currentState)
        entry.host(HostState.Covered)
        assertEquals(Lifecycle.State.CREATED, entry.lifecycle.currentState)
        entry.host(HostState.Active)
        entry.parent(Lifecycle.State.STARTED)
        assertEquals(Lifecycle.State.STARTED, entry.lifecycle.currentState)
        entry.parent(Lifecycle.State.RESUMED)
        assertEquals(Lifecycle.State.RESUMED, entry.lifecycle.currentState)
        entry.unhosted()
        assertEquals(Lifecycle.State.CREATED, entry.lifecycle.currentState)
    }

    @Test
    fun `release destroys the lifecycle and clears the view model store`() {
        val entry = entry()
        val vm = ViewModelProvider.create(entry)[PlainVm::class]
        entry.host(HostState.Active)
        entry.release()
        assertTrue(vm.cleared)
        assertEquals(Lifecycle.State.DESTROYED, entry.lifecycle.currentState)
        entry.host(HostState.Active)
        assertEquals(Lifecycle.State.DESTROYED, entry.lifecycle.currentState)
    }

    @Test
    fun `the default factory supports SavedStateHandle constructors and the extras carry the screen`() {
        val entry = entry()
        val vm = ViewModelProvider.create(entry)[HandleVm::class]
        assertFalse(vm.handle.contains("anything"))
        assertEquals(C(7), entry.defaultViewModelCreationExtras.screen<C>())
    }

    @Test
    fun `saved state survives a save and restore round trip`() {
        val entry = entry()
        val vm = ViewModelProvider.create(entry)[HandleVm::class]
        vm.handle["count"] = 3
        val saved = entry.saveState()

        val restored = OwnerEntry(id("root/1"), C(7), ViewModelStore(), JvmOwnerPlatform).also { it.restoreState(saved) }
        val again = ViewModelProvider.create(restored)[HandleVm::class]
        assertEquals(3, again.handle.get<Int>("count"))
    }
}
