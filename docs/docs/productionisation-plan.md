---
title: Productionisation plan
sidebar_position: 3
---

# Productionisation plan

**Status:** Implemented — see the implementation record  
**Scope:** Take Compose Router from an experimental navigation model to a library that holds up in a production
Android application: customizable rendering, entry lifetime and events, entry identity and restoration, Android
owners (ViewModel factories, `SavedStateHandle`, Hilt), overlay projection, and predictive back  
**Compatibility:** Breaking changes are intentional throughout. No compatibility layers will be retained.

## Implementation record

The plan was implemented in one pass. What shipped, and where it deviates from the decisions below:

- **M1–M4 and M5–M6 landed together** in `core/src/commonMain`, with Android-specific plumbing in `androidMain`.
  Every target compiles (Android, JVM, iOS, JS, Wasm); 73 tests run on the JVM: unit, per-policy contract suites,
  a headless composition harness, and `runComposeUiTest` on the desktop target.
- **Owners are common, not Android-only.** JetBrains' multiplatform lifecycle 2.10 and savedstate 1.3 put
  `ViewModelStore`, `LifecycleRegistry`, and `SavedStateRegistry` in common code, so `OwnerEntry` (D16) is the entry
  on every platform. Android adds `SavedStateViewModelFactory`, the `Application` extra, and the screen seeded into
  `SavedStateHandle`; the retained runtime lives in an activity-scoped `ViewModel`.
- **No movable content, measured.** Per-entry `movableContentOf` in the host was tried and rejected: on Compose
  1.10 a movable lambda created in the same recomposition that first invokes it runs its `DisposableEffect`s twice
  without disposing the first, which broke hosted tracking and would misfire inside user screens. D1 stands.
- **D4 corrected.** `rememberSaveable` keys are positional (the composite key hash includes the whole path above
  the host), so an entry re-placed at a different call site loses *all* composition state, not only `remember`.
  Only its ViewModels and saved-state registry survive. The rendering docs state this; `renderEach` keys loops.
- **Host state emission** is done by a small `HostHandle` that emits each `HostState` once, whichever effect
  observes it first; effect ordering on re-host was not reliable enough to depend on.
- **Projected exits.** `OverlayHost` owns a `Transition<Boolean>` per projection (`ProjectedEntry.transition`):
  `false → true` on projection, `true → false` when the owner drops the entry. The projection is removed when the
  transition settles at `false`, so an overlay that animates with it keeps the entry composed and `Retiring` until
  the exit ends, and one that ignores it is removed the next frame. Found on the emulator: the first cut dropped
  projections as soon as their owner did, so sheets could not animate out.
- **Back delivery** is `PlatformBackScope`: one `PredictiveBackHandler` per dispatcher owner on Android, a no-op on
  other targets. The gesture captures `backAction` at its start and commits exactly that (D19).
- **Root lifecycle capping** reads the activity lifecycle only on Android; nested entries are capped at their
  parent entry everywhere. Non-Android targets provide no default `LocalLifecycleOwner`.
- **Naming.** `NavConfig` stays as the namespace for the shipped policies (`NavConfig.Stack`, `NavConfig.Tab(...)`)
  to keep call sites unchanged; the interface is `NavigatorPolicy`. Root keys default to the root type's simple
  name, so ids read `Root/2/1`.
- **Tab first-tab rule** is implemented as `NavigatorPolicy.onRoutes`: once the graph is known, a tab navigator
  creates its first declared tab if missing and orders owned tabs by declaration.
- **Review round: eight confirmed defects.** The graph is re-declared every composition so destination content sees
  current captures (`Router` lost its `key`: routes reconcile whenever the declaration changes). A restored
  navigator reuses the entry the registry already holds for an id, so observers and hosts see one owner object per
  entry for the runtime's life. `NavigatorPolicy.navigate` returns the entry it landed on, and chain delivery uses
  it instead of searching by equality; pending chains queue per entry instead of overwriting. A navigator under a
  retiring entry rejects navigation, back and pop with a diagnostic, and an entry registered beneath an unowned
  ancestor is born unowned, so nothing can be orphaned during an exit. Projected content carries the back scope
  of the router that projected it, so it registers no second handler on the activity dispatcher. The shipped
  renderers render the pane beneath a selected projection as covered (`CREATED`, its own back handlers disarmed).
  `NavigationRuntime.clear()` releases every entry deepest-first and the non-Android runtimes clear when their
  composition leaves. Found along the way: a new navigator consumed its chain before the Router added its start
  destination; now the start comes first, a segment equal to the selected entry reuses it, and the Router delivers
  pending chains in the same pass, so the first frame is the target.
- **Review round: five medium findings.** `Router` state and its release listener are keyed on the navigator, so a
  navigator replaced at the same call site gets its own graph. A tab is its *registered route*: navigating a
  different concrete screen of a tab declared by supertype replaces that tab in place (history position kept)
  rather than adding a second one. `withEntries` rejects a selection outside the subset and drops a transition
  whose ends leave it. A `BackAction` is void once its navigator has mutated at all (a version counter replaces
  the selected/incoming check, which a tab could satisfy with stale history). An entry beneath a retiring section
  reports `Retiring` — `alive` is derived from the parent shells' owned lists, so hosts observe it.
- **Cleanup.** `parentType` and `Screen.isRoute` removed as unused. `Navigator.mutate(policy) { scope -> }` joins
  `stateOf` so custom policies can expose operations publicly; the custom-policy example keeps popped entries owned
  and saves entry ids (screens are not saveable values). Setup docs state the serialization plugin requirement;
  the contract suite is documented as internal test code pending a `compose-router-testing` artifact.
- **`LocalCurrentEntry` removed.** The entry host once provided it so a nested navigator could find its parent
  entry and so lifecycle caps could follow composition position; both now come from `ScreenScope` and the
  registry, and nothing else consumed it.
- **Simplification pass.** With owners common, the factory/observer indirection planned for an Android-only owner
  (`NavEntryFactory`, `RestorableEntry`, `RetainedStores`, a library-internal `NavigatorEvents`) was removed: the
  runtime creates `OwnerEntry`s and keeps their stores by id, and the registry drives each entry's lifecycle
  directly; `NavigatorEvents` is now purely the application's observer. `TabNavigator` and `PolicyNavigator` went
  (`Navigator<T>` plus `StackNavigator<T>`; `stateOf(policy)` reads custom policy state), nine `rememberNavigator`
  overloads became five, and chains are a flat segment list rather than a tree.
- **Data model: kotlinx.serialization, not `java.io.Serializable`.** Screens are `@Serializable`; the navigator
  snapshot keeps live objects in memory and Android wraps each in a `Parcelable` that encodes lazily in
  `writeToParcel` (class name + `encodeToSavedState`) and decodes by name on restore, with a consumer keep rule.
  Java serialization returned a *different instance* for `object` screens after process death, which broke
  every `screen == Tab` comparison; it also did not exist off the JVM. A non-serializable screen fails at
  `navigate`. `SavedStateHandle.screen<T>()` reads the seeded screen. `kotlin-reflect` is gone: start
  destinations and first tabs are decoded from an empty state on every platform.
- **One identity: `EntryId`.** Chain segments are addressed to the entry they were navigated under and dropped
  when it is released (no stale replay after a structural change, no cross-talk between navigators of one type);
  content for a retired route is kept per entry while that entry is hosted; a nested entry's lifecycle cap is its
  parent entry from the registry, wherever it is composed, so projected sheets are capped by their section; a
  released entry's saved state is removed from the `SaveableStateHolder`, which used to leak and bloat the saved
  bundle. The graph synchronizes during composition on every key change, so a renderer never hosts an entry the
  same frame retires. Projection is decided per entry from the live registration rather than a cached route set.
- **Predictive back reaches projections.** A projection's transition is a `SeekableTransitionState` that follows
  the gesture targeting its entry, animates back on cancel, and exits on commit; the inline renderer leaves the
  pane beneath untouched while a projected entry is the one being popped.
