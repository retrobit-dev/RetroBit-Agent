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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import dev.retrobit.agent.data.Project
import dev.retrobit.agent.ui.components.BrandMark
import dev.retrobit.agent.ui.components.Pill
import dev.retrobit.agent.ui.components.relativeTime
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import dev.retrobit.agent.ui.icons.PixelIcons
import dev.retrobit.agent.ui.theme.PixelFont

private data class BottomDestination(val route: String, val label: String, val icon: ImageVector)

private val destinations = listOf(
    BottomDestination("projects", "Home", PixelIcons.Home),
    BottomDestination("templates", "Templates", PixelIcons.Layout),
    BottomDestination("cloud", "Cloud", PixelIcons.Cloud),
)

@Composable
fun RetroBitRoot() {
    val container = container()
    val projectsVm: ProjectsViewModel = viewModel(factory = SimpleVmFactory { ProjectsViewModel(container) })
    val cloudVm: CloudViewModel = viewModel(factory = SimpleVmFactory { CloudViewModel(container) })
    val projects by projectsVm.projects.collectAsStateWithLifecycle()

    RetroBitTheme {
        AuroraBackground {
            val navController = rememberNavController()
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            val drawer = remember(drawerState, scope) { DrawerController(drawerState, scope) }
            val backStack by navController.currentBackStackEntryAsState()
            val route = backStack?.destination?.route.orEmpty()

            CompositionLocalProvider(LocalDrawer provides drawer) {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = !route.startsWith("workspace"),
                    scrimColor = Color.Black.copy(alpha = 0.72f),
                    drawerContent = {
                        DashboardSidebar(
                            projects = projects,
                            route = route,
                            onNavigate = { target ->
                                scope.launch { drawerState.close() }
                                navigateTab(navController, target)
                            },
                            onOpenProject = { id ->
                                scope.launch { drawerState.close() }
                                navController.navigate("workspace/$id")
                            },
                            onClose = { scope.launch { drawerState.close() } },
                        )
                    },
                ) {
                    NavHost(navController = navController, startDestination = "projects") {
                        composable("projects") {
                            MainShell("projects", navController) {
                                ProjectsScreen(
                                    vm = projectsVm,
                                    onOpenProject = { id -> navController.navigate("workspace/$id") },
                                    onOpenCloud = { navigateTab(navController, "cloud") },
                                    onBrowseTemplates = { navigateTab(navController, "templates") },
                                    onCreateWithPrompt = { prompt, id ->
                                        navController.navigate("workspace/$id?prompt=${android.net.Uri.encode(prompt)}")
                                    },
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
                        composable(
                            route = "workspace/{projectId}?prompt={prompt}",
                            arguments = listOf(
                                navArgument("prompt") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                            ),
                        ) { entry ->
                            val projectId = entry.arguments?.getString("projectId").orEmpty()
                            val initialPrompt = entry.arguments?.getString("prompt").orEmpty()
                            val workspaceVm: WorkspaceViewModel = viewModel(
                                key = "workspace_$projectId",
                                factory = SimpleVmFactory {
                                    WorkspaceViewModel(
                                        container = container,
                                        projectId = projectId,
                                        initialPrompt = initialPrompt.ifBlank { null },
                                    )
                                },
                            )
                            WorkspaceScreen(vm = workspaceVm, onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardSidebar(
    projects: List<Project>,
    route: String,
    onNavigate: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    ModalDrawerSheet(
        drawerContainerColor = colors.surface,
        drawerContentColor = colors.onSurface,
        drawerShape = RoundedCornerShape(0.dp),
        modifier = Modifier
            .fillMaxWidth(0.88f)
            .widthIn(max = 340.dp),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(30.dp)
                Spacer(Modifier.width(9.dp))
                Text(
                    "RetroBit",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                )
                Spacer(Modifier.weight(1f))
                androidx.compose.material3.IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(PixelIcons.Close, "Tutup", tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(18.dp))

            SidebarItem("Dashboard", PixelIcons.Home, route.startsWith("projects")) { onNavigate("projects") }
            SidebarItem("Search", PixelIcons.Search, false) { onNavigate("projects") }
            SidebarItem("All projects", PixelIcons.Grid, false) { onNavigate("projects") }
            SidebarItem("Templates", PixelIcons.Layout, route.startsWith("templates")) { onNavigate("templates") }
            SidebarItem("AI Cloud", PixelIcons.Cloud, route.startsWith("cloud")) { onNavigate("cloud") }

            Spacer(Modifier.height(20.dp))
            Text(
                "RECENTS",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                letterSpacing = 1.4.sp,
            )
            Spacer(Modifier.height(6.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if (projects.isEmpty()) {
                    Text(
                        "Belum ada proyek.",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                projects.take(8).forEach { project ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(3.dp))
                            .clickable { onOpenProject(project.id) }
                            .padding(horizontal = 8.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(colors.surfaceVariant)
                                .border(1.dp, colors.outlineVariant, RoundedCornerShape(2.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                project.name.take(1).uppercase(),
                                fontFamily = PixelFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = colors.onSurface,
                            )
                        }
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                project.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                relativeTime(project.updatedAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        Icon(PixelIcons.MoreVertical, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.surfaceVariant.copy(alpha = 0.4f))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(4.dp))
                    .clickable { onNavigate("cloud") }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("AI Cloud", style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
                    Text(
                        "Provider, model & system prompt",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                Icon(PixelIcons.ArrowRight, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(2.dp))
                    .clickable { onNavigate("cloud") }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(PixelIcons.Star, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Refer friends", style = MaterialTheme.typography.labelMedium, color = colors.onSurface)
                    Text(
                        "Earn free credits",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                Icon(PixelIcons.ArrowRight, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(13.dp))
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(26.dp, "R")
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Retro Bit", style = MaterialTheme.typography.labelMedium, color = colors.onSurface)
                    Text(
                        "Local workspace · on-device",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                Pill("OFFLINE", color = colors.onSurfaceVariant, background = Color.Transparent)
            }
        }
    }
}

@Composable
private fun SidebarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(if (selected) colors.surfaceVariant else Color.Transparent)
            .border(
                1.dp,
                if (selected) colors.outline else Color.Transparent,
                RoundedCornerShape(3.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) colors.onSurface else colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun MainShell(
    route: String,
    navController: androidx.navigation.NavHostController,
    content: @Composable () -> Unit,
) {
    Scaffold(
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
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
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
                                        if (selected) MaterialTheme.colorScheme.outline else Color.Transparent,
                                        RoundedCornerShape(2.dp),
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
                                        if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                                        RoundedCornerShape(2.dp),
                                    )
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                destination.icon,
                                contentDescription = destination.label,
                                tint = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            destination.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        }
    }
}