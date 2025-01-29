package ankers.compose.router

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import ankers.compose.router.navigator.Navigator
import java.io.Serializable
import kotlin.random.Random
import kotlin.reflect.full.primaryConstructor

@Composable
fun <T : Screen> Router(
    navigator: Navigator<T>,
    key: Any = Unit,
    decoration: ScreenDecoration = Crossfade,
    config: RouterScope<T>.() -> Unit,
) {
    val stateHolder = rememberSaveableStateHolder()

    RouterInternal(
        autoConstruct = { route, error ->
            route.primaryConstructor?.call()
                ?: route.objectInstance
                ?: error(error)
        },
        navigator = navigator,
        key = key,
        decoration = { selected, content ->
            decoration(selected) { latestSelected ->
                CompositionLocalProvider(
                    LocalViewModelStoreOwner provides latestSelected as AndroidNavEntry
                ) {
                    stateHolder.SaveableStateProvider(latestSelected.id) {
                        content(latestSelected)
                    }
                }
            }
        },
        config = config
    )
}

data class AndroidNavEntry<T : Screen>(
    override val id: Long = Random.nextLong(System.currentTimeMillis()),
    override val screen: T,
    @Transient
    override val viewModelStore: ViewModelStore,
) : NavEntry<T>, Serializable, ViewModelStoreOwner {
    override fun equals(other: Any?): Boolean = other is NavEntry<*> && other.id == id
    override fun hashCode(): Int = id.hashCode()
}