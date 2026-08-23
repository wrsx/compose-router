package ankers.compose.router.host

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import kotlin.reflect.KClass

// reflection factory: a no-arg constructor, or one taking a SavedStateHandle
internal object JvmOwnerPlatform : OwnerPlatform {
    override fun defaultFactory(entry: OwnerEntry<*>): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T {
            val java = modelClass.java
            java.constructors.firstOrNull { it.parameterTypes.contentEquals(arrayOf(SavedStateHandle::class.java)) }
                ?.let { return it.newInstance(extras.createSavedStateHandle()) as T }
            return java.getDeclaredConstructor().newInstance() as T
        }
    }

}
