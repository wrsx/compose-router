package ankers.compose.router.back

import androidx.compose.runtime.Composable
import ankers.compose.router.navigator.Navigator

// no system back on these targets; call navigator.back() from your own input handling
@Composable
internal actual fun PlatformBackScope(root: Navigator<*>, content: @Composable () -> Unit) = content()
