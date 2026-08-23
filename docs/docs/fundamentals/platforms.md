---
sidebar_position: 11
---

# Platforms

The core is common Kotlin. Each platform binding supplies a retained runtime and back delivery. Screens are
`@Serializable`; start destinations and first tabs are decoded from an empty state, so `object` screens and classes
whose every field has a default need no `start = ...` anywhere. No platform uses `kotlin-reflect`.

## Android

- The runtime — entry registry, ViewModel stores, pending chains — is retained in an activity-scoped `ViewModel`,
  so rotation keeps every entry's ViewModels; process death restores the navigator snapshot and each entry's
  saved-state registry.
- Entries are `HasDefaultViewModelProviderFactory` with a `SavedStateViewModelFactory`: `hiltViewModel()` and
  `SavedStateHandle` work unchanged, and every handle is seeded with the screen — read it with
  `SavedStateHandle.screen<T>()`.
- One `PredictiveBackHandler` per back scope delivers system back and the predictive gesture. Set
  `android:enableOnBackInvokedCallback="true"` (or target API 36) to enable the gesture.
- Screens stay live objects in memory; they are encoded only when the system parcels saved state for process
  death, and decoded by class name on restore. The library's consumer ProGuard rules keep those names.

## Desktop, iOS, web

- The runtime lives for the composition. There is no system back; call `navigator.back()` yourself.
- ViewModels need an explicit factory on iOS and web (no reflection): `viewModel { MyViewModel(...) }`.
- Saved state is in-memory: parked sections restore, but there is no process death to survive.
