package ankers.compose.router.render

import androidx.compose.animation.AnimatedContent
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

/** The default renderer: crossfades between selected entries; projected entries go to the [OverlayHost]. */
val CrossfadeRenderer: RouterRenderer = {
    val beneath = coveredBeneathProjection()
    inlineSelected?.let { target ->
        Crossfade(targetState = target, label = "router") { render(it, handlesBack = beneath) }
    }
    Projected()
}

/**
 * A single-pane renderer that follows predictive back: the gesture seeks a slide between the outgoing and incoming
 * entries, commit completes it, cancel returns. Pushes slide forward, pops slide back.
 */
val PredictiveBackRenderer: RouterRenderer = {
    val target = inlineSelected
    if (target != null) {
        val seekable = remember { SeekableTransitionState(target) }
        val gesture = transition
        // a gesture popping a projected entry leaves the inline pane where it is; the projection follows it instead
        LaunchedEffect(gesture?.incoming, gesture?.progress, target) {
            if (gesture != null && gesture.incoming != target) seekable.seekTo(gesture.progress, gesture.incoming) else seekable.animateTo(target)
        }
        val entries = entries
        val beneath = coveredBeneathProjection()
        rememberTransition(seekable, label = "router").AnimatedContent(
            transitionSpec = {
                val forward = entries.indexOf(targetState) >= entries.indexOf(initialState)
                if (forward) {
                    (fadeIn() + slideInHorizontally { it / 4 }) togetherWith (fadeOut() + slideOutHorizontally { -it / 4 })
                } else {
                    (fadeIn() + slideInHorizontally { -it / 4 }) togetherWith (fadeOut() + slideOutHorizontally { it })
                }
            },
            contentKey = { it.id },
        ) { entry -> render(entry, handlesBack = beneath) }
    }
    Projected()
}

// a selected projection covers the pane beneath: rendered covered, its lifecycle drops to CREATED and its own back
// handlers disarm, so back reaches the projection
private fun RouterRenderScope.coveredBeneathProjection(): Boolean? = if (selected?.let(::isProjected) == true) false else null

// sends projected entries to the overlay host; keyed so each projection is its own effect
@Composable
private fun RouterRenderScope.Projected() {
    projectedEntries.forEach { key(it.id) { projectToRoot(it) } }
}
