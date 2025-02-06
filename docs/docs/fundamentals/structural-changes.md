---
sidebar_position: 7
---

# Structural Changes

It is possible to perform structural changes to your navigation graph at runtime. Pass a `key` to `Router` and the graph will automatically reconcile when the key changes.

This can be particularly useful when you only want sections of your navigation graph to exist under certain conditions.

For example, you might split your navigation graph into authenticated and un-authenticate sections:

```kotlin
Router(rootNavigator, key = signedIn) {
    if (signedIn) {
        screen<SignedIn> {
            //..
        }
    } else {
        screen<SignedOut> {
            //..
        }
    }
}
```

When `signedIn` changes from `true` to `false`, The router will automatically navigate to `SignedOut` and pop the `SignedIn` screen off the stack.

:::info
All screens and content lambdas will be disposed when the section of the navigation graph they belong to is removed
:::
