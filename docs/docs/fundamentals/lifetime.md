---
sidebar_position: 8
---

# Lifetime and events

## States

An entry is in one of four situations, and the library keeps them apart:

| | Owned by a navigator | Rendered | What happens |
|---|---|---|---|
| **Active** | yes | yes | selected, settled, uncovered — lifecycle `RESUMED` |
| **Parked** | yes | no | a background tab, the screen under the one you pushed — everything retained, lifecycle `CREATED` |
| **Retiring** | no | yes | popped, still animating out — retained until the host goes, lifecycle `CREATED` |
| **Retired** | no | no | released: ViewModels cleared, saved state dropped, lifecycle `DESTROYED` |

Release cascades. Entry ids are paths (`Root/1/2`), so retiring an entry retires everything beneath its prefix —
including screens parked in other tabs — while any ancestor of a still-rendered descendant stays alive until that
descendant exits.

## Owners

Every entry is a `ViewModelStoreOwner`, `LifecycleOwner`, and `SavedStateRegistryOwner`, on every platform.
`viewModel()`, `LifecycleResumeEffect`, `collectAsStateWithLifecycle`, and `SavedStateHandle` behave as they do under
Navigation 2 and 3. A nested entry's lifecycle never exceeds its parent entry's.

## Events

Observe a navigator with a scoped writer: one object per navigator, per entry, per host. Writers compose with `plus`
and `Discard` is the unit, so a partial observer delegates to it:

```kotlin
val analytics = object : NavigatorEvents by NavigatorEvents.Discard {
    override fun entry(entry: NavEntry<*>) = object : EntryEvents by EntryEvents.Discard {
        override fun retired() = log("left ${entry.screen::class.simpleName}")
    }
}

rememberNavigator<Root>(events = analytics)
```

`EntryEvents.hosted()` returns a `HostEvents` that receives `state(Active | Inactive | Covered | Retiring)` and
`unhosted()`; `EntryEvents.child()` returns the observer for a navigator created under the entry. A DI scope per
screen has the same shape: open in `entry()`, child scope in `child()`, close in `retired()`.

## Inspecting

`navigator.describe()` renders the navigator and everything nested under it:

```
Root [stack] selected=Root/2
  Root/1 Home
  Root/2 Tabs *
    Root/2 [tab:stack] selected=Root/2/1 history=[Root/2/1]
      Root/2/1 Feed *
      Root/2/2 Profile
```
