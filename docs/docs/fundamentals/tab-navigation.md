---
sidebar_position: 3
---

# Tab navigation

`NavConfig.Tab()` owns one entry per tab and keeps it while another tab is selected, so switching back restores the
tab exactly as it was — scroll, input, ViewModels, and any stack nested inside it.

```kotlin
val tabs = rememberNavigator(NavConfig.Tab())    // a Navigator<SignedIn>

Router(tabs) {
    screen<Home> { /* ... */ }
    screen<Search> { /* ... */ }
    screen<Profile> { /* ... */ }
}

tabs.navigate(Search)                            // selects Search, creating it the first time
```

`entries` lists the owned tabs in declaration order, and `selected` is the current tab. The first declared tab is
the *first tab* and always exists once the graph is known.

## Back behaviour

Two policies, both operating on a private selection history:

```kotlin
rememberNavigator(NavConfig.Tab(NavConfig.Tab.BackPress.Stack))   // default
rememberNavigator(NavConfig.Tab(NavConfig.Tab.BackPress.First))
```

- **Stack** — back steps through the tabs you visited, then lands on the first tab.
- **First** — back returns to the first tab whenever another is selected.

In both, the first tab is the last place back leaves you; after that, back passes to the parent navigator.
`pop()` steps the history and never removes a tab.

## Keeping every tab composed

Because `entries` is the owned set, a renderer that keeps all tabs alive is a few lines:

```kotlin
Router(tabs, renderer = {
    Box { renderEach(entries) { tab -> AnimatedVisibility(tab == selected) { render(tab) } } }
}) { /* ... */ }
```
