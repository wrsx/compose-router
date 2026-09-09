package ankers.compose.router.render

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import ankers.compose.router.entry.EntryId
import ankers.compose.router.entry.NavEntry

/** The default renderer: crossfades between selected entries; projected entries go to the [OverlayHost]. */
val CrossfadeRenderer: RouterRenderer = {
    val beneath = coveredBeneathProjection()
    inlineSelected?.let { target ->
        Crossfade(targetState = target, label = "router") { render(it, handlesBack = beneath) }
    }
    Projected()
}

/**
 * Chooses the transition between two entries. [forward] is true for a push or a replace and false for a pop, judged
 * by where each entry last stood in the navigator, so a popped entry still animates out the way it came.
 */
typealias PredictiveBackSpec = AnimatedContentTransitionScope<NavEntry<*>>.(forward: Boolean) -> ContentTransform

/** The default: a quarter-width slide with a fade, forward for pushes and back for pops. */
val DefaultPredictiveBackSpec: PredictiveBackSpec = { forward ->
    if (forward) {
        (fadeIn() + slideInHorizontally { it / 4 }) togetherWith (fadeOut() + slideOutHorizontally { -it / 4 })
    } else {
        (fadeIn() + slideInHorizontally { -it / 4 }) togetherWith (fadeOut() + slideOutHorizontally { it })
    }
}

/**
 * A single-pane renderer that follows predictive back: the gesture seeks [transitionSpec] between the outgoing and
 * incoming entries, commit completes it, cancel returns. Button-driven navigation runs the same spec.
 */
fun predictiveBackRenderer(transitionSpec: PredictiveBackSpec = DefaultPredictiveBackSpec): RouterRenderer = {
    val target = inlineSelected
    if (target != null) {
        val seekable = remember { SeekableTransitionState(target) }
        val gesture = transition
        // a gesture popping a projected entry leaves the inline pane where it is; the projection follows it instead
        LaunchedEffect(gesture?.incoming, gesture?.progress, target) {
            if (gesture != null && gesture.incoming != target) seekable.seekTo(gesture.progress, gesture.incoming) else seekable.animateTo(target)
        }
        // where each entry last stood: a popped entry is gone from `entries` by the time its exit animates
        val positions = remember { mutableMapOf<EntryId, Int>() }
        entries.forEachIndexed { index, entry -> positions[entry.id] = index }
        positions.keys.retainAll(entries.mapTo(mutableSetOf()) { it.id } + seekable.currentState.id)
        val beneath = coveredBeneathProjection()
        rememberTransition(seekable, label = "router").AnimatedContent(
            transitionSpec = {
                val forward = (positions[targetState.id] ?: 0) >= (positions[initialState.id] ?: -1)
                transitionSpec(forward)
            },
            contentKey = { it.id },
        ) { entry -> render(entry, handlesBack = beneath) }
    }
    Projected()
}

/** [predictiveBackRenderer] with [DefaultPredictiveBackSpec]. */
val PredictiveBackRenderer: RouterRenderer = predictiveBackRenderer()

// a selected projection covers the pane beneath: rendered covered, its lifecycle drops to CREATED and its own back
// handlers disarm, so back reaches the projection
private fun RouterRenderScope.coveredBeneathProjection(): Boolean? = if (selected?.let(::isProjected) == true) false else null

// sends projected entries to the overlay host; keyed so each projection is its own effect
@Composable
private fun RouterRenderScope.Projected() {
    projectedEntries.forEach { key(it.id) { projectToRoot(it) } }
}
