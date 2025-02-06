# Android

### ViewModel

Screen instances (`NavEntry`) are ViewModelStoreOwners. Requesting a ViewModel via `viewModel()` will scope it to the navigation entry.

### State Restoration

Saveable state is saved when screens are disposed and restored when they re-enter the composition.

:::warning
Hilt is not yet supported.
:::
