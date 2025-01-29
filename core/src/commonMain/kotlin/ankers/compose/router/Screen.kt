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

//data class DefaultNavEntry<T : Screen>(
//    override val id: Long,
//    override val screen: T,
//) : NavEntry<T> {
//    override fun equals(other: Any?): Boolean = other is NavEntry<*> && other.id == id
//    override fun hashCode(): Int = id.hashCode()
//}
expect interface Screen