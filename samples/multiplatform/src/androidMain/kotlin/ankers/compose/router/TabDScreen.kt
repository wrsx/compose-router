package ankers.compose.router

import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.runtime.Composable

@Composable
fun TabDScreen(
    onSignOut: () -> Unit
) {
    TabScreen("Structural Change", Icons.AutoMirrored.Outlined.ExitToApp) {
        Button(onClick = onSignOut) {
            Text("Sign Out")
        }
    }
}