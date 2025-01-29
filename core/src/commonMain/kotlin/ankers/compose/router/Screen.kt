package ankers.compose.router

import kotlin.reflect.KClass

typealias Route = KClass<out Screen>

fun Screen.isRoute(screen: Screen) = screen::class.isInstance(this)

interface NavEntry<T : Screen> {
    val id: Long
    val screen: T
}

fun interface NavEntryCreator {
    fun create(screen: Screen): NavEntry<*>
}

fun interface NavEntryRemoveListener {
    fun onRemoved(id: Long)
}

expect interface Screen