---
sidebar_position: 9
---

# Structural changes

The graph can react to application state. `Router` re-declares its graph on every composition; whenever the declared
routes change, it re-synchronizes:

```kotlin
Router(root) {
    if (signedIn) {
        screen<SignedIn> { /* ... */ }
    } else {
        screen<SignedOut> { /* ... */ }
    }
}
```

When `signedIn` flips, entries of removed routes are retired — with everything nested beneath them, parked or not —
the policy reconciles its selection, and if the navigator is left empty the new start destination is constructed.
A removed screen keeps rendering until its exit transition completes.

Removing a **route** is the only structural teardown. Removing the **Router itself** — composing it
conditionally, or a nested Router leaving composition with its screen — retires nothing: the entries stay owned
by the runtime with their ViewModels and saved state intact, exactly as a parked tab's do, and reattach when a
Router for that navigator next composes. Ownership outlives rendering by design; if it did not, every parked
tab's nested stack would die with it. To tear a subtree down, remove its route or retire its owning entry —
never by un-composing a Router and expecting release.

Navigating to a route that is not in the live graph is dropped with a diagnostic rather than rendering nothing:
the typed graph checks the static graph, the router checks the live one.

Because the declaration runs every composition, destination content always sees the current captures of its
enclosing composable — parameters and callbacks included — not the ones from the composition that first built the
graph. A navigator nested under a retiring section rejects navigation with a diagnostic while the section animates
out, so nothing can be created beneath an entry that is already gone.
