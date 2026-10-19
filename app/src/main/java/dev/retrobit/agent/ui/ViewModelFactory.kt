package dev.retrobit.agent.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.retrobit.agent.AppContainer
import dev.retrobit.agent.RetroBitApp

class SimpleVmFactory<T : ViewModel>(private val creator: () -> T) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = creator() as VM
}

@Composable
fun container(): AppContainer {
    val context = LocalContext.current
    return (context.applicationContext as RetroBitApp).container
}
