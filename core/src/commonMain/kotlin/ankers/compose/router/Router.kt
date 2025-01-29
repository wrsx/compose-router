package ankers.compose.router

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ankers.compose.router.navigator.NavConfig
import ankers.compose.router.navigator.Navigator
import kotlin.reflect.KClass

@Composable
internal fun <T : Screen> RouterInternal(
    navigator: Navigator<T>,
    autoConstruct: (route: Route, errorMsg: String) -> Screen,
    key: Any = Unit,
    decoration: ScreenDecoration = Crossfade,
    config: RouterScope<T>.() -> Unit,
) {
    val contentByRoute =
        remember { mutableStateMapOf<Route, @Composable (NavEntry<*>) -> Unit>() }
    val routes = remember { mutableStateListOf<Route>() }
    var lastSynchronizedKey by remember { mutableStateOf<Any?>(null) }

    fun synchronize() {
        if (key == lastSynchronizedKey) return

        val previousRoutesSize = routes.size
        val selectedRoutePosition =
            navigator.selected?.screen?.let { routes.indexOf(it::class) } ?: 0

        routes.clear()

        config(
            object : RouterScope<T>() {
                override fun <C : ChildScreenOf<T>> screen(
                    route: KClass<C>,
                    config: NavConfig,
                    content: @Composable ScreenScope<C>.(NavEntry<C>) -> Unit
                ) {
                    val screenScope = object : ScreenScope<C> {
                        @Composable
                        override fun rememberNavigator(
                            type: KClass<C>,
                            navConfig: NavConfig
                        ) = navigator.rememberNavigator(type, navConfig)
                    }

                    contentByRoute[route] = @Composable { entry ->
                        DisposableEffect(Unit) {
                            onDispose {
                                /** Screen no longer exists in the graph */
                                if (!routes.contains(route)) {
                                    contentByRoute.remove(route)
                                    navigator.removeIf { entry.screen.isRoute(it.screen) }
                                }
                            }
                        }

                        content(screenScope, entry as NavEntry<C>)
                    }

                    if (routes.contains(route)) {
                        error("${route.simpleName} already registered")
                    } else {
                        routes.add(route)
                    }
                }
            }
        )

        check(routes.size > 0) { "Must register at least one route" }

        val removed = contentByRoute.keys - routes

        removed.forEach { route ->
            /**
             * Clear removed routes from the content registry
             * Screen dispose will take care of the selected route
             */
            if (!route.isInstance(navigator.selected?.screen)) {
                contentByRoute.remove(route)
            }
        }

        /** Clear removed routes from the navigator */
        navigator.removeIf { entry ->

            val should = contentByRoute.keys.none { route ->
                route.isInstance(entry.screen)
            }

            should
        }

        /**
         * If the number of routes registered is the same,
         * attempt to auto-navigate to the same position
         */
        if (previousRoutesSize == routes.size) {
            val newRoute = routes[selectedRoutePosition]
            val screen =
                autoConstruct(newRoute, "Attempt to auto-navigate to a new screen with arguments")

            navigator.navigate(screen as ChildScreenOf<T>)
        }


        lastSynchronizedKey = key
    }

    DisposableEffect(key) {
        synchronize()

        onDispose {}
    }

    if (routes.isEmpty()) synchronize()

    if (navigator.isEmpty) {
        val firstRoute = routes.first()
        val firstScreen = autoConstruct(firstRoute, "Start destination must not contain arguments")

        /**
         * Must happen during composition so we auto-navigate in one pass
         * Navigator is backed by snapshot state so its safe to mutate
         */
        navigator.navigate(firstScreen as ChildScreenOf<T>)
    }

    navigator.BackHandler(routes)

    navigator.selected?.let { navEntry ->
        decoration(navEntry) { visibleEntry ->
            contentByRoute[visibleEntry.screen::class]?.invoke(visibleEntry)
        }
    }
}