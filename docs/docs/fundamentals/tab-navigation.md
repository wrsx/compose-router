---
sidebar_position: 3
---

# Tab Navigation

A tab navigator reuses the same screen instance when navigating between screens.

Create a tab navigator by passing `NavConfig.Tab()` to `rememberNavigator`

```kotlin
val tabNavigator = rememberNavigator(NavConfig.Tab())
```

### Back Behaviour

Two variants are supported:

#### History

Navigating back steps through tab history, always ending back at the first tab. A tab only exists once in the history.

```kotlin
rememberNavigator(NavConfig.Tab(BackPress.Stack))
```

#### Back-to-first

Navigating back will return to the first tab if it wasnt already selected.

```kotlin
rememberNavigator(NavConfig.Tab(BackPress.First))
```

Both variants ensure that the first screen is always the last screen to be removed.

:::info
First tab refers to the first tab screen registered positionally in the graph
:::
