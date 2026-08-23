package ankers.compose.router.entry

import ankers.compose.router.Screen

/** A screen instance on a navigator, with the stable [id] that keys its state, owners, and saved state. */
interface NavEntry<T : Screen> {
    val id: EntryId
    val screen: T
}
