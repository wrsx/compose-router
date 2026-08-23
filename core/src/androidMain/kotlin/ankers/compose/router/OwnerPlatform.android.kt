package ankers.compose.router.host

import android.app.Application
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.MutableCreationExtras

internal class AndroidOwnerPlatform(private val application: Application) : OwnerPlatform {
    override fun defaultFactory(entry: OwnerEntry<*>): ViewModelProvider.Factory =
        SavedStateViewModelFactory(application, entry, null)

    override fun extras(entry: OwnerEntry<*>, extras: MutableCreationExtras) {
        extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] = application
    }
}
