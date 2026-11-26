package com.grabbit.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import com.grabbit.core.UiValues

enum class DockEdge {
    LEFT,
    RIGHT,
}

class FloatingDownloadState(
    private val edgeState: MutableState<DockEdge>,
    private val fractionState: MutableState<Float>,
    private val expandedState: MutableState<Boolean>,
) {
    val edge: DockEdge
        get() = edgeState.value

    val verticalFraction: Float
        get() = fractionState.value

    val expanded: Boolean
        get() = expandedState.value

    fun dock(
        edge: DockEdge,
        verticalFraction: Float,
    ) {
        edgeState.value = edge
        fractionState.value = verticalFraction.coerceIn(0f, 1f)
    }

    fun expand() {
        expandedState.value = true
    }

    fun collapse() {
        expandedState.value = false
    }
}

@Composable
fun rememberFloatingDownloadState(): FloatingDownloadState {
    val edge = rememberSaveable { mutableStateOf(DockEdge.RIGHT) }
    val verticalFraction =
        rememberSaveable { mutableStateOf(UiValues.FLOATING_INITIAL_VERTICAL_FRACTION) }
    val expanded = rememberSaveable { mutableStateOf(false) }
    return remember(edge, verticalFraction, expanded) {
        FloatingDownloadState(edge, verticalFraction, expanded)
    }
}
