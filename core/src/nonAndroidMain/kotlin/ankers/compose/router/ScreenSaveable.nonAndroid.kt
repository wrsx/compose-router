package ankers.compose.router.host

import ankers.compose.router.Screen

// no process death to survive: saved state stays in memory
internal actual fun Screen.toSaveable(): Any = this

internal actual fun screenFromSaveable(saved: Any): Screen = saved as Screen