- **Not done:** Robolectric/instrumented tests for Android back delivery and Hilt (verified manually on an
  emulator instead); `sheet<T>`/`alert<T>` sugar (D9 projection ships as `projected<T>`); a desktop Escape-key
  back binding.

## Summary

The library's navigation model is sound and does not need redesign: a typed graph (`ChildScreenOf<Parent>`),
entries as plain data on snapshot-state stacks, routers that resolve entries to content inside composition, nested
routers for scoping, and typed chained navigation across navigators. Navigation 3 independently arrived at the same
shape, and two of this library's ideas — compile-time stack membership and typed cross-navigator chains — remain ahead
of it.

What the library lacks is the other half of the model: **presentation** (only the selected entry can be rendered) and
**entry lifetime** (the core knows only "in the list or not", so resources are never released, tabs lose their
ViewModels on re-selection, and rotation breaks ViewModel scoping). The Android adapter then needs the owners that
`SavedStateHandle` and Hilt require.

This plan delivers those in six milestones:

| Milestone | Delivers | Library is then… |
|---|---|---|
| M1 Customizable rendering | `RouterRenderer`, no-concurrent-hosting rule, core-resolved back dispatch, hosted tracking, cleanup | able to render sheets, alerts and multi-pane layouts |
| M2 Entry lifetime and events | retirement rule, hierarchical ownership, scoped-writer events, navigator shell/policy split, navigator tests | no longer leaking on every back press |
| M3 Entry identity and restoration | stable ids, saved destination keys, reconstruction | correct across rotation and process death |
| M4 Android owners | per-entry `Lifecycle`, `SavedStateRegistry`, default factory | usable with Hilt and `SavedStateHandle` |
| M5 Overlay projection | local sheets rendered at the root | able to express section-owned overlays |
| M6 Predictive back | gesture-driven transitions through the render scope | complete for current Android expectations |

M1–M4 are sequential and are the definition of "production-capable". M5 and M6 are additive and independent of each
other.

## Where the library stands

### Validated by Navigation 3

| Compose Router | Navigation 3 |
|---|---|
| `SnapshotStateList<NavEntry<*>>` behind the navigator | "You own the back stack": `SnapshotStateList<T>` |
| `screen<Profile> { entry -> … }` | `entryProvider { entry<Profile> { … } }` |
| Entry = screen instance + id, `ViewModelStoreOwner`, `SaveableStateProvider(id)` | `NavEntry`, `rememberViewModelStoreNavEntryDecorator`, `rememberSavedStateNavEntryDecorator` |
| Platform-neutral core with an Android wrapper | Decorators over a neutral core |

Still distinctive: `ChildScreenOf<T>` makes "this key belongs to this stack" a compile-time fact; `then()` chains
type-check a path across nested navigators and are delivered to navigators that do not exist yet; conditional
sections of the graph reconcile automatically; nested routers give graph-scoped state without a dedicated API.

### Known defects

All are addressed by a milestone below.

| Defect | Location | Milestone |
|---|---|---|
| `removeIf` mutates `navEntries` while iterating it | `navigator/StackNavigator.kt` | M1 |
| Navigating to a route absent from the live graph renders nothing, silently | `Router.kt` | M1 |
| Positional auto-navigation on structural change pushes a duplicate of a surviving screen and fails for screens with arguments | `Router.kt` | M1 |
| `locked` is never set; `screen<>(config)` is ignored; `pop(count)` throws on over-pop | core | M1 |
| `pop` / `popToRoot` never release entry resources; ViewModels are never cleared and their coroutines keep running | navigators | M2 |
| Re-selecting a tab present in history clears its ViewModels | `navigator/TabNavigator.kt` | M2 |
| Nested entries are never released when their parent section is removed, and while a parent is parked they are unreachable for cleanup — their state exists only inside the parent's composition | core | M2 |
| No tests for navigator semantics | `core` | M2 |
| Entries restored after activity recreation carry a null `@Transient viewModelStore`; ids are random | `RouterAndroid.kt`, `rememberNavigator.kt` | M3 |
| `AndroidNavEntry` is only a `ViewModelStoreOwner`: no `SavedStateHandle`, `AndroidViewModel`, or Hilt | `RouterAndroid.kt` | M4 |

## Principles

1. **The core stays small.** Presentation is policy and lives in renderers. The library ships guarantees, not a
   presentation framework.
2. **Entries are data.** The navigator never stores composable content. Anything launched is a destination.
3. **Rules are enforced where they are cheap.** Rules that renderer authors must follow are detected at the entry host
   and fail with a descriptive error.
4. **No navigation-managed movable content.** Renderers keep each entry at a stable call site; a renderer that needs
   to move one may use `movableContentOf` itself.
5. **One rule for lifetime.** An entry is released when no navigator owns it *and* nothing is rendering it or anything
   beneath it; retiring a parent retires its descendants.
6. **Events are observe-only scoped writers.** Observers receive a scoped object per navigator, entry, and host,
   compose with `plus`, and have `Discard` as the unit — the pattern used by Koala's `DataSourceEvent`. Entry
   creation is a separate, single factory; nothing the library depends on ever comes out of a writer.
7. **Overlays render at the top of the tree**, which is their honest position, and are owned by whichever navigator
   should own their lifetime.
8. **Breaking changes are fine.** The library is experimental; a clean API is worth more than compatibility.
9. **Back policy lives in core; back delivery lives in the platform.** The core resolves which navigator handles
   back from its own state; a platform binding registers that action once per back scope and only delivers events.

## Architecture

Four layers, outermost first:

- **Navigator** — owns a stack of entries and the navigation operations. Exposes an immutable `entries` snapshot.
- **Router** — one per graph. Registers routes (`screen<X> { }`), reconciles on `key`, hands the active entries to
  the renderer.
- **Renderer** — one per router. Decides which entries to display, where, and how slots transition.
- **Entry host** — runs around one entry's content each time it is rendered; it is what `render(entry)` does.
  Validates the route, tracks hosting, records back-eligibility overrides, provides saveable state and
  platform owners, drives lifecycle state, then invokes the registered content.

```text
Navigator.entries
       |
       v
Router builds RouterRenderScope
       |
       v
Renderer chooses placement (front-most last, for z-order only)
       |
       v
render(entry) — the entry host
       |
       v
Registered destination content
```

### Terminology

- **Entry** — a `NavEntry`: a screen instance plus a stable id.
- **Entry id** — a path: the parent entry's id followed by a navigator-local counter (`root/3/2`). Every descendant
  of an entry shares its prefix, matched segment by segment (D12, D15).
- **Owned** — an entry a navigator is responsible for. A stack owns its list; a tab navigator owns one entry per tab
  whether or not it is selected. `Navigator.entries` is the owned set, in navigator-defined order.
- **Selected** — the owned entry a navigator presents by default. Explicit; never assumed to be `entries.last()`.
- **History** — the tab navigator's record of selections, consulted only by its back policy. Internal.
- **Selected path** — root → its selected entry → the navigators nested under that entry → their selected entries
  → …; the path along which back is resolved (D8).
- **Back action** — what back would do right now: produced by the deepest navigator on the selected path whose
  policy has something to do, and captured as a value so predictive back commits what it previewed (D8, D19).
- **Back scope** — a region with its own platform back dispatcher: the root, and each window-based sheet or dialog.
- **Slot** — a stable call site inside a renderer where one entry is composed.
- **Hosted** — an entry host is composed for the entry. Hosting can outlive ownership while an exit transition runs;
  the entry is then **retiring**.
- **Parked** — owned but not hosted (under a pushed screen, an unselected tab, a hidden pane). Everything is retained.
- **Retired** — not owned by any navigator and not hosted. Terminal.

## Decisions

### Rendering

