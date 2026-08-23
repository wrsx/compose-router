package ankers.compose.router.host

import androidx.compose.runtime.Composable
import androidx.lifecycle.LifecycleOwner

/** The lifecycle that caps root entries: the activity on Android; none where the platform provides no owner. */
@Composable
internal expect fun rootLifecycleOwner(): LifecycleOwner?
