Compose Router is a navigation builder for Jetpack Compose. The project is Multiplatform, but bindings only exist for Android right now.

Define a set of screens:
```kotlin
object Root : NavigationRoot
object Home : ChildScreenOf<Root>
object Profile : ChildScreenOf<Root>
object Settings : ChildScreenOf<Profile>  
```
Define a navigation graph:
```kotlin
val navigator = rememberNavigator<Root>()

Router(navigator) {
	screen<Home> {
		HomeScreen(navigator)
	}
	
	screen<Profile> {
		ProfileScreen(navigator)
	}
}
```
💡 Compose Router will automatically navigate to the first screen positionally.

Navigate to a screen:
```kotlin
navigator.navigate(Profile)
```

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

