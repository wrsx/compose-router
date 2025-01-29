package ankers.compose.router

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.toMutableStateList

@Composable
fun <T : Any> rememberMutableStateListOf(elements: List<T> = listOf()) = rememberSaveable(
    saver = listSaver(
        save = { it.toList() },
        restore = {
            it.toMutableStateList()
        }
    )
) {
    elements.toMutableStateList()
}