package ankers.compose.router

import androidx.compose.runtime.Composable
import ankers.compose.router.navigator.NavConfig
import ankers.compose.router.navigator.Navigator
import kotlin.reflect.KClass

@DslMarker
annotation class NavigationDslMarker

@NavigationDslMarker
abstract class RouterScope<T : Screen> {
    abstract fun <C : ChildScreenOf<T>> screen(
        route: KClass<C>,
        config: NavConfig,
        content: @Composable ScreenScope<C>.(NavEntry<C>) -> Unit,
    )

    inline fun <reified C : ChildScreenOf<T>> screen(
        config: NavConfig = NavConfig.Stack,
        noinline content: @Composable ScreenScope<C>.(NavEntry<C>) -> Unit,
    ) = screen(
        route = C::class,
        config = config,
    ) { screenEntry -> content(screenEntry) }
}

interface ScreenScope<T : Screen> {
    @Composable
    fun rememberNavigator(
        type: KClass<T>,
        navConfig: NavConfig
    ): Navigator<T>
}

@Composable
inline fun <reified T : Screen> ScreenScope<T>.rememberNavigator(navConfig: NavConfig = NavConfig.Stack): Navigator<T> {
    return rememberNavigator(T::class, navConfig)
}