package ankers.compose.router

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable

typealias ScreenDecoration = @Composable (
    selected: NavEntry<*>,
    content: (@Composable (NavEntry<*>) -> Unit),
) -> Unit

val Crossfade: ScreenDecoration =
    @Composable { selected, content ->
        Crossfade(selected, label = "", animationSpec = tween(1000)) { visibleEntry ->
            content.invoke(visibleEntry)
        }
    }