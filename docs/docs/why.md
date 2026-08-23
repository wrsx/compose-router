---
title: Why Compose Router
sidebar_position: 2
---

# Why Compose Router

Compose Router is deliberately opinionated. The application can choose how navigation behaves and how entries are
rendered, but it does not mutate a back stack directly. The navigator owns entry identity, lifetime, restoration
and back resolution.

This works well for apps whose navigation is mostly a tree of stacks, tabs and overlays. It is less suitable when
navigation must be application state that lives in a ViewModel, reducer or external store.

## The model

Screens are serializable values. They declare which navigator they belong to, so the compiler rejects a screen
registered or navigated on the wrong navigator. Typed chains extend this check across nested navigators:

```kotlin
root.navigate(Home.then(Profile).then(Settings))
```

A `Router` declares the screens available to a navigator. Routers may be nested, and the child navigator is owned
by the entry that created it. This is how a tab can retain its stack, or a signed-in section can release every
screen beneath it when it leaves the graph. Conditional routes are reconciled with the navigator during
composition; removed routes retire rather than causing an implicit navigation.

Rendering is separate from navigation policy. A renderer receives the entries and the one supported way to
compose them. It can show one entry, several panes, or a base screen under an overlay. Projected entries let a
sheet remain owned by its feature while being drawn at the root of the UI.

Each entry has its own ViewModel store, saved state and lifecycle. It keeps them while it is owned, even when it is
parked in another tab or underneath another screen. After it is removed, it stays alive only while it or one of
its descendants is still being rendered, such as during an exit animation. It is then released once.

Back is resolved through the selected path of nested navigators. The result is a `BackAction` tied to the state
against which it was created, so a predictive-back gesture cannot later commit against different navigation
state.

## Why the back stack is not mutable application state

A mutable list is enough to describe which screen values are present. It is not enough, on its own, to maintain
the runtime guarantees above:

- Two pushes of `Article(3)` are separate entries with separate state, despite containing equal screen values.
- An entry removed from a stack may still be rendering, while an entry absent from the current UI may still be
  owned by a parked tab.
- Removing a section must also retire descendants in nested stacks, including descendants parked in other tabs.
- A feature-owned overlay may render outside its owner's place in the composition.
- Back must find the deepest navigator on the selected path and ignore navigators in parked or covered entries.

Compose Router assigns stable hierarchical entry ids and keeps one registry of ownership and hosting. These rules
would be difficult to enforce if entries could also be added to or removed from an exposed list.

Typing is a separate concern. A `MutableList<ChildScreenOf<T>>` could provide the same basic compile-time membership
check. Closing mutation buys the runtime guarantees around identity, lifetime, restoration and back; it is not
what makes the graph typed.

Navigation 3 makes the opposite trade: the application owns the back stack and composes lower-level pieces for
entry lifetime and presentation. That makes navigation easy to hoist into a ViewModel or reducer, replace
atomically, persist in a custom format, or model with a topology other than nested stacks. It also leaves the
application to define multi-stack policy, parent-child relationships, cross-stack navigation and any lifetime
rules that follow from them.

Compose Router assumes that most apps do not need to control those mechanisms. It provides stacks and retained
tabs, fixes the ownership and lifetime rules, and leaves two areas open:

- A `NavigatorPolicy` decides how navigation, pop and back change a navigator's owned entries. Stack and tab
  policies are included, and custom policies use the same shell.
- A `RouterRenderer` decides how those entries are laid out and animated.

Applications can still inspect `entries`, `selected`, `backAction` and `describe()`, and can observe scoped lifetime
events. They cannot replace the entry registry or hoist a navigator out of composition.

## When it fits

Compose Router is most useful when an app has nested feature-owned stacks, retained tabs, section-owned overlays,
or resources that must follow an entry's lifetime exactly. It also suits Compose Multiplatform apps that want the
same navigation and ownership model across Android, desktop, iOS and web.

A flat stack does not need this machinery. An application-owned list and a `when` over its last element will be
simpler. A different navigation library is also a better fit when navigation must be ordinary application state,
when the topology is not a tree, or when one screen needs to own several independent child navigators.

## Costs

- Screens must be `@Serializable`, and every module declaring them needs the serialization plugin.
- Nested navigators exist in composition. A chain targeting one that has not been composed yet waits until it is
  created, and navigation state cannot be moved into a ViewModel or reducer.
- Custom layouts require custom renderers. The library includes crossfade and predictive-back renderers, but not
  Material sheets or adaptive scaffolds.
- Renderer authors must keep an entry at one call site, use keyed rendering for repeated entries, and respect the
  hosting and back-handling rules.
- A screen covered by an overlay is `CREATED` by default so its back handlers are disabled. A custom renderer can
  keep it at `STARTED` when needed.
- The library is pre-1.0. APIs may change, and Android back and Hilt behaviour are not yet covered by instrumented
  tests.
