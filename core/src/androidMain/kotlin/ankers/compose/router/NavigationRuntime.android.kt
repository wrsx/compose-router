package ankers.compose.router.navigator

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ankers.compose.router.host.AndroidOwnerPlatform

// retained across configuration changes by the activity's view model store
internal class RuntimeHolder(val runtime: NavigationRuntime) : ViewModel() {
    override fun onCleared() = runtime.clear()
}

@Composable
actual fun rememberNavigationRuntime(rootKey: String): NavigationRuntime {
    val application = LocalContext.current.applicationContext as Application
    val holder = viewModel<RuntimeHolder>(
        key = "ankers.compose.router:$rootKey",
        factory = viewModelFactory { initializer { RuntimeHolder(NavigationRuntime(AndroidOwnerPlatform(application))) } },
    )
    return holder.runtime
}
