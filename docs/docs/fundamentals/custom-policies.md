---
sidebar_position: 12
---

# Custom policies

A navigator is a closed *shell* — ids, ownership, hosting, events, back resolution — plus an open *policy* that
decides how `navigate`, `pop`, and back mutate the owned entries. `NavConfig.Stack` and `NavConfig.Tab` are the
shipped policies; yours plugs into the same shell.

```kotlin
/** A stack that keeps popped entries — ViewModels and all — so they can be re-entered with `forward()`. */
object ForwardStack : NavigatorPolicy<ForwardStack.State> {
    class State(val forward: List<EntryId>)          // saved as strings: policy state must be saveable
    override val key = "forward-stack"
    override fun initialState() = State(emptyList())
    override fun saveState(state: State): Any? = ArrayList(state.forward.map { it.toString() })
    override fun restoreState(saved: Any?) = State((saved as? List<String>).orEmpty().map(EntryId::parse))

    override fun navigate(scope: PolicyScope<State>, to: Screen): NavEntry<*> {
        scope.state.forward.mapNotNull(scope::owned).forEach(scope::retire)   // a new push discards the forward entries
        scope.state = State(emptyList())
        return scope.create(to).also { scope.select(it) }
    }

    override fun backStep(scope: PolicyScope<State>): BackStep? {
        val owned = scope.owned.filterNot { it.id in scope.state.forward }
        if (owned.size < 2) return null
        val outgoing = owned.last()
        return BackStep(outgoing, owned[owned.lastIndex - 1]) {
            scope.state = State(scope.state.forward + outgoing.id)   // stays owned, parked, not on the back path
            scope.select(owned[owned.lastIndex - 1])
        }
    }
    // pop, popToRoot, onRetired ...
}

fun Navigator<*>.forward() = mutate(ForwardStack) { scope ->
    val next = scope.state.forward.lastOrNull()?.let(scope::owned) ?: return@mutate
    scope.state = ForwardStack.State(scope.state.forward.dropLast(1))
    scope.select(next)
}

val navigator = rememberNavigator<Root>(ForwardStack)
val forward = navigator.stateOf(ForwardStack).forward      // the policy's private state, typed by the policy
```

The contract:

- A policy is a reusable specification. Its private state has an explicit type, an initial value, and a saveable
  form — primitives, strings, and lists of them, with entries referenced by `EntryId` string; the shell holds and
  saves it through `rememberSaveable`, so screens themselves are not saveable values.
- Mutation goes through four operations on `PolicyScope`: `create`, `retire`, `select`, `move`.
- `backStep` returns a local step; the shell wraps it into the stale-safe `BackAction`.
- Operations beyond navigate, pop and back are extension functions built on `navigator.mutate(policy) { scope -> }`
  and `navigator.stateOf(policy)`, both of which check that the navigator was created with that policy.
- `onRetired` is called when the router retires entries whose route left the graph; `onRoutes` when the graph is
  known, in declaration order.

## Contract tests

The repository's `NavigatorContractTests` is an abstract suite run once per shipped policy. It is internal test
code, not a published artifact: copy it into your test source set and subclass it with your policy to learn what
the shell expects. A `compose-router-testing` artifact that ships the suite, the headless harness and the
recording observer is the intended home for it.
