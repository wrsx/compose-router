---
sidebar_position: 6
---

# Overlays

Sheets, dialogs, and alerts are ordinary destinations on a stack, rendered at the top of the UI tree. Who owns the
overlay's lifetime decides where it lives.

## Global overlays: root destinations

An overlay reachable from anywhere — connect a device, release notes, a deep-linked dialog — is a child of the root
navigator. The root renderer draws the base first and the overlays last, in containers of its choosing:

```kotlin
interface RootOverlay
object ConnectSheet : ChildScreenOf<Root>, RootOverlay

val RootRenderer: RouterRenderer = {
    val overlays = entries.takeLastWhile { it.screen is RootOverlay }
    val base = entries.dropLast(overlays.size).lastOrNull()
    Box {
        base?.let { render(it) }                              // inactive by default: it is not selected
        renderEach(overlays) { ModalBottomSheet(onDismissRequest = { root.back() }) { render(it) } }
    }
}
```

The base stays composed beneath the sheet; back dismisses the sheet, because it is the root's selected entry.

## Section-owned overlays: projected rendering

A filters sheet belongs to the screen that opened it: it should share that screen's state, be typed against that
section, and disappear with it. Register it on the *local* navigator as `projected`, and put an `OverlayHost` above
the root router:

```kotlin
data class Filters(val query: String) : ChildScreenOf<Workouts>

OverlayHost(overlay = { projected -> projected.forEach { Sheet(onDismiss = { root.back() }) { it.content() } } }) {
    Router(root) { /* ... */ }
}

screen<Workouts> {
    val workouts = rememberNavigator()
    Router(workouts) {
        screen<WorkoutList> { /* ... */ }
        projected<Filters> { FiltersSheet(it.screen.query) }     // rendered at the OverlayHost
    }
}
```

The entry is owned by `workouts`: it is typed, saved with the section, reachable by chained navigation, and removed
when `Workouts` is. Its content is composed at the root, with its own ViewModel store and saved state; share state
with the opener through arguments or lexical capture. Back pops it, because it is the selected entry of a navigator on
the selected path.

`OverlayHost` receives the projected entries in projection order and picks containers by your own markers.

### Animating in and out

Each `ProjectedEntry` carries a `transition: Transition<Boolean>` that runs `false → true` as the entry is projected
and `true → false` when its owner drops it. Animate with it and the entry stays composed — live, `Retiring` — until
the exit ends; the projection is removed once the transition settles at `false`:

```kotlin
OverlayHost(overlay = { projected ->
    projected.forEach { item ->
        key(item.entry.id) {
            item.transition.AnimatedVisibility(visible = { it }, enter = fadeIn(), exit = fadeOut()) {
                Scrim(onDismiss = { root.back() })
                Sheet(Modifier.animateEnterExit(enter = slideInVertically { it }, exit = slideOutVertically { it })) {
                    item.content()
                }
            }
        }
    }
}) { /* ... */ }
```

A container with no transition of its own — a platform dialog — reads `item.visible` instead:
`if (item.visible) AlertDialog(/* ... */) { item.content() }`. An overlay that ignores both is removed the next
frame, so nothing leaks. Key each item by `entry.id` so a projection keeps its state while others come and go.