**D1. `ScreenDecoration` is replaced by `RouterRenderer`.** The decoration is built around one selected entry and
cannot express a different composition structure. The type, its default, and the `decoration` parameter are removed.

**D2. Renderers receive an immutable snapshot of the owned entries, plus an explicit `selected`.**
`Navigator.entries: List<NavEntry<*>>` is a snapshot of what the navigator owns, in navigator-defined order; the
mutable collection is never exposed. A stack owns its list and selects the last entry. A tab navigator owns one entry
per tab, ordered by route registration, and selects the current tab; its selection history is internal to the back
policy and is never exposed. Ownership — not history — is what renderers lay out and what the lifetime rule (D11)
refers to.

**D3. `render(entry)` is the only path to content.** The render scope exposes `render`, not the content registry.
The host owns validation, hosting, back-eligibility overrides and the child-navigator registration (D8, D10), state, and owners.

**D4. No concurrent hosting.** An entry may not be hosted from two sites at once: a second `render(entry)` while
another host for that entry is still composed fails with an error naming the entry and this rule. Hosting is recorded
and cleared in effects, so re-placing an entry within a single recomposition does not trip the rule. Keying inside
`render` cannot preserve identity across sibling reorders — a non-inline `render()` call introduces its own positional
group, so a key beneath it never meets its siblings — therefore dynamically repeated calls must be keyed at the call
site: `renderEach(entries) { render(it) }` (library-provided; keys each iteration under one parent) or an explicit
`key(entry.id) { render(entry) }`. With that, reordering siblings is a true move that keeps state and effects; moving
an entry to a different call site is a dispose-and-recreate in which only `rememberSaveable` state survives, and
inside an animated container the outgoing host lingers for the exit animation, so the rule *will* fire. Rationale: composition identity is positional,
`AnimatedContent` composes both states during a transition, and `SaveableStateHolder` requires one active site per
key. Stable placement — each entry keeps one call site; slots animate, scenes do not — is therefore the renderer
author's responsibility, documented with the reference renderer rather than guaranteed by the library. Moving an
entry between call sites without losing composition identity requires application-owned `movableContentOf` (D1).

**D5. The default renderer is a selected-entry crossfade** with identical visible behaviour to today, and it is a
*content-swapping* renderer. A slot-based list/detail reference renderer ships alongside it as the pattern to extend.

**D6. The renderer is presentation-agnostic.** No `Overlay`, `Scene`, or `Presentation` types in the core.
Applications classify destinations with their own markers. The single exception is M5's *projected* registration.

**D7. Renderers compose.** Wrappers invoke an inner renderer on a subset of entries through a library-provided
`withEntries(entries, selected, active) { inner() }` — a narrowed scope defines its own `selected` and can only lower
activity; selectors choose a renderer by condition with no library support. Predictive back
cannot be a generic wrapper over multi-pane renderers (it would render the base in two states and violate D4); it is
consumed per slot (D19).

### Back dispatch and overlays

**D8. Back policy lives in core; back delivery lives in the platform.** The core decides which navigator handles
back from its own state, and a platform binding only delivers the event. The **selected path** is root → its
selected entry → the navigators nested under that entry → their selected entries → …; the **back action** is
produced by the deepest navigator on that path whose policy has something to do — a stack pops its top entry, a tab
navigator steps its history or jumps to its first tab — and `back()` commits it. Back is not `pop()`: `pop()` remains
an explicit operation, and the tab policies are not pops. This is a function of navigator state plus three presentation registrations the host makes in effects — child
navigators (D10), promotion, and coverage — with no dependence on composition order, render order, or per-host
handlers, so it is deterministic and testable on the JVM given those registrations. The path
never branches, because an entry has at most one child navigator (D10), and it descends only through hosted entries:
a child navigator exists only while its parent entry is hosted, so a renderer that does not host the selected entry
stops resolution at that navigator, which then pops its selected entry.

A renderer may override the default at the render call. `render(entry, handlesBack = true)` promotes a non-selected
entry to the front of its navigator (a focus-driven desktop pane); at most one promotion per navigator, a second
fails descriptively like D4. `render(entry, handlesBack = false)` declares a rendered entry covered, excluding it and
its descendants from the path and, on Android, holding its lifecycle below `STARTED` so in-screen back handlers
deactivate (D14). The back path entry is therefore `promoted ?: selected-unless-covered`; when it has no child
navigator, or the child has no action, the navigator's own policy applies, and that policy always concerns the
selected entry.

The platform registers the core's action **once per back scope** — at the root router, and inside any host whose
back dispatcher differs from its parent's, since a window-based sheet or dialog has its own dispatcher and focus —
as `enabled = root.canGoBack`, `onBack = root.back()`. Predictive back is the same registration with progress (D19).
The composable `BackHandler` on `Navigator` and the `backHandlerProvider` seam are removed; navigators stay
platform-neutral and expose `backAction`, `canGoBack`, and `back()`.

Why not registration order: Android fixes a handler's priority when it registers, so "last rendered wins" decays as
soon as slots reorder or a handler is composed behind the front (a push under a sheet). Under the selected path those
cases are simply not on the path — a push behind a sheet, a reordered pane, and a retiring entry cannot take a press,
and nothing needs disabling or re-registering. Rendering the front-most entry last remains a z-order convention only.
Application-written in-screen `BackHandler`s still register with the platform in composition order and take priority
when enabled, which is intended.

**D9. Overlays are destinations rendered at the top of the tree.** Two models, chosen by who owns the lifetime:
*root destinations* (global, deep-linkable, independent of the opener — M1) and *local destinations with projected
rendering* (owned by the section that opened them, typed against it, removed with it — M5). A root destination
cannot see the opener's state; a projected one is composed at the root and shares state through lexical capture or
arguments. Overlays are pushed on stack navigators: every child of a tab navigator's type is a tab by definition, so a
sheet at that level lives on the root stack or on a stack nested inside a tab.

**D10. Nested routers.** At most one child navigator per entry, enforced: a second `rememberNavigator()` attaching
under an entry that already has a live child fails descriptively. The typed graph already assumes this — every child
under `screen<Coaching>` is a `Navigator<Coaching>`, and the chained-navigation mailbox is keyed by parent type, so two
same-typed children would race for the same pending navigation. Two independent stacks under one screen are modelled
as two entries. Back priority is resolved by D8. Resource ownership is hierarchical (D12). Chained navigation
is consumed when a child router is composed, so a renderer that omits an intermediate entry defers the remainder of
the chain; this is the contract. Chrome decisions that depend on deeper state are an application concern solved with
hoisted state.

### Lifetime and events

**D11. Entry lifetime has one rule.** An entry is released when no navigator owns it *and* no host renders it or
anything beneath it (D12). The
renderer signals "done" by no longer calling `render(entry)`. Ownership, selection, history, and hosting are four
distinct things:

| Concept | Known by | Decides |
|---|---|---|
| Owned | navigator | retention |
| Selected | navigator | the default thing to render |
| History | the tab back policy | what back does |
| Hosted | entry host | release timing |

Parked is owned and not hosted; retiring is hosted and not owned; retired is neither. An unselected tab is owned and
therefore parked — there is no tab-cache special case. The rule gives correct release on pop and retention of an
outgoing entry until its exit transition completes; those two must land together, because the default renderer
renders the outgoing entry after it has stopped being owned.

**D12. Ownership cascades by id prefix.** A nested navigator's entries are descendants of the parent entry hosting
it: retiring the parent retires them; a parked parent retains them. The library holds no tree of navigators to make
this work — a parked parent's nested state exists only inside its composition's saved bucket, so any tree registered
from composition would be gone exactly when cleanup needs it. Instead entry ids are paths (D15), and retiring an entry marks it and every descendant under its prefix unowned. An
unowned entry is released when no live host has an id under its prefix — neither it nor any hosted descendant
remains. Parked leaves are released immediately; every ancestor of a still-hosted descendant stays alive until that
descendant exits, because a projected sheet may capture its parent's state (D9) and must not watch it vanish
mid-animation. Released resources are the ViewModel store, the event writer (whose `retired()` is called), and saved
state, which is already hierarchical because nested `rememberSaveable`s live inside the parent's bucket. Prefix
matching is segment-aware: `root/1` is not a prefix of `root/10`. Signing out from Profile therefore releases the Coaching tab's three-deep parked stack. The composition-local
registration of child navigators exists for back resolution and the one-child invariant (D8, D10), not for cleanup.

