---
sidebar_position: 1
---

# Basic Usage

Define a root navigator by implementing the `NavigationRoot` interface:

```kotlin
object Root : NavigationRoot
```

Define a set of screens which belong to that navigator by using the `ChildScreenOf<T>` interface:

```kotlin
object Home : ChildScreenOf<Root>
object Profile : ChildScreenOf<Root>
```

Now you can define a navigation graph. Create your root navigator with `rememberNavigator<Root>()` and pass it to a Router. Inside the `Router { .. }` block, define the content for each screen:

```kotlin
val rootNavigator = rememberNavigator<Root>()

Router(navigator) {
    screen<Home> {
        HomeScreen(navigator)
    }

    screen<Profile> {
        ProfileScreen(navigator)
    }
}
```

Navigate to a screen:

```kotlin
rootNavigator.navigate(Profile)
```

:::info
Compose Router will automatically choose the first screen as your start destination
:::

Create a natural navigation hierarchy by nesting Routers:

```kotlin
val navigator = rememberNavigator<Root>()

Router(navigator) {
    screen<Home> { .. }

    screen<Profile> {
        val profileRouter = rememberNavigator()

        Router(profileRouter) {
            screen<Settings> { .. }
        }
    }
}
```

Compose Router is type-safe at the graph declaration:

```kotlin
Router(navigator) {
    screen<Home> { .. }

    screen<Profile> { .. }

    screen<Settings> {
        // Error: Settings is not a child screen of Root
    }
}
```

and during navigation:

```kotlin
// Error: Settings is not a child screen of Root
navigator.navigate(Settings)
```
