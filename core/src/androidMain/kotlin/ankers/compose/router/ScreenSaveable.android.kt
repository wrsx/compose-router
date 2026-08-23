package ankers.compose.router.host

import android.os.Parcel
import android.os.Parcelable
import ankers.compose.router.Route
import ankers.compose.router.Screen

internal actual fun Screen.toSaveable(): Any = ScreenParcel(this)

internal actual fun screenFromSaveable(saved: Any): Screen = (saved as ScreenParcel).screen

// holds the live screen; serializes it only when the system parcels the saved state (process death)
internal class ScreenParcel(val screen: Screen) : Parcelable {
    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(screen::class.java.name)
        dest.writeBundle(encodeScreen(screen))
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<ScreenParcel> {
            override fun createFromParcel(source: Parcel): ScreenParcel {
                val name = checkNotNull(source.readString())
                val state = checkNotNull(source.readBundle(ScreenParcel::class.java.classLoader))
                @Suppress("UNCHECKED_CAST")
                val route = Class.forName(name).kotlin as Route
                return ScreenParcel(decodeScreen(route, state))
            }

            override fun newArray(size: Int): Array<ScreenParcel?> = arrayOfNulls(size)
        }
    }
}
