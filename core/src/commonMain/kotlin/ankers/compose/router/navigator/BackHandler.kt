package ankers.compose.router.navigator

import ankers.compose.router.Route

internal interface BackHandler {
    val enabled: Boolean
    fun onBack(routes: List<Route>)
}