package ankers.compose.router.host

import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.enableSavedStateHandles
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.savedstate.SavedState
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.savedState
import ankers.compose.router.Screen
import ankers.compose.router.entry.EntryId
import ankers.compose.router.entry.NavEntry
import ankers.compose.router.events.HostState

/** [CreationExtras] key under which every ViewModel created for an entry can read its screen. */
object ScreenKey : CreationExtras.Key<Screen>

/** The screen whose entry created this ViewModel. */
@Suppress("UNCHECKED_CAST")
fun <T : Screen> CreationExtras.screen(): T = checkNotNull(this[ScreenKey]) { "no screen in these extras" } as T

/** Platform-specific pieces of an [OwnerEntry]. */
interface OwnerPlatform {
    fun defaultFactory(entry: OwnerEntry<*>): ViewModelProvider.Factory

    fun extras(entry: OwnerEntry<*>, extras: MutableCreationExtras) {}
}

/**
 * The entry every platform binding creates: a [ViewModelStoreOwner], [LifecycleOwner], and [SavedStateRegistryOwner]
 * in the shape of Navigation 2's `NavBackStackEntry`, so `viewModel()`, `SavedStateHandle`, lifecycle-aware
 * effects, and on Android `hiltViewModel()` behave as they do there.
 *
 * Lifecycle is the lower of the host's cap ([HostState]) and the parent entry's state: `Active` → RESUMED,
 * `Inactive` → STARTED, `Covered`/`Retiring`/parked → CREATED, released → DESTROYED.
 */
class OwnerEntry<T : Screen>(
    override val id: EntryId,
    override val screen: T,
    override val viewModelStore: ViewModelStore,
    private val platform: OwnerPlatform,
) : NavEntry<T>, LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner,
    HasDefaultViewModelProviderFactory {

    // composition is single-threaded; the main-thread check only gets in the way of jvm tests
    private val lifecycleRegistry = LifecycleRegistry.createUnsafe(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    private var hostCap: Lifecycle.State = Lifecycle.State.CREATED
    private var parentCap: Lifecycle.State = Lifecycle.State.RESUMED
    private var released = false

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    init {
        savedStateController.performAttach()
    }

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory by lazy { platform.defaultFactory(this) }
    private val defaultArgs: SavedState by lazy { screenArgs(screen) }

    override val defaultViewModelCreationExtras: CreationExtras
        get() = MutableCreationExtras().apply {
            this[SAVED_STATE_REGISTRY_OWNER_KEY] = this@OwnerEntry
            this[VIEW_MODEL_STORE_OWNER_KEY] = this@OwnerEntry
            this[ScreenKey] = screen
            this[DEFAULT_ARGS_KEY] = defaultArgs
            platform.extras(this@OwnerEntry, this)
        }

    /** Restores the saved-state registry from [saved] (null for a fresh entry) and starts the lifecycle. */
    fun restoreState(saved: Any?) {
        savedStateController.performRestore(saved as? SavedState)
        enableSavedStateHandles()
        apply()
    }

    fun saveState(): Any = savedState().also { savedStateController.performSave(it) }

    fun host(state: HostState) {
        hostCap = when (state) {
            HostState.Active -> Lifecycle.State.RESUMED
            HostState.Inactive -> Lifecycle.State.STARTED
            HostState.Covered, HostState.Retiring -> Lifecycle.State.CREATED
        }
        apply()
    }

    fun unhosted() {
        hostCap = Lifecycle.State.CREATED
        apply()
    }

    fun parent(state: Lifecycle.State) {
        parentCap = state
        apply()
    }

    fun release() {
        if (released) return
        released = true
        if (lifecycleRegistry.currentState == Lifecycle.State.INITIALIZED) {
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }

    private fun apply() {
        if (released) return
        val effective = if (hostCap.isAtLeast(parentCap)) parentCap else hostCap
        lifecycleRegistry.currentState = if (effective.isAtLeast(Lifecycle.State.CREATED)) effective else Lifecycle.State.CREATED
    }

    override fun equals(other: Any?): Boolean = other is NavEntry<*> && other.id == id
    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = "$id=${screen::class.simpleName}"
}
