---
sidebar_position: 5
---

# Animations

Animations can be implemented through screen decoration. `Router` takes a `ScreenDecoration`:

```kotlin
typealias ScreenDecoration = @Composable (
    selected: NavEntry<*>,
    content: (@Composable (NavEntry<*>) -> Unit),
) -> Unit
```

`selected` is the current selected NavEntry on the navigator\
`content` is a lambda which can take any valid NavEntry on the navigator and render its corresponding screen

For example, the default Crossfade animation is implemented as:

```kotlin
val CrossfadeDecoration: ScreenDecoration = @Composable { selected, content ->
    Crossfade(selected) { visibleEntry ->
        content.invoke(visibleEntry)
    }
}
```