**D13. Entry creation and entry events are separate channels.** Exactly one `NavEntryFactory` (the renamed
`NavEntryCreator`, now given the id) creates each entry; on Android its product is the owner object. Events are
*observe-only* writers in the scoped-writer pattern: interfaces per level — navigator, entry, host — each returned
from the level above, each with its own methods and a terminal call, and each receiving the entry the factory made.
Writers compose with `plus`, `Discard` is the unit (`Discard + other == other`), the root observer is a default
parameter of `rememberNavigator`, and partial observers delegate to `Discard`. The library never reads identity or
state back out of a writer, which is what makes fan-out safe: a combined writer is never asked to be an entry or an
owner. Events replace `NavEntryRemoveListener`, whose flat id-keyed shape forced consumers to keep a correlation
map — the map that today holds every ViewModel store forever; observers hold the entry reference instead.

**D14. Lifecycle is split.** The per-entry `LifecycleOwner` is host machinery: `SavedStateRegistryOwner` requires it,
nested entries must be capped at their parent's state, and the platform `BackHandler` and
`collectAsStateWithLifecycle` read it. Lifecycle *state* is driven by presentation facts only the renderer knows,
through one bit, `active`, whose default is `entry == selected`: the selected entry is active; transitioning, covered,
and peeked entries are not; a multi-pane renderer opts extra visible panes in explicitly, and a narrowed scope can
only lower activity. The host folds `active`, `handlesBack`, and ownership into a `HostState` — `Active`, `Inactive` (transitioning,
peeked), `Covered`, `Retiring` — delivered through `HostEvents.state`, which Android maps to lifecycle caps `RESUMED`,
`STARTED`, `CREATED`, `CREATED`; `retired()` → `DESTROYED`. An entry rendered with `handlesBack = false` (D8) is held at `CREATED`
while covered, because the platform dispatcher registers in-screen back handlers from `ON_START` (Navigation 3 keeps
entries under dialogs at `STARTED`; this library chooses `CREATED` so in-layout overlays can disarm what is beneath
them). Lifecycle is a convention screens opt into;
`LaunchedEffect` ignores it.

### Identity and platform

**D15. Entry ids are stable paths.** An id is the parent entry's id followed by a counter local to the navigator
(`root/3/2`, compared segment by segment — `root/1` is not a prefix of `root/10`): assigned at push, persisted with the stack, unique across duplicates of the same screen, identical after
recreation, and shared as a prefix by every descendant (D12). The saved form of a navigator is a snapshot — its
root key, owned `(screen, id)` entries, the policy key, the policy's private state through its `Saver`, and the
persisted id counter — never serialized `NavEntry` objects with transient fields; `(screen, id)` pairs alone could not
restore even a tab selection. Ids are namespaced by a root key (the `NavigationRoot` type by default, or an explicit
`key` on `rememberNavigator`), so two roots in one activity-scoped holder cannot collide; two live roots with the same
key fail descriptively. Restored entries are reconstructed through
`NavEntryFactory`, so writers re-attach to retained resources by id.

**D16. `AndroidNavEntry` becomes what `NavBackStackEntry` is:** `LifecycleOwner`, `ViewModelStoreOwner`,
`SavedStateRegistryOwner`, and `HasDefaultViewModelProviderFactory` with a `SavedStateViewModelFactory`. Its
`SavedStateRegistry` bundle is persisted through the entry's `SaveableStateProvider`, so the two saved-state layers
compose. `hiltViewModel()` and `SavedStateHandle` then work unchanged. Arguments reach ViewModels through a fixed
contract: the entry's default factory seeds every `SavedStateHandle` with the whole `Screen` under
`NavEntry.SCREEN_KEY` (`savedStateHandle.screen<Workouts>()` as the typed helper), and the Android binding accepts
an optional adapter `(Screen) -> Map<String, Any?>` for applications migrating ViewModels that read individual keys.

**D17. `navigate()` to a route absent from the live graph is dropped with a diagnostic.** The typed graph validates
the static graph; structural changes make the live graph smaller. The router already hands its routes to the
navigator; `navigate` checks them once known. Without this, "fail loudly on an unregistered route" turns a deep link
that arrives before auth resolves into a crash.

**D18. Positional auto-navigation is removed.** On a structural change the router retires the entries of removed
routes and reports them to the policy (D20). If the selected entry survived, nothing else happens; otherwise the
policy's reconciled selection applies (a stack selects its new top); if the navigator is left empty, the router
constructs the declared start destination — the first registered route, which must be argument-free. Navigating to a
same-index replacement route is no longer attempted: it pushed duplicates of surviving screens and could not
construct routes that take arguments.

### Predictive back

**D19. Predictive back is a transition consumed per slot.** The render scope exposes transient transition state
(outgoing and incoming entries, progress, edge); each renderer seeks its own leaving slot. The platform receives the gesture once
per back scope; the core captures `backAction` at gesture start, previews from its `outgoing`/`incoming`, routes
progress to that action's target only, and commits the captured action — a no-op if it has gone stale (D8). A
single-pane
`PredictiveBackRenderer` ships as the common case. The stack changes only on commit. Requires D11.

### Navigators

**D20. A navigator is a shell plus a policy.** The shell is library-owned and closed: owned entries and ids,
`selected`, child registration, events, hosting, back resolution. The *policy* decides how `navigate`, `pop`, and
`back` mutate owned entries, selection, and private history, and is open: `NavConfig` becomes a `NavigatorPolicy`
with `Stack` and `Tab` as the shipped implementations. Applications can add policies the shipped two do not cover — a
forward-capable history for desktop and web, single-top or replace semantics, a no-back flow — without touching
lifetime, hosting, or back dispatch. `currentScreen` (a duplicate of `selected?.screen`) and `removeIf` (a
router-internal operation) leave the public API.

The policy contract:

- **A policy is a reusable specification, not a stateful instance.** It declares an explicit private state type `S`
  (tab history, a forward list), an `initial()` value, and a `Saver<S>`. The shell owns the `S` value per navigator
  and saves it (D15); policies hold no mutable fields.
- **Policies mutate through four shell operations only:** `create(screen): NavEntry` (the shell assigns the id and
  runs the factory and events), `retire(entry)`, `select(entry)`, and `move(entry, toIndex)`. Policies never touch
  hosting, ids, children, or events.
- **Route removal is reported, not discovered.** When the router drops a route (structural change), the shell retires
  the affected entries and then calls `policy.onRetired(state, entries)` so the policy reconciles its history and
  selection; the shell applies the policy's resulting selection (D18).
- **Back is a local step the shell wraps.** `policy.backStep(state, owned, selected)` returns `(outgoing, incoming,
  apply)` or null; the shell wraps it into the stale-safe `BackAction` (target, staleness check, commit).
- **No runtime capability discovery.** Every policy gives meaningful semantics to the common surface — `navigate`,
  `navigate(chain)`, `pop(count)`, `popToRoot()`, `back()`, `canGoBack`, `backAction`, `entries`, `selected`,
  `isEmpty` — a `Tab` policy's `pop` steps history and its `popToRoot` returns to the first tab. Operations only some
  policies support live on the policy's *public navigator type*: `rememberNavigator(Stack)` returns a
  `StackNavigator<T>` with `popTo<Route>(inclusive)`, `replace(screen)`, and `navigate(to, singleTop = true)`; a
  custom policy declares its own. Capability is a compile-time fact — never an `Unsupported` result, never a silent
  no-op.

