package ankers.compose.router.host

import androidx.lifecycle.SavedStateHandle
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.savedState
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import ankers.compose.router.Route
import ankers.compose.router.Screen
import ankers.compose.router.routeName
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.serializer

/** Key under which an entry's screen is seeded into its ViewModels' `SavedStateHandle`; read it with [screen]. */
const val SCREEN_SAVED_STATE_KEY: String = "ankers.compose.router.screen"

/**
 * The screen of the entry that created this ViewModel, for view models whose construction you do not control, such
 * as Hilt's. View models you construct yourself can take the screen from `CreationExtras.screen()` instead.
 */
inline fun <reified T : Screen> SavedStateHandle.screen(): T {
    val saved = checkNotNull(get<SavedState>(SCREEN_SAVED_STATE_KEY)) { "no screen in this SavedStateHandle: it was not created for a navigation entry" }
    return decodeFromSavedState(serializer<T>(), saved)
}

// the serializer of a screen class, or null when it is not @Serializable
@OptIn(ExperimentalSerializationApi::class)
@Suppress("UNCHECKED_CAST")
internal fun screenSerializerOrNull(route: Route): KSerializer<Screen>? =
    try {
        serializer(route, emptyList(), isNullable = false) as KSerializer<Screen>
    } catch (e: SerializationException) {
        null
    }

internal fun screenSerializer(screen: Screen): KSerializer<Screen> =
    screenSerializerOrNull(screen::class)
        ?: error("${screen::class.routeName} is not @Serializable. Every screen must be, so navigation state survives process death")

internal fun encodeScreen(screen: Screen): SavedState = encodeToSavedState(screenSerializer(screen), screen)

internal fun decodeScreen(route: Route, state: SavedState): Screen =
    decodeFromSavedState(checkNotNull(screenSerializerOrNull(route)) { "${route.routeName} is not @Serializable" }, state)

/** Seeds a `SavedStateHandle` with [screen] under [SCREEN_SAVED_STATE_KEY]. */
internal fun screenArgs(screen: Screen): SavedState = savedState { putSavedState(SCREEN_SAVED_STATE_KEY, encodeScreen(screen)) }

/** An argument-free [route] decoded from an empty state: objects and classes whose every field has a default. */
internal fun constructStartDestination(route: Route): Screen? {
    val serializer = screenSerializerOrNull(route) ?: return null
    return try {
        decodeFromSavedState(serializer, savedState())
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        // the saved-state decoder reports a missing required field this way
        null
    }
}

/** How a screen is kept in saved state: live in memory, encoded only where the platform writes it out. */
internal expect fun Screen.toSaveable(): Any

internal expect fun screenFromSaveable(saved: Any): Screen
