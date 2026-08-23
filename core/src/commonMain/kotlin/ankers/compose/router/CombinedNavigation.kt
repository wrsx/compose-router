package ankers.compose.router

import kotlin.jvm.JvmName

/**
 * A typed path across one or more navigators, built with [then]. Navigating a chain pushes each segment on the
 * navigator that owns it, including navigators that do not exist yet: pending segments are delivered when their
 * navigator is created.
 */
class CombinedNavigation<L : Screen, R : Screen> internal constructor(
    /** (screen whose navigator owns it — null for the navigator the chain is navigated on, screen), in push order. */
    internal val segments: List<Pair<Screen?, Screen>>,
)

/** Then a sibling: both screens belong to the same navigator. */
fun <P, L : ChildScreenOf<P>, R : ChildScreenOf<P>> L.then(same: R): CombinedNavigation<L, R> =
    CombinedNavigation(listOf(null to this, null to same))

/** Then a child: [child] belongs to the navigator hosted by this screen. */
fun <L : Screen, R : ChildScreenOf<L>> L.then(child: R): CombinedNavigation<L, R> =
    CombinedNavigation(listOf(null to this, this to child))

@JvmName("ABThenB")
fun <T, L : Screen, M : ChildScreenOf<T>, R : ChildScreenOf<T>> CombinedNavigation<L, M>.then(same: R): CombinedNavigation<L, R> =
    CombinedNavigation(segments + (segments.last().first to same))

@JvmName("AAThenB")
fun <T, L : Screen, M : ChildScreenOf<T>, R : ChildScreenOf<M>> CombinedNavigation<L, M>.then(child: R): CombinedNavigation<L, R> =
    CombinedNavigation(segments + (segments.last().second to child))
