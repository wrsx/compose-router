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
| **Retired** | no | no | released: retained values closed, ViewModels cleared, saved state dropped, lifecycle `DESTROYED` |

Release cascades. Entry ids are paths (`Root/1/2`), so retiring an entry retires everything beneath its prefix —
including screens parked in other tabs — while any ancestor of a still-rendered descendant stays alive until that
descendant exits.

## Owners

Every entry is a `ViewModelStoreOwner`, `LifecycleOwner`, and `SavedStateRegistryOwner`, on every platform.
`viewModel()`, `LifecycleResumeEffect`, `collectAsStateWithLifecycle`, and `SavedStateHandle` behave as they do under
Navigation 2 and 3. A nested entry's lifecycle never exceeds its parent entry's.

## Retained values

`retained` keeps a plain object, with no `ViewModel` supertype, for as long as its entry:

```kotlin
class UserProfileModel(
    user: UserProfile,
    repository: UserRepository,
    scope: CoroutineScope,
) {
    val profile: StateFlow<Profile?> = repository.profile(user.id).stateIn(scope, SharingStarted.Eagerly, null)
}

screen<UserProfile> { entry ->
    val model = retained { UserProfileModel(entry.screen, repository, coroutineScope) }
    UserProfileScreen(model.profile.collectAsState().value)
}
```

The value survives recomposition, parking and, on Android, configuration changes. When the entry is released its
`coroutineScope` is cancelled and, if the value is `AutoCloseable`, it is closed. `coroutineScope` dispatches on the
main thread where the platform has one, as `viewModelScope` does; a test constructs the class directly with a test
scope.

An entry retains one value per type, so every composable in its content that asks for `UserProfileModel` gets the
same instance. Pass `key` to retain several values of one type. Name another entry as `owner` to reach the value that
entry retains, for example a sheet that edits the filters of the list beneath it: the value lives as long as the
owner, not the caller.

After process death the restored entry creates its values again. State that must survive belongs in
`savedStateHandle`, which is saved under the value's key and seeded with the entry's screen.

## Region-scoped state

A screen that hosts a nested router is also a natural lifetime boundary. For example, state used throughout the
signed-in part of an app can be owned by the `Authenticated` entry and passed to everything beneath it:

```kotlin
@Serializable object Authenticated : ChildScreenOf<Root>
@Serializable object Home : ChildScreenOf<Authenticated>
@Serializable object Account : ChildScreenOf<Authenticated>

screen<Authenticated> {
    val region = retained { AuthRegion() }
    val authenticated = rememberNavigator()

    CompositionLocalProvider(LocalAuthRegion provides region) {
        Router(authenticated) {
            screen<Home> { HomeScreen(LocalAuthRegion.current) }
            screen<Account> { AccountScreen(LocalAuthRegion.current) }
        }
    }
}
```

`AuthRegion` belongs to the `Authenticated` entry. It survives navigation within the region, retained-tab
changes, and periods when the entry is parked. Removing `Authenticated` retires every navigator beneath it and
releases the region after it has finished rendering, including exit animations.

Child entries retain their own values. Calling `retained { AuthRegion() }` inside `Home` would therefore create a
Home-scoped instance rather than retrieve the authenticated one. Pass the region as a parameter, provide it through
a custom `CompositionLocal`, name the `Authenticated` entry as the `owner`, or connect the same hierarchy to a DI
container through the events API below. A projected overlay is composed at the `OverlayHost`, so give it the region
through an argument or lexical capture rather than relying on a custom composition local.

If the scoped value needs deterministic cleanup, make it `AutoCloseable` so its entry closes it, or create and close
it from `EntryEvents`. Only use a navigation entry for the scope when their lifetimes really match. A login
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
