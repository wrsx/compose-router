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

## Region-scoped state

A screen that hosts a nested router is also a natural lifetime boundary. For example, state used throughout the
signed-in part of an app can be owned by the `Authenticated` entry and passed to everything beneath it:

```kotlin
@Serializable object Authenticated : ChildScreenOf<Root>
@Serializable object Home : ChildScreenOf<Authenticated>
@Serializable object Account : ChildScreenOf<Authenticated>

screen<Authenticated> {
    val region = viewModel<AuthRegionModel>()
    val authenticated = rememberNavigator()

    CompositionLocalProvider(LocalAuthRegion provides region) {
        Router(authenticated) {
            screen<Home> { HomeScreen(LocalAuthRegion.current) }
            screen<Account> { AccountScreen(LocalAuthRegion.current) }
        }
    }
}
```

`AuthRegionModel` belongs to the `Authenticated` entry. It survives navigation within the region, retained-tab
changes, and periods when the entry is parked. Removing `Authenticated` retires every navigator beneath it and
clears the model after the region has finished rendering, including exit animations.

Child entries still have their own ViewModel stores. Calling `viewModel<AuthRegionModel>()` inside `Home` would
therefore create a Home-scoped instance rather than retrieve the authenticated one. Pass the region as a parameter,
provide it through a custom `CompositionLocal`, or connect the same hierarchy to a DI container through the events
API below. A projected overlay is composed at the `OverlayHost`, so give it the region through an argument or lexical
capture rather than relying on a custom composition local.

If the scoped value needs deterministic cleanup, own it from a ViewModel and close it in `onCleared()`, or create and
close it from `EntryEvents`. Only use a navigation entry for the scope when their lifetimes really match. A login
session or token store will often belong to application authentication state, while signed-in UI state, feature
components and flow coordinators are good candidates for the `Authenticated` entry.

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
