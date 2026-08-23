package ankers.compose.router.host

import androidx.compose.runtime.Composable
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal actual fun rootLifecycleOwner(): LifecycleOwner? = LocalLifecycleOwner.current
