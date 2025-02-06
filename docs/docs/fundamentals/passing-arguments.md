---
sidebar_position: 4
---

# Passing Arguments

The instance of the screen object passed to `navigate` is the same instance passed into the screen content. Define arguments on your screen object:

```kotlin
data class UserProfile(val id: Int)

Button(onClick = {
    navigator.navigate(UserProfile(123))
}) {
    Text("Navigate")
 }
```

And receive the arguments inside the screen content:

```kotlin
screen<UserProfile> { navEntry ->
    UserProfileScreen(navEntry.screen.id)
}
```
