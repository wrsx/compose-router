package ankers.compose.router

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun TabScreen(
    label: String,
    icon: ImageVector? = null,
    background: Color = Color.Transparent,
    content: @Composable () -> Unit = {},
) {
    Column(
        Modifier
            .background(background)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(72.dp)) }
        Text(label, style = MaterialTheme.typography.h4, textAlign = TextAlign.Center)
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}
