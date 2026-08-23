# Compose Router

Compose Router is a navigation library for Compose Multiplatform. Routers are declared in composition, and nested
navigators are owned by the entries that create them. Screens declare which navigator they belong to, so the same
relationship is checked when routes are registered and when navigation occurs.

```kotlin
@Serializable object Root : NavigationRoot
@Serializable object Home : ChildScreenOf<Root>
@Serializable object Profile : ChildScreenOf<Root>
@Serializable object Settings : ChildScreenOf<Profile>

@Composable
fun App() {
    val root = rememberNavigator<Root>()

    Router(root) {
        screen<Home> { HomeScreen(onProfile = { root.navigate(Profile) }) }
        screen<Profile> {
            val profile = rememberNavigator()          // Navigator<Profile>, owned by this entry
            Router(profile) {
                screen<Settings> { SettingsScreen() }
            }
        }
        screen<Settings> { }                           // compile error: Settings is not a child of Root
    }
}
```

`ChildScreenOf<T>` restricts a navigator and its router to the children of `T`. It also allows a path such as
`Home.then(Profile).then(Settings)` to be checked across nested navigators. This catches graph wiring mistakes, but
does not require the application to maintain a mutable back stack.

Stack and retained-tab policies are included. Rendering is separate from navigation policy: the built-in renderers
cover crossfade and predictive back, while custom renderers can implement multi-pane layouts, sheets or dialogs.
An overlay can remain owned by the feature that opened it while being rendered higher in the UI.

Each entry has its own ViewModel store, saved state and lifecycle. Entries parked in another tab keep their state;
entries are released after they are no longer owned or rendered. Scoped events are available for analytics and DI,
and `navigator.describe()` prints the current navigation tree.

Android gets predictive back, `hiltViewModel()`, and `SavedStateHandle` seeding; the core is common Kotlin with JVM,
iOS, JS, and Wasm targets.

## Why

Compose Router is aimed at apps with nested feature-owned stacks, retained tabs, section-owned overlays or resources
whose lifetime should follow a navigation entry.

The application does not own a mutable back stack. The navigator owns entry identity, lifetime, restoration and
back resolution; applications choose the navigation policy and renderer. This is useful when those runtime rules
should be consistent across the app. It is a poor fit when navigation needs to live in a ViewModel or reducer, or
when the app needs a topology other than a tree of stacks, tabs and overlays. The full rationale and trade-offs are
in [Why Compose Router](docs/docs/why.md).

## Setup

Screens are `@Serializable`, so modules that declare them need the Kotlin serialization compiler plugin alongside
the library:

```kotlin
plugins {
    kotlin("plugin.serialization") version "<kotlin version>"
}

dependencies {
    implementation("ankers.compose.router:core:<version>")   // brings kotlinx-serialization-core
}
```

## Documentation

The documentation is a Docusaurus site under `docs/`:

```sh
cd docs && npm install && npm start
```

The sample under `samples/multiplatform` runs on Android (`:samples:multiplatform:installDebug`) and desktop
(`:samples:multiplatform:run`). Resize the desktop window on the Items tab to see the list/detail renderer adapt.

## Building

Requires a JDK 17+ on the path for Gradle itself; the build provisions its own JDK 17 toolchain for compilation.

```sh
./gradlew :core:jvmTest                       # unit, contract, harness and headless ui tests
./gradlew :core:compileDebugKotlinAndroid
./gradlew :samples:multiplatform:assembleDebug
```
