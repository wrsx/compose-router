---
sidebar_position: 4
---

# Passing arguments

Arguments are fields on the screen. The instance you navigate to is the instance the content receives:

```kotlin
data class UserProfile(val id: Int) : ChildScreenOf<Root>

navigator.navigate(UserProfile(123))

screen<UserProfile> { entry -> UserProfileScreen(entry.screen.id) }
```

## In retained values

A retained value is constructed by your own code, so the screen is an ordinary constructor argument:

```kotlin
class UserProfileModel(val user: UserProfile, scope: CoroutineScope)

screen<UserProfile> { entry ->
    val model = retained { UserProfileModel(entry.screen, coroutineScope) }
}
```

## In ViewModels

Every entry is a `ViewModelStoreOwner`, so `viewModel()` scopes a ViewModel to the entry. The screen reaches the
ViewModel through its creation extras on every platform:

```kotlin
class UserProfileViewModel(val user: UserProfile) : ViewModel()

screen<UserProfile> {
    val vm = viewModel { UserProfileViewModel(createSavedStateHandle(), screen<UserProfile>()) }
}
```

The entry also seeds every `SavedStateHandle` with the screen, so a ViewModel whose construction you do not control —
a `@HiltViewModel` — reads it from the handle:

```kotlin
@HiltViewModel
class UserProfileViewModel @Inject constructor(handle: SavedStateHandle) : ViewModel() {
    val user = handle.screen<UserProfile>()
}
```
