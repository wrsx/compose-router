---
slug: /
sidebar_position: 1
---

# Overview

Compose Router is a navigation library for Compose Multiplatform in which the navigation graph lives *inside* the
composition. A navigator owns entries; a `Router` declares which screens it renders; nested routers live in the
content of the entries that host them, so scoping falls out of structure rather than a special API.

Three ideas carry the library:

**A typed graph.** Screens declare which navigator they belong to, and the compiler checks it:

```kotlin
@Serializable object Root : NavigationRoot
@Serializable object Home : ChildScreenOf<Root>
@Serializable object Settings : ChildScreenOf<Home>

root.navigate(Home)          // ok
root.navigate(Settings)      // compile error: Settings belongs to Home's navigator
root.navigate(Home.then(Settings))   // ok: a typed path across both navigators
```

**Renderers.** A `Router` hands its navigator's entries to a renderer, which decides what to show and where — one
screen with a crossfade, a list beside a detail, a base screen beneath a sheet. The library enforces the rules that
keep any layout safe.

**Entry lifetime.** Every entry has a ViewModel store, saved state, and a lifecycle, on every platform. An entry is
*parked* while it is owned but not rendered (a background tab, the screen under the one you pushed) and keeps
everything; it is *retired* when no navigator owns it and nothing renders it, and releases everything — including
the screens parked beneath it.

```kotlin
val navigator = rememberNavigator<Root>()

Router(navigator) {
    screen<Home> { HomeScreen(navigator) }
    screen<Profile> { ProfileScreen(navigator) }
}
```

Stack and tab policies are built in; custom policies plug into the same shell. Android adds predictive back,
`hiltViewModel()`, and `SavedStateHandle` seeding.

:::info
The API in these pages is the productionised one. The design record that produced it is under
**Productionisation plan**.
:::
