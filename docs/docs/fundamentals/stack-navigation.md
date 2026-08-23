---
sidebar_position: 2
---

# Stack navigation

`NavConfig.Stack` is the default policy: entries stack, duplicates are allowed, back pops the top entry.

```kotlin
val stack = rememberNavigator<Root>()            // a StackNavigator<Root>

stack.navigate(Article(1))
stack.navigate(Article(2))
stack.pop()                                      // removes Article(2)
stack.popToRoot()
```

A `StackNavigator` adds the operations only a stack can express:

```kotlin
stack.popTo<Home>()                              // back to the most recent Home
stack.popTo<Home>(inclusive = true)              // and pop Home as well
stack.replace(Article(3))                        // swap the selected entry
stack.navigate(Article(3), singleTop = true)     // no-op if an equal screen is already on top
```

`pop` never empties a navigator: an empty navigator would only re-create its start destination.

## Back

Back is not `pop`. `navigator.back()` commits the navigator's **back action** — the resolved step of the deepest
navigator on the selected path that has something to do — so a nested stack pops before its parent, and a tab
navigator steps its history rather than removing a tab. See **Back**.
