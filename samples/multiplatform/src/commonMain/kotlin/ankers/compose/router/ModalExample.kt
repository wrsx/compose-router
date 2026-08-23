package ankers.compose.router

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import ankers.compose.router.navigator.Navigator

@Composable
private fun ModalContainer(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.background(Color.Black).fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.h4, color = Color.White)
        content()
    }
}

fun RouterScope<ModalStack>.modalScreens(modals: Navigator<ModalStack>) {
    screen<ModalA> {
        ModalContainer("Modal A") { Button(onClick = { modals.navigate(ModalB) }) { Text("Go to B") } }
    }
    screen<ModalB> {
        ModalContainer("Modal B") { Button(onClick = { modals.navigate(ModalC) }) { Text("Go to C") } }
    }
    screen<ModalC> {
        ModalContainer("Modal C") {
            Button(onClick = { modals.pop(2) }) { Text("Pop to A") }
        }
    }
}
