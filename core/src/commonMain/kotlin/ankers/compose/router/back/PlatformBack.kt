package ankers.compose.router.back

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import ankers.compose.router.navigator.Navigator

// the back dispatcher already registered above this point; projections carry it so they do not register again
internal val LocalBackScope = compositionLocalOf<Any?> { null }

/**
 * Registers the core's back action with the platform once per back scope — at the root, and again inside any
 * window-based container with its own dispatcher. The core resolves what back does; the platform only delivers it.
 */
@Composable
internal expect fun PlatformBackScope(root: Navigator<*>, content: @Composable () -> Unit)
