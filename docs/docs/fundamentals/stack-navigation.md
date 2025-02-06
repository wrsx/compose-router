---
sidebar_position: 2
---

# Stack Navigation

A stack navigator behaves like a regular stack of destinations. Navigating to a new screen will place a new entry at the top of the stack, and navigating back will pop that destination from the stack.

The default navigator returned from `rememberNavigator` is a stack navigator, but you can optionally pass `NavConfig.Stack` explicitly:

```kotlin
val stackNavigator = rememberNavigator(NavConfig.Stack)
```

The ModalStack example [here](https://github.com/wrsx/compose-router/blob/main/samples/multiplatform/src/androidMain/kotlin/ankers/compose/router/App.kt) demonstrates basic usage of a stack navigator.
