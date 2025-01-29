package ankers.compose.router

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ankers.compose.router.navigator.NavConfig
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    val rootNavigator = rememberNavigator<Root>()
    var signedIn by remember { mutableStateOf(true) }

    MaterialTheme {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            GlobalHeader()
            Router(rootNavigator, key = signedIn) {
                if (signedIn) {
                    screen<SignedIn> {
                        TabsExample(
                            rootNavigator = rootNavigator,
                            onSignOut = {
                                signedIn = false
                            }
                        )
                    }
                } else {
                    screen<SignedOut> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Button(onClick = { signedIn = true }) {
                                Text("Sign in")
                            }
                        }
                    }
                }
                screen<ModalStack> {
                    val modalNavigator = rememberNavigator(NavConfig.Stack)

                    Router(navigator = modalNavigator) {
                        modalScreens(modalNavigator)
                    }
                }
            }
        }
    }
}