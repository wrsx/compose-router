# Deep Links

First class support for deep links is not supported yet. Instead, you can utilize chained navigation to map a deep link to a navigation path:

```kotlin
fun onDeepLinkReceived(url: String) {
    when(url) {
        "profile/settings" -> navigator.navigate(SignedIn.then(Profile).then(Settings))
        //..
    }
}
```
