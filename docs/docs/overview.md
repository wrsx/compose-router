---
slug: /
sidebar_position: 1
---

# Overview

Compose Router is a navigation DSL for Jetpack Compose. The project is Multiplatform, but bindings only exist for Android right now.

The goal of this library is to provide a simple yet flexible approach for managing navigation in Compose. It supports all of the core features you would expect from a navigation library, along with several enhancements. Noteable features include:

Out of the box config for common navigation patterns like **Stack** and **Tab** navigation

```kotlin
val navigator = rememberNavigator(NavConfig.Tab())
// OR
val navigator = rememberNavigator(NavConfig.Stack)
```

Supports structural changes to the navigation graph at runtime:

```kotlin
if (signedIn) {
    screen<SignedIn> {
        // ..
    }
} else {
    screen<SignedOut> {
        // ..
    }
}
```

Suports type-safe chained navigation across nested graphs:

```kotlin
navigator.navigate(SignedIn.then(Profile).then(Settings))
```

A full working example app demonstrating common navigation patterns can be found [here](https://github.com/wrsx/compose-router/tree/main/samples/multiplatform).

:::warning
This library is an experimental approach towards handling navigation in compose. It is a personal project and a work in progress, so some features may be lacking or incomplete.
:::