### Deferred

- Start destinations are constructed by reflection on Android (`primaryConstructor`/`objectInstance`). This works
  and stays; an explicit `start = Home` form would remove `kotlin-reflect` and enable non-Android `Router` bindings.
- Deep-link URI parsing stays application-side, as in Navigation 3; chained navigation is the primitive.
- Reusable renderers and an explicit front override are added when repeated application patterns justify them.

## Scenarios

Each scenario: what the user sees, how it is modelled, how it is rendered, how back and ownership behave.

### Single-pane stack

Screens push and pop with a crossfade. `[A, B, C]` on one navigator; `CrossfadeRenderer` renders `selected`; back
pops. The outgoing entry keeps rendering until the animation ends — retiring, still hosted, resources retained — then
is released (D11). M1 behaviour; release correct from M2.

### Global bottom sheets and alerts (root destinations)

A sheet rises above the whole app; the screen beneath does not move, scroll, or reload; back or a swipe dismisses it
and nothing beneath has changed. `ConnectDevice : ChildScreenOf<Root>, RootSheet` is pushed on the root navigator
from anywhere; arguments are screen fields; several overlays may stack as the trailing run of entries. The root
renderer renders the base first and the overlays last inside application-chosen containers. Back: the sheet is the root's selected entry, so the selected path
ends at the root, which can pop (D8). Owner: the root. M1.

### Local bottom sheets and alerts (projected rendering)

Identical on screen; different owner. `Filters : ChildScreenOf<Workouts>` is pushed on the *local* navigator and
registered as projected. The local renderer hands `{ render(entry) }` to the root `OverlayHost`; the entry is
composed once, at the root, with its own owners. Back pops the local stack: the projected sheet is the local navigator's selected entry, so the
selected path ends there (D8). Removing `Workouts` removes the projection. Composition locals inside are the root's; a
projection disappears if its owner is no longer rendered. M5.

### Master/detail

Wide window: list left, detail right. Tapping an item animates the detail in; the list does not move and keeps its
scroll position and in-progress work. Phone: the same navigation is a push. Widening or unfolding while reading slides
the list in and leaves the detail untouched, unsaved input included. Back closes the detail on a wide window, pops to
the list on a phone. A shared element can travel from row to header.

`[Inbox, Conversation(7)]` on one navigator; the layout is derived from entries and window size. A slot-based
renderer with two permanent slots whose visibility and weights change; neither entry changes slot, so neither is
recreated (see the reference renderer). Keying an `AnimatedContent` on the whole layout composes the list twice and
trips D4. Back: the detail is `selected`, so the selected path runs through it — a sub-stack inside it pops first, then the
parent closes the detail, then the list's sub-stack is reachable (D8). M1.

### Nested routers and tabs

Tabs keep their own history; nested flows push and pop inside a tab; back walks out of the deepest flow first.
Switching away from a tab parks it — scroll, data, input, and nested flow intact — and switching back restores it;
signing out retires the whole signed-in section including every nested entry, even those parked in tabs you are not
looking at (D12). Because `entries` is the owned set of
tabs, a keep-alive tab renderer is one line — `renderEach(entries) { AnimatedVisibility(it == selected) { render(it) } }` —
giving no recomposition cost on switch with nothing hosted twice.
Back: D8. Ownership: D11–D12. M1 for rendering; correct retention from M2.

### Structural changes

Sections appear and disappear with application state; the displaced screen animates out. Registered content for a
removed route is retained while any entry of that route is hosted and released when the last host disposes, so "fail
loudly on an unregistered route" coexists with exit animations. In a multi-pane renderer, removing a pane's route
removes that pane. Positional auto-navigation is gone (D18): the surviving top entry is selected, or the declared
start destination is constructed when the navigator is empty. M1.

### Predictive back

Dragging from the edge reveals the previous screen, or slides a detail pane out while the list stays, with the
gesture; releasing commits or cancels. The platform delivers the gesture once; the core captures the back action from the selected path (D8); the renderer
seeks its leaving slot; the peeked entry is rendered `active = false`, so `RESUMED`-gated work does not fire on a
peek. M6.

### Rotation and process death

Rotate the device mid-flow and every screen keeps its ViewModels, saved state, and nested stacks; return after process
death and the stack, arguments, and `rememberSaveable` state are restored with fresh ViewModels that receive their
`SavedStateHandle`. M3–M4.

## Feature: customizable rendering (M1)

### Render scope

```kotlin
@Stable
class RouterRenderScope internal constructor(
    val entries: List<NavEntry<*>>,          // owned, in navigator-defined order
    val selected: NavEntry<*>?,              // from the navigator: last for a stack, current tab for tabs
    private val activeCap: Boolean,          // a narrowed scope can lower activity, never raise it
    private val renderEntry: @Composable (NavEntry<*>, Boolean, Boolean?) -> Unit,
) {
    /**
     * `active` defaults to "is the selected entry": the selected entry is active; transitioning, covered, and
     * peeked entries are not. Multi-pane renderers opt extra visible panes in explicitly (D14).
     * `handlesBack`: null = default (the selected entry); true = promote this entry; false = covered (D8).
     */
    @Composable
    fun render(entry: NavEntry<*>, active: Boolean = entry == selected, handlesBack: Boolean? = null) {
        renderEntry(entry, active && activeCap, handlesBack)
    }

    /** Keys each iteration on `entry.id` under one parent, so reorders are moves (D4). Use for any repeated rendering. */
    @Composable
    fun renderEach(entries: List<NavEntry<*>>, content: @Composable (NavEntry<*>) -> Unit = { render(it) }) {
        for (entry in entries) key(entry.id) { content(entry) }
    }

    /**
     * Runs `content` in a narrowed scope. `entries` must be a subset of this scope's entries. `selected` defaults to
     * this scope's selected entry if it is in the subset, otherwise the subset's last entry — a narrowed scope never
     * inherits a `selected` it does not contain. `active = false` makes everything rendered inside inactive,
     * whatever the inner renderer asks for.
     */
    @Composable
    fun withEntries(
        entries: List<NavEntry<*>>,
        selected: NavEntry<*>? = if (this.selected in entries) this.selected else entries.lastOrNull(),
        active: Boolean = true,
        content: @Composable RouterRenderScope.() -> Unit,
    )
}

typealias RouterRenderer = @Composable RouterRenderScope.() -> Unit
```

The scope deliberately exposes no navigation mutation. A renderer captures the relevant `Navigator` when it needs to
dismiss something. `render(entry)` is the entry host; every call:

1. Is not self-keying — a non-inline call cannot key its own siblings. Repeated rendering goes through `renderEach`,
   which applies `key(entry.id)` per iteration under one parent (D4).
2. Accepts only entries this router's navigator owns or that are retiring here (the outgoing entry of a transition,
   still hosted since before it left the stack); a foreign entry or one already released fails descriptively. Then
   resolves content by `entry.screen::class`, failing descriptively if the route is not registered.
3. Fails descriptively if another host for the entry is still composed (D4).
4. Records the entry as hosted — in an effect, so a same-recomposition re-placement sees the old host cleared
   first — until the host disposes.
5. Records the entry's `handlesBack` override and provides `LocalCurrentEntry`, under which the entry's single child
   navigator registers for back resolution; a second child fails descriptively (D8, D10).
6. Provides entry-scoped saveable state and platform owners; sets lifecycle state from `active` (D14, from M4).
7. Invokes the registered destination content with the original `NavEntry`.

### Router

```kotlin
@Composable
fun <T : Screen> Router(
    navigator: Navigator<T>,
    key: Any = Unit,
    renderer: RouterRenderer = CrossfadeRenderer,
    config: RouterScope<T>.() -> Unit,
)
```

The trailing lambda remains the route graph; declarations that never passed `decoration` are unchanged.

