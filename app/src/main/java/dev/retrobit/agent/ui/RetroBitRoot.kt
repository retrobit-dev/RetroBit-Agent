package dev.retrobit.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.retrobit.agent.ui.components.AuroraBackground
import dev.retrobit.agent.ui.screens.CloudScreen
import dev.retrobit.agent.ui.screens.CloudViewModel
import dev.retrobit.agent.ui.screens.ProjectsScreen
import dev.retrobit.agent.ui.screens.ProjectsViewModel
import dev.retrobit.agent.ui.screens.TemplatesScreen
import dev.retrobit.agent.ui.screens.WorkspaceScreen
import dev.retrobit.agent.ui.screens.WorkspaceViewModel
import dev.retrobit.agent.ui.theme.Palette
import dev.retrobit.agent.ui.theme.RetroBitTheme

private data class BottomDestination(val route: String, val label: String, val icon: ImageVector)

private val destinations = listOf(
    BottomDestination("projects", "Proyek", Icons.Outlined.GridView),
    BottomDestination("templates", "Template", Icons.Outlined.Dashboard),
    BottomDestination("cloud", "AI Cloud", Icons.Outlined.CloudQueue),
)

@Composable
fun RetroBitRoot() {
    val container = container()
    val projectsVm: ProjectsViewModel = viewModel(factory = SimpleVmFactory { ProjectsViewModel(container) })
    val cloudVm: CloudViewModel = viewModel(factory = SimpleVmFactory { CloudViewModel(container) })
    val themeMode by container.settings.themeModeFlow.collectAsStateWithLifecycle()

    RetroBitTheme(darkTheme = themeMode != "light") {
        AuroraBackground {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = "projects") {
                composable("projects") {
                    MainShell("projects", navController) {
                        ProjectsScreen(
                            vm = projectsVm,
                            onOpenProject = { id -> navController.navigate("workspace/$id") },
                            onOpenCloud = { navigateTab(navController, "cloud") },
                            onBrowseTemplates = { navigateTab(navController, "templates") },
                        )
                    }
                }
                composable("templates") {
                    MainShell("templates", navController) {
                        TemplatesScreen(
                            vm = projectsVm,
                            onOpenProject = { id -> navController.navigate("workspace/$id") },
                        )
                    }
                }
                composable("cloud") {
                    MainShell("cloud", navController) {
                        CloudScreen(vm = cloudVm)
                    }
                }
                composable("workspace/{projectId}") { entry ->
                    val projectId = entry.arguments?.getString("projectId").orEmpty()
                    val workspaceVm: WorkspaceViewModel = viewModel(
                        key = "workspace_$projectId",
                        factory = SimpleVmFactory { WorkspaceViewModel(container, projectId) },
                    )
                    WorkspaceScreen(vm = workspaceVm, onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

@Composable
private fun MainShell(
    route: String,
    navController: androidx.navigation.NavHostController,
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            RetroBottomBar(current = route) { target ->
                navigateTab(navController, target)
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
                .statusBarsPadding()
        ) {
            content()
        }
    }
}

private fun navigateTab(navController: androidx.navigation.NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun RetroBottomBar(current: String, onNavigate: (String) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                destinations.forEach { destination ->
                    val selected = current == destination.route
                    Column(
                        Modifier
                            .weight(1f)
                            .then(
                                Modifier
                                    .border(
                                        1.dp,
                                        if (selected) Palette.Primary.copy(alpha = 0.35f) else Color.Transparent,
                                        RoundedCornerShape(14.dp),
                                    )
                            )
                            .clickable { onNavigate(destination.route) }
                            .padding(vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .then(
                                    Modifier.background(
                                        if (selected) Palette.Primary.copy(alpha = 0.16f) else Color.Transparent,
                                        CircleShape,
                                    )
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                destination.icon,
                                contentDescription = destination.label,
                                tint = if (selected) Palette.Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            destination.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        }
    }
}
