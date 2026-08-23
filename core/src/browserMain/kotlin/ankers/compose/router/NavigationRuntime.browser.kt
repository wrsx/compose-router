package ankers.compose.router.navigator

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import ankers.compose.router.host.NoReflectionOwnerPlatform

// lives with the composition: leaving it ends every entry
@Composable
actual fun rememberNavigationRuntime(rootKey: String): NavigationRuntime {
    val runtime = remember(rootKey) { NavigationRuntime(NoReflectionOwnerPlatform) }
    DisposableEffect(runtime) { onDispose { runtime.clear() } }
    return runtime
}
