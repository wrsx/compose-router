package ankers.compose.router.host

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import kotlin.reflect.KClass

internal object NoReflectionOwnerPlatform : OwnerPlatform {
    override fun defaultFactory(entry: OwnerEntry<*>): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T =
            error("no reflection on this platform: create ${modelClass.simpleName} with viewModel { } or a factory")
    }

}

// the web has no qualified class names, and no process death for saved state to outlive
internal actual val KClass<*>.stableName: String? get() = simpleName
