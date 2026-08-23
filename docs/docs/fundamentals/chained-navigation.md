---
sidebar_position: 10
---

# Chained navigation

Navigate to a screen along a path, so the back stack represents a real flow:

```kotlin
navigate(Home.then(Newsfeed).then(NewsPost(123)))
```

Chains cross navigators. Each segment is delivered to the navigator that owns it — including navigators that do not
exist yet, which receive their segments when they are created:

```kotlin
root.navigate(SignedIn.then(Profile).then(Settings))
```

A navigator always starts with its start destination; a segment equal to the entry already selected reuses it, so
`Profile.then(Settings)` and `Profile.then(ProfileHome).then(Settings)` land on the same stack whether or not
`Profile` had been visited before. Delivery is addressed to the entry each segment was navigated under: the rest of the chain waits for *that*
entry's navigator, and is dropped if the entry is released first — a section removed while a chain is in flight
never replays it later. Any other navigation in the meantime interleaves normally.

Chains are typed: `then` only accepts a sibling on the same navigator or a child of the preceding screen, so a path
that the graph cannot express does not compile.

Deep links map onto chains:

```kotlin
when (url.path) {
    "profile/settings" -> root.navigate(SignedIn.then(Profile).then(Settings))
}
```
