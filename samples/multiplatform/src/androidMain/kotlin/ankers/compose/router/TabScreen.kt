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
    contentBefore: (@Composable () -> Unit)? = {},
    contentAfter: (@Composable () -> Unit)? = {},
) {
    Column(
        Modifier
            .background(background)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 108.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            contentBefore?.invoke()
            icon?.let {
                Icon(
                    modifier = Modifier.size(72.dp),
                    imageVector = icon,
                    contentDescription = "",
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.h4,
                textAlign = TextAlign.Center
            )
            contentAfter?.invoke()
        }
    }
}