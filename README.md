# Compose Router

A typed, composition-embedded navigation library for Compose Multiplatform. The navigation graph lives inside your
composition, nested navigators scope state naturally, and the type system checks which screens belong to which
navigator — at declaration and at navigation.

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

What you get:

- **Typed graph.** `ChildScreenOf<T>` makes "this screen belongs to that navigator" a compile-time fact, and
  `navigate(Home.then(Profile).then(Settings))` type-checks the whole path across nested navigators.
- **Stacks and tabs out of the box**, with two back disciplines for tabs and retained tab state.
- **Renderers**, not a fixed layout: crossfade by default, predictive back, list/detail, sheets and dialogs — any
  layout of a navigator's entries, with the rules that keep it safe enforced at runtime.
- **Overlays owned by the right navigator**: a sheet can belong to the section that opened it and still draw
  above everything.
- **Entry lifetime done properly**: ViewModels, saved state, and lifecycle per entry on every platform; parked
  screens keep their state, retired ones release it — including screens parked in other tabs.
- **Observable**: scoped events for analytics or DI, and `navigator.describe()` for a text dump of the whole tree.

Android gets predictive back, `hiltViewModel()`, and `SavedStateHandle` seeding; the core is common Kotlin with JVM,
iOS, JS, and Wasm targets.

## Why

Reach for it when the app has nested, feature-owned stacks; mixes tabs and stacks; wants the compiler to check
which screens belong to which navigator; is Compose-only; and needs resource lifetime to be exactly right.

The back stack is not a list you own, on purpose. Releasing a ViewModel at the right moment, keeping a parked
tab's state, retiring a section with everything beneath it, drawing a section's sheet above the whole app, and
resolving back across nested stacks all need one place that knows every owner and every host of every entry. The
navigator is that place; what stays open is the policy (how navigate, pop and back mutate entries) and the
renderer (what the entries look like). The trade-offs — more rules, `@Serializable` screens, renderers you write —
are spelled out in [Why Compose Router](docs/docs/why.md).

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

The docs are a Docusaurus site under `docs/`:

```sh
cd docs && npm install && npm start
```

Start with **Basic usage**, then **Rendering**, **Overlays**, **Back**, and **Lifetime**. The sample under
`samples/multiplatform` runs on Android (`:samples:multiplatform:installDebug`) and desktop
(`:samples:multiplatform:run`) — resize the desktop window on the Items tab to see list/detail adapt.

## Building

Requires a JDK 17+ on the path for Gradle itself; the build provisions its own JDK 17 toolchain for compilation.

```sh
./gradlew :core:jvmTest                       # unit, contract, harness and headless ui tests
./gradlew :core:compileDebugKotlinAndroid
./gradlew :samples:multiplatform:assembleDebug
```
