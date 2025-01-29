package ankers.compose.router

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.compose.viewModel
import ankers.compose.router.navigator.NavConfig
import ankers.compose.router.navigator.Navigator
import ankers.compose.router.navigator.NavigatorProvider
import ankers.compose.router.navigator.rememberNavigatorImpl
import kotlin.random.Random
import kotlin.reflect.KClass

class ViewModelRegistry : ViewModel() {
    val storesById: MutableMap<Long, ViewModelStore> = mutableMapOf()
}

@Composable
fun <T : Screen> rememberNavigator(
    type: KClass<T>,
    navConfig: NavConfig,
): Navigator<T> {
    val locked = remember { mutableStateOf(false) }
    val navigationsByRoute = remember { mutableStateMapOf<Route, MutableList<Screen>>() }
    val vmRegistry = viewModel<ViewModelRegistry>()

    return rememberNavigatorImpl(
        type = type,
        navConfig = navConfig,
        navigationsByRoute = navigationsByRoute,
        locked = locked,
        coreNavigatorProvider = NavigatorProvider(
            navigationsByRoute = navigationsByRoute,
            entryCreator = { screen ->
                val id = Random.nextLong(System.currentTimeMillis())
                val vmStore = ViewModelStore()
                vmRegistry.storesById[id] = vmStore

                AndroidNavEntry(
                    screen = screen,
                    id = id,
                    viewModelStore = vmStore
                )
            },
            entryRemoveListener = { entryId ->
                vmRegistry.storesById.getOrDefault(entryId, null)?.clear()
                vmRegistry.storesById.remove(entryId)
            }
        ),
        backHandlerProvider = { backHandler, routes ->
            BackHandler(backHandler.enabled) {
                backHandler.onBack(routes)
            }
        }
    )
}

@Composable
inline fun <reified T : NavigationRoot> rememberNavigator(
    navConfig: NavConfig = NavConfig.Stack,
) = rememberNavigator(
    type = T::class,
    navConfig = navConfig,
)