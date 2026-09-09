package ankers.compose.router.back

import ankers.compose.router.navigator.Navigator
import ankers.compose.router.navigator.NavigatorShell

/**
 * A back transition driven by hand — a drag to dismiss — through the same path as the platform's predictive
 * gesture: the target navigator's renderer seeks between [BackAction.outgoing] and [BackAction.incoming] as
 * [progress] advances, [commit] completes the pop and [cancel] returns. Both end the gesture; the second is a no-op.
 */
class BackGesture internal constructor(
    val action: BackAction,
    private val shell: NavigatorShell<*>,
) {
    private var ended = false

    /** Moves the transition to [fraction] of the way from [BackAction.outgoing] to [BackAction.incoming]. */
    fun progress(fraction: Float, edge: BackEdge = BackEdge.None) {
        if (ended) return
        shell.transition = BackTransition(action.outgoing, action.incoming, fraction.coerceIn(0f, 1f), edge)
    }

    /** Completes the pop, unless the navigator has moved on since the gesture began. */
    fun commit() {
        if (ended) return
        ended = true
        action.commit()
        shell.transition = null
    }

    /** Returns to [BackAction.outgoing]. */
    fun cancel() {
        if (ended) return
        ended = true
        shell.transition = null
    }
}

/** Begins a back gesture from what back would do right now, or null when it would do nothing. */
fun Navigator<*>.dragBack(): BackGesture? {
    val action = backAction ?: return null
    return BackGesture(action, action.target.shell)
}
