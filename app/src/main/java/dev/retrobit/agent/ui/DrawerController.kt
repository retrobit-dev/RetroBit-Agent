package dev.retrobit.agent.ui

import androidx.compose.material3.DrawerState
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Lets any screen open the dashboard drawer without threading callbacks everywhere. */
class DrawerController(private val state: DrawerState, private val scope: CoroutineScope) {
    fun open() {
        scope.launch { state.open() }
    }

    fun close() {
        scope.launch { state.close() }
    }
}

val LocalDrawer = staticCompositionLocalOf<DrawerController?> { null }
