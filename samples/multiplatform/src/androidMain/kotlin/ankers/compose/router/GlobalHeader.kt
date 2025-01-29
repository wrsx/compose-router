package ankers.compose.router

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun GlobalHeader() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Global Header",
            modifier = Modifier.padding(vertical = 4.dp),
            style = MaterialTheme.typography.h4,
            textAlign = TextAlign.Center
        )
        TabRowDefaults.Divider(
            Modifier
                .background(Color.Black)
                .fillMaxWidth()
                .height(1.dp)
        )
    }
}