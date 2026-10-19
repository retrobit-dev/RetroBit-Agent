package dev.retrobit.agent

import android.app.Application
import android.content.Context
import dev.retrobit.agent.agent.AgentEngine
import dev.retrobit.agent.data.ProjectRepository
import dev.retrobit.agent.data.SecretStore
import dev.retrobit.agent.data.SettingsStore

class AppContainer(context: Context) {
    val context: Context = context.applicationContext
    val repository = ProjectRepository(this.context)
    val settings = SettingsStore(this.context)
    val secrets = SecretStore(this.context)
    val engine = AgentEngine(this.context, repository, settings, secrets)
}

class RetroBitApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
