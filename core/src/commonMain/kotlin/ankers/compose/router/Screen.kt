package ankers.compose.router

import kotlin.reflect.KClass

/**
 * Marker for every destination. Screens are plain `@Serializable` values: an `object`, or a `data class` carrying
 * arguments. Serialization is how a screen survives process death, so a screen without a serializer is rejected
 * when it is navigated to.
 */
interface Screen

/** The root of a navigation tree. Create its navigator with `rememberNavigator<Root>()`. */
interface NavigationRoot : Screen

/**
 * A screen owned by the navigator of [T]. Only children of [T] can be registered in that navigator's
 * [Router] or passed to its `navigate`, which is checked at compile time. [T] is contravariant, so a screen typed
 * against an interface is accepted by the navigator of any screen implementing it: a feature module can declare
 * `interface Coaching : Screen` and type its screens `ChildScreenOf<Coaching>` without naming the app's tab.
 */
interface ChildScreenOf<in T : Screen> : Screen

/** A registered route: the class of a screen. */
typealias Route = KClass<out Screen>

internal val Route.routeName: String get() = simpleName ?: toString()
