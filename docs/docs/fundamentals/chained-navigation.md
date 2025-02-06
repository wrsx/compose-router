---
sidebar_position: 6
---

# Chained Navigation

Sometimes it necessary to navigate to a screen which would otherwise require a series of individual navigations. We want to ensure that the path to this new screen remains valid so that the backstack represents a real flow. To achieve this, Compose Router introduces chained navigation:

```kotlin
// Navigate to NewsPost(123), but make sure Home and Newsfeed are on the backstack

navigate(Home.then(Newsfeed).then(NewsPost(123)))
```

Chained navigation also works for nested navigators, allowing you to express navigation paths which span over multiple navigators:

Given this navigation graph:

```kotlin
val rootNavigator = rememberNavigator<Root>()

Router(rootNavigator) {
    screen<SignedIn> {
        val signedInNavigator = rememberNavigator()

        Router(signedInNavigator) {
            screen<TabA> {
                //..
            }
            screen<TabB> {
                //..
            }
        }
    }

    screen<SignedOut> {
        //..
    }
}
```

We could then navigate into TabA via the rootNavigator:

```kotlin
rootNavigator.navigate(SignedIn).then(TabA)
```

Chained navigation is type safe, and will only allow you to express navigation paths which are defined in your graph. Following the above example, the following would be invalid:

```kotlin
// Error: SignedOut is not a child screen of TabA

rootNavigator.navigate(SignedIn.then(TabA).then(SignedOut))
```