### Default renderer

```kotlin
val CrossfadeRenderer: RouterRenderer = {
    selected?.let { target ->
        Crossfade(targetState = target) { visibleEntry ->
            render(visibleEntry)
        }
    }
}
```

### Back action

```kotlin
/** What back would do right now: a value produced by a navigator's own policy. */
interface BackAction {
    val target: Navigator<*>
    val outgoing: NavEntry<*>      // leaves the front on commit
    val incoming: NavEntry<*>      // becomes selected on commit
    fun commit()                   // no-op if stale: target.selected is no longer `outgoing`
}

abstract class Navigator<T : Screen> {
    internal val children = mutableStateMapOf<EntryId, Navigator<*>>()   // child navigators by hosting entry (D10)
    internal var promoted by mutableStateOf<EntryId?>(null)                // render(entry, handlesBack = true); at most one
    internal val covered = mutableStateSetOf<EntryId>()                    // render(entry, handlesBack = false)
    internal fun ownBackAction(): BackAction?   // wraps policy.backStep(state, owned, selected) stale-safely (D20)

    /** The entry back descends through: a promoted entry, else the selected entry unless it is covered. */
    val backPathEntry: NavEntry<*>?
        get() = promoted?.let { id -> entries.firstOrNull { it.id == id } }
            ?: selected?.takeUnless { it.id in covered }

    /** Deepest navigator on the back path with an action. Derived state. */
    val backAction: BackAction?
        get() = backPathEntry?.let { children[it.id] }?.backAction ?: ownBackAction()
    val canGoBack: Boolean get() = backAction != null
    fun back() = backAction?.commit()
}

// Policies: a stack with more than one entry pops its top; a BackPress.Stack tab steps its history;
// a BackPress.First tab not on its first tab jumps there. No policy removes an owned tab.

// Registration, in rememberNavigatorImpl — driven by composition, stored in navigator state
val parentEntry = LocalCurrentEntry.current
DisposableEffect(child, parentEntry.id) {
    check(parent.children.put(parentEntry.id, child) == null) { "Entry ${parentEntry.id} already has a child navigator" }
    onDispose { parent.children.remove(parentEntry.id) }
}

// Android binding — once per back scope (root router, and inside each window-based container)
BackHandler(enabled = root.canGoBack) { root.back() }
```

Each navigator knows only its direct children; the recursion composes that into "deepest on the back path" with no
composition traversal. Promotion and coverage are registered by the host in effects, keyed by entry id and cleared on
dispose; a second promotion fails descriptively. Coverage removes an entry and its subtree from the path; promotion
replaces `selected` as the descent point. When the path entry has no child navigator, or the child has no action, the
navigator's own policy applies — and that policy always concerns the selected entry. Registration and removal share one effect, and Compose runs disposals before new effects
within a recomposition, so a structural change that disposes one child and composes another under the same entry
never trips the invariant. A retiring entry's child stays registered during its exit animation but is never on the
selected path. Back is not `pop()`: `pop()` remains an explicit navigation operation.

### Example: application-owned overlays

```kotlin
interface RootOverlay
interface RootSheet : RootOverlay
interface RootAlert : RootOverlay

val RootRenderer: RouterRenderer = {
    val overlays = entries.takeLastWhile { it.screen is RootOverlay }
    val base = entries.dropLast(overlays.size).lastOrNull()

    Box {
        base?.let { render(it) }         // beneath; not selected, so inactive (STARTED) by default. Add
                                         // handlesBack = false only for in-layout containers; windows are immune

        renderEach(overlays) { entry ->                        // keyed per entry (D4); last for z-order, back resolves from selected (D8)
            when (entry.screen) {
                is RootSheet -> ModalBottomSheet(onDismissRequest = { rootNavigator.pop() }) { render(entry) }
                is RootAlert -> AlertDialog(onDismissRequest = { rootNavigator.pop() }, content = { render(entry) })
            }
        }
    }
}
```

Application policy, not library policy. Another application could render the same entries as panes, windows, or
custom layers.

### Reference: slot-based list/detail renderer

```kotlin
val ListDetailRenderer: RouterRenderer = {
    val wide = LocalWindowInfo.current.containerSize.width > threshold
    val list = entries.firstOrNull()
    val detail = entries.getOrNull(1)

    Row {
        AnimatedVisibility(
            visible = wide || detail == null,
            modifier = if (wide) Modifier.weight(0.4f) else Modifier.weight(1f),
        ) {
            list?.let { render(it, active = wide || detail == null) }   // slot 1: never moves; a visible pane stays active
        }
        AnimatedContent(
            targetState = detail,
            modifier = if (wide) Modifier.weight(0.6f) else Modifier.weight(1f),
        ) { d ->
            d?.let { render(it) }             // slot 2: never moves
        }
    }
}
```

Wide window, tap an item: slot 2 animates the detail in; slot 1 is untouched. Phone: slot 1 animates out while slot 2
animates in. Widen or unfold while reading: slot 1 becomes visible; slot 2's call site is unchanged, so the detail
keeps everything. Widths are modifiers, which do not affect composition identity.

### Composing renderers

```kotlin
val OverlayRenderer: (RouterRenderer) -> RouterRenderer = { inner -> {
    val (overlays, rest) = entries.partition { it.screen is RootOverlay }
    Box {
        // The narrowed scope selects the base and is inactive while a sheet is up; if it inherited the outer
        // `selected` (the sheet), inner()'s crossfade would render the sheet a second time and trip D4.
        withEntries(rest, selected = rest.lastOrNull(), active = overlays.isEmpty()) { inner() }
        renderEach(overlays) { ModalBottomSheet { render(it) } }   // keyed per entry; selected → active by default
    }
} }

val AdaptiveRenderer: RouterRenderer = { if (isWide()) ListDetailRenderer() else CrossfadeRenderer() }
```

Wrappers for additive concerns; selectors for alternatives. A wrapper can only narrow: the subset must be drawn from
the scope's own entries, the narrowed scope defines its own `selected`, and it can lower activity but not raise it.
D4 makes a bad composition fail loudly.

## Feature: entry lifetime and events (M2)

### The rule

Released when not owned *and* nothing under its prefix is hosted. Retiring an entry marks its whole prefix unowned;
an unowned entry is released when no live host — host counts are kept in the retained holder, keyed by id — has an
id under its prefix: parked leaves immediately, ancestors of a still-hosted descendant after that descendant exits.
Descendants are found by segment-aware prefix rather than by walking composition, so a parked subtree is released
with its parent. The library calls `retired()` on every released writer.

### Events

```kotlin
/** Exactly one per navigator tree, platform-supplied. Returns the entry — on Android, the owner object. */
fun interface NavEntryFactory {
    fun create(screen: Screen, id: EntryId): NavEntry<*>
}

/** Observe-only. Receives the entry the factory made; the library never reads anything back out of a writer. */
interface NavigatorEvents {
    /** An entry was created by the factory: pushed, or reconstructed after process death. */
    fun entry(entry: NavEntry<*>): EntryEvents
    operator fun plus(other: NavigatorEvents): NavigatorEvents = CombinedNavigatorEvents(this, other)
    object Discard : NavigatorEvents {
        override fun entry(entry: NavEntry<*>) = EntryEvents.Discard
        override fun plus(other: NavigatorEvents) = other
    }
}

interface EntryEvents {
    /** An entry host began rendering this entry. */
    fun hosted(): HostEvents
    /** A nested navigator was created under this entry. */
    fun child(): NavigatorEvents
    /** No longer owned by any navigator and no longer hosted. Terminal. */
    fun retired()
    operator fun plus(other: EntryEvents): EntryEvents
    object Discard : EntryEvents
}

/** Folded by the host from `active`, `handlesBack`, and ownership. Android maps to lifecycle caps (D14). */
enum class HostState { Active, Inactive, Covered, Retiring }   // RESUMED, STARTED, CREATED, CREATED

interface HostEvents {
    fun state(state: HostState)
    /** Terminal for this host. */
    fun unhosted()
}

@Composable
fun <T : NavigationRoot> rememberNavigator(
    navConfig: NavConfig = NavConfig.Stack,
    events: NavigatorEvents = NavigatorEvents.Discard,   // combined with the platform binding's own observer
): Navigator<T>
```

