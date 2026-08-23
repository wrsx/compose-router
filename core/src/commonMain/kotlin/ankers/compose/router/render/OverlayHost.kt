package ankers.compose.router.render

import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import ankers.compose.router.entry.EntryId
import ankers.compose.router.entry.NavEntry
import kotlinx.coroutines.flow.collectLatest

/** An entry projected to the root: its owner's entry host, composed wherever the overlay places [content]. */
class ProjectedEntry internal constructor(
    val entry: NavEntry<*>,
    internal val order: Int,
    /** Progress of a predictive back gesture that would pop this entry, while one is in flight. */
    internal val backProgress: State<Float?>,
    val content: @Composable () -> Unit,
) {
    internal val seekable = SeekableTransitionState(false)

    /** Whether the owner still holds the entry; `false` while it animates out. */
    var visible: Boolean by mutableStateOf(true)
        internal set

    /**
     * Runs `false → true` as the entry is projected and `true → false` once its owner drops it, and follows a
     * predictive back gesture that would pop the entry. Animate with it
     * (`item.transition.AnimatedVisibility(visible = { it }, …)`) and [content] stays composed, live, until the
     * exit ends: the projection is removed when the transition settles at `false`. An overlay that ignores it is
     * removed the next frame.
     */
    lateinit var transition: Transition<Boolean>
        internal set
}

class OverlayRegistry internal constructor() {
    private var sequence = 0
    private val items = mutableStateMapOf<EntryId, ProjectedEntry>()

    /** Projected entries in the order they were projected, including those animating out. */
    val projected: List<ProjectedEntry> get() = items.values.sortedBy { it.order }

    internal fun add(entry: NavEntry<*>, backProgress: State<Float?>, content: @Composable () -> Unit) {
        items[entry.id] = ProjectedEntry(entry, sequence++, backProgress, content)
    }

    // the owner dropped the entry: start the exit, removal follows from the transition
    internal fun release(id: EntryId) {
        items[id]?.visible = false
    }

    internal fun drop(item: ProjectedEntry) {
        if (items[item.entry.id] === item) items.remove(item.entry.id)
    }
}

val LocalOverlayRegistry = staticCompositionLocalOf<OverlayRegistry?> { null }

/**
 * Hosts entries projected from any router beneath it. [overlay] receives them in projection order and chooses
 * their containers — a sheet, a dialog, a custom layer — composing each one's [ProjectedEntry.content] inside.
 * Each entry carries a [ProjectedEntry.transition] to animate its entrance, its exit, and predictive back.
 */
@Composable
fun OverlayHost(
    modifier: Modifier = Modifier,
    overlay: @Composable (projected: List<ProjectedEntry>) -> Unit,
    content: @Composable () -> Unit,
) {
    val registry = remember { OverlayRegistry() }
    CompositionLocalProvider(LocalOverlayRegistry provides registry) {
        Box(modifier) {
            content()
            val projected = registry.projected
            projected.forEach { key(it.entry.id) { Track(it, registry) } }
            overlay(projected)
        }
    }
}

// one transition per projection, created before the overlay reads it: enters, follows a gesture, exits, then drops
@Composable
private fun Track(item: ProjectedEntry, registry: OverlayRegistry) {
    item.transition = rememberTransition(item.seekable, label = "projected ${item.entry.id}")
    LaunchedEffect(item) {
        snapshotFlow { item.visible to item.backProgress.value }.collectLatest { (visible, progress) ->
            when {
                // the owner dropped it: the gesture that did so is over, whatever its last reported progress
                !visible -> {
                    item.seekable.animateTo(false)
                    registry.drop(item)
                }
                progress != null -> item.seekable.seekTo(progress.coerceIn(0f, 1f), targetState = false)
                else -> item.seekable.animateTo(true)
            }
        }
    }
}
