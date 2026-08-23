package ankers.compose.router.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import ankers.compose.router.back.BackTransition
import ankers.compose.router.back.LocalBackScope
import ankers.compose.router.entry.NavEntry

/** Lays out a router's active entries. Receives a [RouterRenderScope]; composes whatever it likes through [RouterRenderScope.render]. */
typealias RouterRenderer = @Composable RouterRenderScope.() -> Unit

/**
 * What a renderer sees: the owned entries, which one is selected, and the only way to compose them.
 *
 * Rules that keep rendering safe: an entry may be rendered from one place at a time; repeated rendering goes
 * through [renderEach], which keys each iteration; a narrowed scope ([withEntries]) defines its own selection and
 * can only lower activity.
 */
@Stable
class RouterRenderScope internal constructor(
    /** Owned entries in navigator-defined order. */
    val entries: List<NavEntry<*>>,
    /** The navigator's selected entry: the last entry of a stack, the current tab. */
    val selected: NavEntry<*>?,
    /** The predictive back gesture in progress on this navigator, if any. */
    val transition: BackTransition?,
    private val activeCap: Boolean,
    private val projected: (NavEntry<*>) -> Boolean,
    private val renderEntry: @Composable (NavEntry<*>, Boolean, Boolean?) -> Unit,
) {
    /**
     * Composes [entry] in place.
     *
     * [active] defaults to "is the selected entry": transitioning, covered, and peeked entries are inactive; a
     * multi-pane renderer opts extra visible panes in. [handlesBack] overrides back resolution: `true` promotes a
     * non-selected entry to the front of its navigator, `false` marks a rendered entry covered.
     */
    @Composable
    fun render(entry: NavEntry<*>, active: Boolean = entry == selected, handlesBack: Boolean? = null) {
        renderEntry(entry, active && activeCap, handlesBack)
    }

    /** Renders [entries] with each iteration keyed on its id, so reorders move state instead of recreating it. */
    @Composable
    fun renderEach(entries: List<NavEntry<*>>, content: @Composable (NavEntry<*>) -> Unit = { render(it) }) {
        for (entry in entries) key(entry.id) { content(entry) }
    }

    /**
     * Runs [content] in a scope narrowed to [entries]. [selected] defaults to this scope's selection if it is in
     * the subset, else the subset's last entry. `active = false` makes everything rendered inside inactive.
     */
    @Composable
    fun withEntries(
        entries: List<NavEntry<*>>,
        selected: NavEntry<*>? = if (this.selected in entries) this.selected else entries.lastOrNull(),
        active: Boolean = true,
        content: @Composable RouterRenderScope.() -> Unit,
    ) {
        require(entries.all { it in this.entries }) { "withEntries may only narrow this scope's entries" }
        require(selected == null || selected in entries) { "withEntries: selected ${selected?.id} is not among the narrowed entries" }
        val narrowed = transition?.takeIf { it.outgoing in entries && it.incoming in entries }
        val scope = RouterRenderScope(entries, selected, narrowed, activeCap && active, projected, renderEntry)
        scope.content()
    }

    /** True for entries registered with `projected<T>`, which render at the root [OverlayHost] rather than in place. */
    fun isProjected(entry: NavEntry<*>): Boolean = projected(entry)

    val inlineEntries: List<NavEntry<*>> get() = entries.filterNot(::isProjected)

    val projectedEntries: List<NavEntry<*>> get() = entries.filter(::isProjected)

    /** The entry a single-pane renderer shows: the selected entry, or the last in-place entry when it is projected. */
    val inlineSelected: NavEntry<*>? get() = selected?.takeUnless(::isProjected) ?: inlineEntries.lastOrNull()

    /** Composes [entry] inside the nearest [OverlayHost] instead of here. The entry stays owned by this navigator. */
    @Composable
    fun projectToRoot(entry: NavEntry<*>, active: Boolean = entry == selected, handlesBack: Boolean? = null) {
        val registry = LocalOverlayRegistry.current
            ?: error("projectToRoot needs an OverlayHost above the root Router")
        val render by rememberUpdatedState(renderEntry)
        val activeNow by rememberUpdatedState(active && activeCap)
        val handlesBackNow by rememberUpdatedState(handlesBack)
        val backProgress = rememberUpdatedState(transition?.takeIf { it.outgoing.id == entry.id }?.progress)
        val backScope = rememberUpdatedState(LocalBackScope.current)
        DisposableEffect(registry, entry.id) {
            registry.add(entry, backProgress) {
                CompositionLocalProvider(LocalBackScope provides backScope.value) { render(entry, activeNow, handlesBackNow) }
            }
            onDispose { registry.release(entry.id) }
        }
    }
}