The library guarantees the terminal calls, including on structural removal. The platform binding supplies the factory
and its own observer; application observers are combined with it — the same composition Koala uses when it joins the
data source's observer with the caller's:

```kotlin
val analytics = object : NavigatorEvents by NavigatorEvents.Discard {
    override fun entry(entry: NavEntry<*>) = object : EntryEvents by EntryEvents.Discard {
        override fun retired() = log("left ${entry.screen::class.simpleName}")
    }
}
rememberNavigator<Root>(events = analytics)   // the binding adds its own observer: androidEvents + analytics
```

A DI scope per screen has the same shape: open in `entry()`, child scope in `child()`, close in `retired()`.

### Tab semantics

The tab navigator is restructured around the contract in D2: one owned list (one entry per tab, in route registration
order, saved with the navigator) and a private history of entry ids for the back policy; `selected` is the entry whose
id is last in history. `navigate(tab)` ensures the tab is owned and appends to history; it never removes anything, so
re-selecting a tab cannot release its ViewModels. "First tab" is `entries.first()`, which removes the navigator's
dependence on the router's route list. A tab stops being owned only when its route leaves the graph or the
navigator's own entry retires. `pop()` on a tab navigator steps history and never affects ownership; the two `BackPress` policies are expressed as
`BackAction`s over history alone (D8).

### Navigator tests

Port the headless harness from the predecessor project (`Recomposer` + `UnitApplier` + `TestMonotonicFrameClock`)
into `core/src/jvmTest`. Cover: push/pop/popToRoot; tab `BackPress.Stack` and `BackPress.First`; chained navigation
across nested navigators including deferred consumption; structural-change removal; `removeIf` with multiple matches;
the retirement rule; cascade through nested navigators.

## Feature: entry identity and restoration (M3)

- `EntryId` is a path — the parent entry's id followed by a counter persisted with the navigator — so it is unique
  across duplicates, stable across recreation, and shared as a prefix by every descendant (D12).
- The navigator saver stores a snapshot — root key, owned `(screen, id)` entries, policy key, policy state through
  the policy's `Saver`, and the id counter — enough to restore tab selection and any custom policy's history.
  `Screen` remains `Serializable` on JVM/Android; other serialization strategies are out of scope.
- On restore, entries are reconstructed through `NavEntryFactory.create(screen, id)`, so the platform entry
  re-attaches to its retained store by id, and observers are notified through `NavigatorEvents.entry(entry)` exactly
  as for a pushed entry. Retained stores live in an activity-scoped holder, as today, and are released by `retired()`
  rather than never.
- Nested stacks are saved inside their parent entry's saveable state, as today; with prefixed ids, the parent's saved
  bucket and the retained holder's prefix describe the same subtree, which is what makes cascade and restoration
  agree.

## Feature: Android owners (M4)

`AndroidNavEntry` implements `LifecycleOwner` (`LifecycleRegistry`), `ViewModelStoreOwner`,
`SavedStateRegistryOwner` (`SavedStateRegistryController`, `enableSavedStateHandles()`), and
`HasDefaultViewModelProviderFactory` (`SavedStateViewModelFactory`). It is the product of the Android
`NavEntryFactory`; the binding's own observer drives its lifecycle and releases it, holding the entry reference rather
than an id:

```kotlin
/** Product of the Android NavEntryFactory. */
class AndroidEntry(id: EntryId, screen: Screen, retained: RetainedStores) : NavEntry,
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner, HasDefaultViewModelProviderFactory {
    override val viewModelStore = retained.storeFor(id)
    fun cap(state: HostState) = lifecycle.moveTo(
        when (state) { Active -> RESUMED; Inactive -> STARTED; Covered, Retiring -> CREATED }
    )
    fun release() { viewModelStore.clear(); retained.remove(id); lifecycle.moveTo(DESTROYED) }
}

/** The Android binding's observer, combined with application observers by the binding. */
class AndroidNavigatorEvents(private val retained: RetainedStores) : NavigatorEvents {
    override fun entry(entry: NavEntry<*>) = object : EntryEvents by EntryEvents.Discard {
        private val android = entry as AndroidEntry
        override fun hosted() = object : HostEvents {
            override fun state(state: HostState) = android.cap(state)
            override fun unhosted() {}
        }
        override fun child() = AndroidNavigatorEvents(retained)
        override fun retired() = android.release()
    }
}
```

`RetainedStores.retirePrefix(id)` marks every entry under the given id unowned and releases each unowned entry as
soon as no live host has an id under its prefix — parked leaves at once, ancestors of a still-hosted descendant when
that descendant's host disposes (D12). Host counts live in the holder, keyed by id, so the check is global. States: `CREATED` on creation or restore; while hosted, the cap from `HostState` (`Active` → `RESUMED`, `Inactive` → `STARTED`, `Covered` and `Retiring` →
`CREATED`), further capped at the parent entry's state read from `LocalLifecycleOwner`; `DESTROYED` on retired. The entry's
`SavedStateRegistry` bundle is registered as a provider inside its `SaveableStateProvider`. With this, `viewModel()`,
`hiltViewModel()`, `SavedStateHandle`, `AndroidViewModel`, lifecycle-aware `BackHandler`s, and
`collectAsStateWithLifecycle` all behave as they do under Navigation 2 and 3.

## Feature: overlay projection (M5)

```kotlin
@Composable
fun OverlayHost(content: @Composable (projected: List<ProjectedEntry>) -> Unit)

inline fun <reified C : ChildScreenOf<T>> RouterScope<T>.projected(
    noinline content: @Composable ScreenScope<C>.(NavEntry<C>) -> Unit,
)

@Composable
fun RouterRenderScope.projectToRoot(entry: NavEntry<*>)
```

`OverlayHost` receives projected entries in registration order and chooses containers by the application's own
markers. `projectToRoot` composes `render(entry)` inside the nearest host and nothing in place. `CrossfadeRenderer`
projects such entries automatically and renders the last non-projected entry in place, so an application on the
default renderer needs only `projected<T>` and an `OverlayHost`. `sheet<T>`/`alert<T>` sugar is decided from the
sample.

## Feature: predictive back (M6)

The render scope gains `transition: BackTransition?` — `outgoing`, `incoming`, `progress`, and `edge` — built from a
captured `BackAction`. The Android binding registers one `PredictiveBackHandler` per back scope, enabled by
`canGoBack`; at gesture start it captures `root.backAction` once, routes progress into that action's target render
scope only, and on release commits *that* action (D8). `commit()` is a no-op if the action is stale — the target's
selected entry is no longer `outgoing` — so a state change mid-gesture can never redirect the commit. Cancel clears
the transition. `PredictiveBackRenderer` drives a `SeekableTransitionState` between `outgoing` and `incoming` for
single-pane apps; slot renderers seek their own leaving slot. The stack is unchanged until commit.

## Milestones

### M1: customizable rendering

1. **Expose owned entries and explicit selection.** `Navigator.entries` as an immutable snapshot of the owned set in
   navigator-defined order; `selected` explicit (last entry for a stack, current tab for tabs); derive
   `currentScreen` and `isEmpty`. The tab navigator exposes its owned tabs in registration order rather than its
   history. Fix `StackNavigator.removeIf` (filter, then remove). Files: `navigator/*.kt`.
2. **Renderer types.** `RouterRenderer.kt` with `RouterRenderScope`, `RouterRenderer`, `CrossfadeRenderer`,
   `withEntries`. Delete `ScreenDecoration`, `CrossfadeDecoration`, the `decoration` parameter, the dead `locked`
   state, and the ignored `config` parameter on `screen<>`. Clamp `pop(count)`.
