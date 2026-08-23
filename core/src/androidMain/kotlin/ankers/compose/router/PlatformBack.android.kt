package ankers.compose.router.back

import androidx.activity.BackEventCompat
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import ankers.compose.router.navigator.Navigator

// one registration per dispatcher: the activity's, and each window's (dialog, sheet)
@Composable
internal actual fun PlatformBackScope(root: Navigator<*>, content: @Composable () -> Unit) {
    val owner = LocalOnBackPressedDispatcherOwner.current
    if (owner == null || owner === LocalBackScope.current) {
        content()
        return
    }
    PredictiveBackHandler(enabled = root.canGoBack) { progress ->
        val action = root.backAction ?: return@PredictiveBackHandler
        val shell = action.target.shell
        try {
            progress.collect { event ->
                shell.transition = BackTransition(
                    outgoing = action.outgoing,
                    incoming = action.incoming,
                    progress = event.progress,
                    edge = if (event.swipeEdge == BackEventCompat.EDGE_LEFT) BackEdge.Left else BackEdge.Right,
                )
            }
            action.commit()
        } finally {
            shell.transition = null
        }
    }
    CompositionLocalProvider(LocalBackScope provides owner) { content() }
}
