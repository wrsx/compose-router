---
sidebar_position: 13
---

# Testing

Navigation state is snapshot state, so most of the library — and most of an app's navigation logic — is testable
on the JVM without an emulator.

- **Plain unit tests** for policies, back resolution, and ids: construct a `NavigatorShell` directly, or a navigator
  through the contract suite.
- **A headless composition** (`Recomposer` + a no-op applier, driven by the test scheduler) for routers, chained
  navigation, structural changes, and lifetime, observed through `entries`, `selected`, `describe()`, and a
  recording `NavigatorEvents`. No layout nodes, so use a renderer that emits none.
- **`runComposeUiTest` on the desktop target** for renderers and the entry host: real layouts and animations,
  headless, in milliseconds.

What needs a device or Robolectric is deliberately small: Android back delivery and Hilt.