3. **`RouterInternal` and back resolution.** Build the scope; implement the entry host steps 1–5 and 7 (step 6's
   lifecycle part arrives in M4); retain content while hosted; hand live routes to the navigator and drop unregistered
   `navigate()` calls with a diagnostic (D17); remove positional auto-navigation (D18). Register the child navigator under
   `LocalCurrentEntry` for back resolution, enforcing one child per entry (D10); compute `backAction`, `canGoBack`,
   and `back()` from the selected path, each navigator's own policy, and `handlesBack` overrides (D8). Files: `Router.kt`, `navigator/rememberNavigator.kt`, `navigator/Navigator.kt`.
4. **Android host and back delivery.** Move `LocalViewModelStoreOwner` and `SaveableStateProvider` into the host
   callback; key the host on `entry.id`. Register the core's back action once per back scope —
   `BackHandler(enabled = root.canGoBack) { root.back() }` at the root router and inside any host whose
   `LocalOnBackPressedDispatcherOwner` differs from its parent's. Remove the composable `BackHandler` from `Navigator`
   and the `backHandlerProvider` seam. Files: `RouterAndroid.kt`, `rememberNavigator.kt`, `navigator/Navigator.kt`.
5. **Samples and docs.** Default rendering unchanged; root sheet and alert; overlay from a deeply nested destination
   with back dismissing it; base composed beneath an overlay; list/detail exercised by resizing the desktop sample;
   the stable-placement rule documented beside the reference renderer; render order is a z-order convention and
   plays no part in back dispatch.
6. **Tests.** Navigator: entries order, `selected`, immutability, tab `entries` are the owned tabs in registration order with
   `selected` the current tab, duplicates distinct, removal
   updates including multi-entry `removeIf`. Compose: default renders selected; custom renderer renders two entries;
   correct content per entry; distinct `ViewModelStoreOwner`s; `rememberSaveable` keyed by id; unregistered route
   fails descriptively; same entry from two sites fails with the concurrent-hosting error; pushing a second entry does not dispose the
   first's composition; narrow↔wide preserves the detail's remembered state; back with a root overlay pops the
   overlay; a push behind the overlay does not change the resolved back action; list/detail closes the detail before the list's
   sub-stack; reordering slots does not change the resolved back action; a retiring entry is never the target; a
   `BackPress.First` tab reaches its first tab through back while `pop()` steps history; a window-hosted
   sheet receives back; a tab back policy is reached through a nested stack; `handlesBack = true` promotes a
   non-selected pane and two promotions fail descriptively; a second child navigator under one entry fails
   descriptively; exit of a structurally
   removed route does not throw; unregistered `navigate()` is dropped; nested routers still install; a narrowed scope never renders the selected
   entry it excludes; rendering a foreign or released entry fails descriptively; reordering `[A, B, C] → [B, A, C]` through `renderEach`
   keeps each entry's `remember` state and does not re-run its `DisposableEffect`.
7. **Verify targets.** `:core:jvmTest`, `:core:testDebugUnitTest`, `:samples:multiplatform:assembleDebug`; compile
   iOS, desktop, JS, and Wasm source sets.

### M2: entry lifetime and events

1. Hosted tracking (host counts in the retained holder, keyed by id) drives retirement. Retiring marks the prefix
   unowned; an unowned entry is released when no live host has an id under its prefix (D12), so parked leaves go at
   once, a hosted descendant finishes its exit transition first, and its ancestors outlive it.
2. `NavEntryFactory` (renamed `NavEntryCreator`, given the id) remains the single creation channel.
   `NavigatorEvents`/`EntryEvents`/`HostEvents` with `plus`, `Discard`, delegation, receiving the created entry;
   `rememberNavigator(events = …)` combined with the platform observer. Remove `NavEntryRemoveListener`.
3. Navigators stop calling removal listeners. Split `Navigator` into shell and policy (D20); implement `Stack` and
   `Tab` as policies, the tab one per "Tab semantics": one owned list plus a private history of ids; re-select never
   removes; first tab is `entries.first()`. Trim the public surface per D20; `StackNavigator<T>` gains `popTo`,
   `replace`, and single-top; policies report retirements through `onRetired`.
4. Android binding supplies the factory and `AndroidNavigatorEvents`; `retired()` releases the entry's store and
   removes it from the holder.
5. Navigator-semantics test suite on the ported harness; retirement and cascade tests; "exit animation still shows
   content and the store is cleared afterwards"; signing out from a tab other than the one holding a deep stack
   releases that stack's ViewModels; a hosted descendant of a retiring section (a projected sheet animating out) is
   released only after its exit transition, and its parent's ViewModels survive until then.

### M3: entry identity and restoration

1. `EntryId` as a root-namespaced, parent-prefixed path from a persisted per-navigator counter; the navigator saver
   stores the snapshot (root key, owned entries, policy key and state, counter).
2. Reconstruction through `NavigatorEvents.entry` on restore; writer re-attaches by id.
3. Tests: rotation keeps ViewModels for every entry including nested; process death restores stack, arguments, and
   `rememberSaveable` state; duplicates restore as distinct entries.

### M4: Android owners

1. `AndroidEntry` as described; `SavedStateRegistry` persisted through `SaveableStateProvider`.
2. Lifecycle caps from `HostState`, further capped at the parent entry's state.
3. Tests: `hiltViewModel()` and `SavedStateHandle` receive arguments; `LifecycleResumeEffect` does not fire for an
   inactive render; nested entries never exceed the parent's state; app-level `BackHandler` in a retiring entry is
   inert; the outgoing entry of a push transition and the base under an overlay are `STARTED`; the list pane is
   `RESUMED` when wide.

### M5: overlay projection

`OverlayHost` and registry; `projectToRoot`; `projected<T>`; default-renderer awareness; sugar decision. Tests: back
pops a projected sheet before the stack beneath; projection removed when the owner is disposed; projected entry hosted
exactly once; exit transition completes; chained navigation into a projected destination.

### M6: predictive back

`BackTransition` in the scope, built from a captured `BackAction`; one `PredictiveBackHandler` per back scope in the
Android binding, progress routed by the core to the action's target render scope; `PredictiveBackRenderer`; reference
list/detail renderer seeks its detail slot. Tests: preview renders both entries with the incoming inactive; cancel
leaves the stack unchanged; commit applies the captured action once; a state change mid-gesture does not redirect
the commit; progress reaches only the target router's renderer.

## Acceptance criteria

The library is production-capable (M1–M4) when:

- Every destination is composed through `render(entry)` with entry-scoped owners, and an entry rendered from two
  sites fails with a diagnostic naming the no-concurrent-hosting rule.
- A renderer can compose zero, one, or many active entries; a root renderer displays base content beneath a sheet or
  alert; the list/detail reference renderer preserves both panes across layout changes.
- Back is resolved by the core from the selected path; a root overlay (the selected entry) owns back regardless of
  render order or of anything composed behind it; a retiring entry is never the target; the platform registers the
  action once per back scope.
- No ViewModel store or saveable state outlives its entry's retirement; exit animations complete with live content;
  parked tabs and their nested flows are retained and re-selecting a tab releases nothing; retiring a section retires
  every descendant, including those parked in other tabs (released by id prefix).
- Rotation and process death restore the stack, arguments, `rememberSaveable` state, and (for rotation) ViewModels,
  for every level of nesting.
- `hiltViewModel()` and `SavedStateHandle` work without application-side adaptation.
- `navigate()` to a route absent from the live graph is dropped with a diagnostic rather than rendering nothing or
  crashing.
- Navigator semantics and the above behaviours are covered by tests on the JVM without an emulator.
- No movable-content machinery or presentation taxonomy beyond `projected` exists in the core.
