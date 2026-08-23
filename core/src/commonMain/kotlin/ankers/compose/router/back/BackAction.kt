package ankers.compose.router.back

import ankers.compose.router.entry.NavEntry
import ankers.compose.router.navigator.Navigator

/**
 * What back would do right now, as a value. Produced by the deepest navigator on the back path whose policy has
 * something to do. Predictive back captures one at gesture start and commits exactly that.
 */
interface BackAction {
    val target: Navigator<*>

    /** Leaves the front on commit. */
    val outgoing: NavEntry<*>

    /** Becomes selected on commit. */
    val incoming: NavEntry<*>

    /** Applies the step, or does nothing if [target] has moved on since this action was resolved. */
    fun commit()
}

/** Which edge a predictive back gesture started from. */
enum class BackEdge { Left, Right, None }

/** A predictive back gesture in progress, as seen by the target navigator's renderer. */
class BackTransition(
    val outgoing: NavEntry<*>,
    val incoming: NavEntry<*>,
    val progress: Float,
    val edge: BackEdge,
)
