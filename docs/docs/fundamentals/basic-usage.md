---
sidebar_position: 1
---

# Basic usage

Declare a root and the screens that belong to it. Screens are plain `@Serializable` values — an `object`, or a
`data class` carrying arguments. kotlinx.serialization is how they survive process death; a screen without a
serializer is rejected the moment it is navigated to, on every platform. Apply the Kotlin serialization compiler
plugin (`kotlin("plugin.serialization")`) in every module that declares screens; the library brings
`kotlinx-serialization-core` itself.

```kotlin
@Serializable object Root : NavigationRoot
@Serializable object Home : ChildScreenOf<Root>
@Serializable data class Article(val id: Int) : ChildScreenOf<Root>
```

Create the root navigator and declare its graph in a `Router`. The first registered route is the start destination.

```kotlin
val root = rememberNavigator<Root>()

Router(root) {
    screen<Home> { HomeScreen(onOpen = { id -> root.navigate(Article(id)) }) }
    screen<Article> { entry -> ArticleScreen(entry.screen.id) }
}
```

## Nesting

A screen's content can create the navigator for its own children. The navigator is owned by that entry: parked with
it, restored with it, and released with it.

```kotlin
object Profile : ChildScreenOf<Root>
object Settings : ChildScreenOf<Profile>

Router(root) {
    screen<Home> { /* ... */ }
    screen<Profile> {
        val profile = rememberNavigator()   // inferred as Navigator<Profile>
        Router(profile) {
            screen<Settings> { /* ... */ }
        }
    }
}
```

An entry hosts at most one navigator. Two independent stacks under one screen are two entries.

## Start destinations

The first route is constructed without arguments on JVM and Android (an `object`, or a class whose constructor has
only defaults). On other platforms, or for a start destination with arguments, pass it explicitly:

```kotlin
Router(root, start = Article(1)) { /* ... */ }
```

## Keys

A root navigator is identified by a key, which namespaces every entry id beneath it (`Root/1`, `Root/1/2`, ...). The
default is the root type's name; two live roots in one activity need distinct keys:

```kotlin
rememberNavigator<Root>(key = "main")
```
