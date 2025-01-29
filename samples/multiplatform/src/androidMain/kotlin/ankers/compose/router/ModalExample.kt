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
import androidx.compose.ui.text.style.TextAlign
import ankers.compose.router.navigator.Navigator

@Composable
fun ModalContainer(
    title: String,
    afterContent: @Composable () -> Unit
) {
    Column(
        Modifier
            .background(Color.Black)
            .fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.h4,
            textAlign = TextAlign.Center,
            color = Color.White
        )
        afterContent()
    }
}

fun RouterScope<ModalStack>.modalScreens(
    modalNavigator: Navigator<ModalStack>,
) {
    screen<ModalA> {
        ModalContainer(title = "Modal Screen A") {
            Button(onClick = { modalNavigator.navigate(ModalB) })
            {
                Text("Go to Modal B")
            }
        }
    }

    screen<ModalB> {
        ModalContainer(title = "Modal Screen B") {
            Button(onClick = { modalNavigator.navigate(ModalC) }) {
                Text("Go to Modal C")
            }
        }
    }

    screen<ModalC> {
        ModalContainer(title = "Modal Screen C") {
            Button(onClick = { modalNavigator.navigate(ModalD) }) {
                Text("Go to Modal D")
            }
        }
    }

    screen<ModalD> {
        ModalContainer(
            title = "Modal Screen D"
        ) {
            Button(onClick = { modalNavigator.navigate(ModalE) }) {
                Text("Go to Modal E")
            }
        }
    }

    screen<ModalE> {
        ModalContainer(
            title = "Modal Screen E"
        ) {
            Button(onClick = { modalNavigator.pop(3) }) {
                Text("Pop to B")
            }
            Button(onClick = { modalNavigator.popToRoot() }) {
                Text("Pop to root")
            }
        }
    }
}