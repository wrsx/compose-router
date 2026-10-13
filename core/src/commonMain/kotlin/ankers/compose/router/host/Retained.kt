package ankers.compose.router.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ankers.compose.router.entry.NavEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlin.reflect.KClass

/**
 * Returns the [T] retained by the entry [owner], creating it with [create] the first time it is asked for.
 *
 * A retained value is a plain object that lives exactly as long as its entry. It survives recomposition, the entry
 * being parked (a background tab, the screen under a push) and, on Android, configuration changes. When the entry is
 * released, the value's [RetainedScope.coroutineScope] is cancelled and, if it is [AutoCloseable], it is closed.
 *
 * An entry retains one value per [key], or per class of [T] when [key] is null, so every call for a type in the same
 * entry returns the same instance: from any composable in the entry's content, or from another entry that names this
 * one as [owner]. Pass [key] to keep several values of one type, or for a local or anonymous class.
 *
 * After process death the restored entry runs [create] again; state that must survive it belongs in
 * [RetainedScope.savedStateHandle].
 */
@Composable
inline fun <reified T : Any> retained(
    key: String? = null,
    owner: NavEntry<*> = currentNavEntry(),
    noinline create: RetainedScope.() -> T,
): T = remember(owner, key) { retainedValue(owner, T::class, key, create) }

/** What [retained] gives the value it creates. */
class RetainedScope internal constructor(
    private val extras: CreationExtras,
    /**
     * Cancelled when the owning entry is released. Dispatches on the main thread where the platform has one, as
     * `viewModelScope` does.
     */
    val coroutineScope: CoroutineScope,
) {
    /**
     * State saved under the value's key: written out for process death on Android, kept in memory elsewhere. It is
     * seeded with the owning entry's screen, which [screen] reads. Created on first use.
     */
    val savedStateHandle: SavedStateHandle by lazy { extras.createSavedStateHandle() }
}

/** The entry whose content is composing; provided by the entry host. */
@PublishedApi
internal val LocalNavEntry = staticCompositionLocalOf<NavEntry<*>?> { null }

@PublishedApi
@Composable
internal fun currentNavEntry(): NavEntry<*> =
    checkNotNull(LocalNavEntry.current) { "retained() needs an entry: call it from an entry's content, or pass owner" }

@PublishedApi
internal fun <T : Any> retainedValue(
    owner: NavEntry<*>,
    type: KClass<T>,
    key: String?,
    create: RetainedScope.() -> T,
): T {
    val store = owner as? ViewModelStoreOwner ?: error("${owner.id} cannot retain values: it has no ViewModel store")
    check((owner as? LifecycleOwner)?.lifecycle?.currentState != Lifecycle.State.DESTROYED) {
        "${owner.id} has been released and can no longer retain values"
    }
    val storeKey = if (key != null) {
        "$RETAINED_KEY:key:$key"
    } else {
        val name = type.stableName
            ?: error("${type.simpleName ?: "a local or anonymous class"} needs a key to be retained")
        "$RETAINED_KEY:type:$name"
    }
    val factory = viewModelFactory { initializer { RetainedHolder(this, create) } }
    val value = ViewModelProvider.create(store, factory)[storeKey, RetainedHolder::class].value
    check(type.isInstance(value)) {
        "${owner.id} retains a ${value::class.simpleName} under '${key ?: type.simpleName}', " +
            "not a ${type.simpleName}: give each type its own key"
    }
    @Suppress("UNCHECKED_CAST")
    return value as T
}

/**
 * The name [retained] keys a class by when it is given no key, unique among the classes an app retains: stable
 * across processes wherever saved state outlives one. Null for local and anonymous classes.
 */
internal expect val KClass<*>.stableName: String?

private const val RETAINED_KEY = "ankers.compose.router.retained"

// the entry's ViewModel store keeps each value through configuration changes and clears it on release
internal class RetainedHolder(extras: CreationExtras, create: RetainedScope.() -> Any) : ViewModel() {
    val value: Any = try {
        RetainedScope(extras, viewModelScope).create()
    } catch (e: Throwable) {
        // a holder that fails to construct is never stored, so nothing would ever clear its scope
        viewModelScope.cancel()
        throw e
    }

    init {
        (value as? AutoCloseable)?.let(::addCloseable)
    }
}
