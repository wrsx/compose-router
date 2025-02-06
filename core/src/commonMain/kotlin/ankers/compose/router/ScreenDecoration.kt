package ankers.compose.router

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable

typealias ScreenDecoration = @Composable (
    selected: NavEntry<*>,
    content: (@Composable (NavEntry<*>) -> Unit),
) -> Unit

val CrossfadeDecoration: ScreenDecoration = @Composable { selected, content ->
    Crossfade(selected) { visibleEntry ->
        content.invoke(visibleEntry)
    }
}