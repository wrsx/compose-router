---
sidebar_position: 5
---

# Rendering

A `Router` does not decide what its navigator looks like; a **renderer** does. The renderer receives a
`RouterRenderScope` — the owned `entries`, the `selected` one, and `render(entry)` — and composes whatever it
likes.

```kotlin
typealias RouterRenderer = @Composable RouterRenderScope.() -> Unit

Router(navigator, renderer = CrossfadeRenderer) { /* ... */ }      // the default
Router(navigator, renderer = PredictiveBackRenderer) { /* ... */ } // slides, follows the back gesture
```

## Writing one

`render(entry)` composes an entry in place, through the *entry host*: the library validates the entry, tracks that
it is rendered, provides its ViewModel store, lifecycle, and saved state, and then composes your registered content.

```kotlin
val TwoPane: RouterRenderer = {
    val list = entries.firstOrNull()
    val detail = entries.getOrNull(1)
    Row {
        AnimatedVisibility(visible = detail == null || wide, Modifier.weight(0.4f)) {
            list?.let { render(it, active = true) }          // slot 1
        }
        AnimatedContent(targetState = detail, Modifier.weight(0.6f)) { d ->
            d?.let { render(it) }                            // slot 2
        }
    }
}
```

Three rules keep any renderer safe, and the host enforces the first:

1. **An entry is rendered from one place at a time.** A second `render(entry)` while another host for it is still
   composed fails with an error naming the entry. Animated containers keep their outgoing content composed until
   the animation ends — so animate *slots* (a pane appearing), never *scenes* (a whole layout crossfading into
   another that contains the same entry).
2. **Keep each entry at one call site.** Composition state, including `rememberSaveable`, is positional: an entry
   re-placed at a different call site is recreated, and only its ViewModels and saved-state registry survive. Give
   each entry a permanent slot and change what the slot *does*, as `TwoPane` does with visibility and weights.
3. **Repeated rendering goes through `renderEach`**, which keys each iteration on the entry id so a reorder moves
   state instead of recreating it.

## `active`

`render(entry, active = ...)` defaults to "is the selected entry". The selected entry is active; transitioning,
peeked, and covered entries are not. A multi-pane renderer opts extra visible panes in, as above. Active maps to the
entry's lifecycle: `RESUMED` when active, `STARTED` when rendered but inactive.

## Narrowing a scope

A wrapper renderer narrows the scope for an inner one. The narrowed scope defines its own `selected` and can only
lower activity:

```kotlin
fun withOverlays(inner: RouterRenderer): RouterRenderer = {
    val (overlays, rest) = entries.partition { it.screen is RootOverlay }
    Box {
        withEntries(rest, active = overlays.isEmpty()) { inner() }
        renderEach(overlays) { ModalBottomSheet(onDismissRequest = { navigator.back() }) { render(it) } }
    }
}
```

## Projected entries

Entries registered with `projected<T>` are not rendered in place: the default renderers send them to the nearest
`OverlayHost`, and a custom renderer calls `projectToRoot(entry)`. See **Overlays**.

## Predictive back

`PredictiveBackRenderer` seeks a slide between the outgoing and incoming entries as the gesture progresses. A custom
renderer reads `transition` — the outgoing and incoming entries, progress, and edge — and seeks its own leaving slot.
