package dev.retrobit.agent.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.retrobit.agent.AppContainer
import dev.retrobit.agent.data.Project
import dev.retrobit.agent.data.Templates
import kotlinx.coroutines.launch

class ProjectsViewModel(private val container: AppContainer) : ViewModel() {

    val repository = container.repository
    val settings = container.settings
    val projects = repository.projects
    val config = settings.config
    val usage = settings.usage

    init {
        viewModelScope.launch { repository.load() }
    }

    fun create(
        name: String,
        templateId: String,
        onCreated: (Project) -> Unit,
    ) {
        val design = Templates.designFor(templateId)
        viewModelScope.launch {
            val project = repository.create(name, templateId, design)
            onCreated(project)
        }
    }

    /** Dashboard composer: create a project from a one-line idea and open it. */
    fun createFromPrompt(prompt: String, onCreated: (Project) -> Unit) {
        val clean = prompt.trim()
        if (clean.isEmpty()) return
        val templateId = Templates.suggestId(clean)
        val words = clean.split(Regex("\\s+")).take(3).joinToString(" ")
        val name = words.replaceFirstChar { it.uppercase() }.take(26).ifBlank { Templates.byId(templateId).name }
        viewModelScope.launch {
            val project = repository.create(name, templateId, Templates.designFor(templateId))
            onCreated(project)
        }
    }

    fun duplicate(projectId: String, onCreated: (Project) -> Unit) {
        viewModelScope.launch {
            repository.duplicate(projectId)?.let(onCreated)
        }
    }

    fun delete(projectId: String) {
        viewModelScope.launch { repository.delete(projectId) }
    }

    fun toggleFavorite(project: Project) {
        viewModelScope.launch { repository.update(project.copy(favorite = !project.favorite), touch = false) }
    }

}
