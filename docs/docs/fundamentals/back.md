---
sidebar_position: 7
---

# Back

The core decides what back does; the platform only delivers the event.

## Resolution

Every navigator exposes a **back action** — what back would do right now, as a value:

```kotlin
navigator.backAction     // BackAction? with target, outgoing, incoming, commit()
navigator.canGoBack
navigator.back()         // backAction?.commit()
```

It is resolved along the **selected path**: root → its selected entry → the navigator nested under that entry → its
selected entry → ... The deepest navigator on that path whose policy has something to do produces the action: a
stack pops its top entry, a tab navigator steps its history or returns to its first tab. Nothing off the selected
path — a screen pushed behind a sheet, a pane that is not in front — can take a press.

`commit()` is stale-safe: if the navigator moved on since the action was resolved, it does nothing.

## Overriding from a renderer

```kotlin
render(entry, handlesBack = true)    // promote a non-selected entry to the front of its navigator
render(entry, handlesBack = false)   // the entry is covered: excluded from back, lifecycle held at CREATED
```

At most one entry per navigator may be promoted.

## Platforms

On Android the library registers one `PredictiveBackHandler` per back scope — the activity, and each window-based
container such as a dialog — enabled by `root.canGoBack`. A gesture captures the action at its start, previews it
through the target navigator's renderer, and commits exactly that action on release.

Other platforms have no system back; call `navigator.back()` from your own input handling.
