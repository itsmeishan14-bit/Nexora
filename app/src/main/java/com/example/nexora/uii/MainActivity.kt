package com.example.nexora.uii

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nexora.ai.*
import com.example.nexora.ai.evaluation.AiEvaluationViewModel
import com.example.nexora.data.NexoraDatabase
import com.example.nexora.data.NexoraRepository
import com.example.nexora.ui.theme.*

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NexoraTheme {
                val context = LocalContext.current
                val database = remember { NexoraDatabase.getDatabase(context) }
                val repository = remember { NexoraRepository(database) }

                val aiEngine = remember {
                    val contextBuilder = AiContextBuilder(repository)
                    val localProvider = LocalAiProvider()
                    val providerManager = AiProviderManager(localProvider = localProvider)
                    val aiService = LocalNexoraAiService(providerManager = providerManager)
                    val actionExecutor = AiActionExecutor(repository)
                    val toolRegistry = AiToolRegistry(repository, actionExecutor)

                    NexoraAiEngine(
                        contextBuilder = contextBuilder,
                        aiService = aiService,
                        actionExecutor = actionExecutor,
                        toolRegistry = toolRegistry,
                        repository = repository
                    )
                }

                val mainViewModel: NexoraMainViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return NexoraMainViewModel(repository) as T
                        }
                    }
                )

                val aiViewModel: NexoraAiViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return NexoraAiViewModel(aiEngine) as T
                        }
                    }
                )

                val mainState by mainViewModel.uiState.collectAsState()
                val aiState by aiViewModel.uiState.collectAsState()

                val windowSize = calculateWindowSizeClass(this)
                val isWide = windowSize.widthSizeClass != WindowWidthSizeClass.Compact

                Row(Modifier.fillMaxSize()) {
                    if (isWide) {
                        NavigationRail(
                            containerColor = Color.White,
                            contentColor = Green60,
                            header = {
                                IconBox(icon = Icons.Rounded.Splitscreen, Green95, Green60, 48)
                            }
                        ) {
                            Spacer(Modifier.weight(1f))
                            NexoraRailItem("Home", Icons.Rounded.Home, mainState.selectedScreen == "home") { mainViewModel.navigateTo("home") }
                            NexoraRailItem("Tasks", Icons.Rounded.CheckCircle, mainState.selectedScreen == "tasks") { mainViewModel.navigateTo("tasks") }
                            NexoraRailItem("Goals", Icons.Rounded.Flag, mainState.selectedScreen == "goals") { mainViewModel.navigateTo("goals") }
                            NexoraRailItem("AI", Icons.Rounded.Insights, mainState.selectedScreen == "insights") { mainViewModel.navigateTo("insights") }
                            Spacer(Modifier.weight(1f))
                        }
                    }

                    Scaffold(
                        containerColor = NexoraBackgroundLight,
                        bottomBar = {
                            if (!isWide && mainState.selectedScreen in listOf("home", "tasks", "goals", "insights")) {
                                NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                                    NexoraNavItem("Home", Icons.Rounded.Home, mainState.selectedScreen == "home") { mainViewModel.navigateTo("home") }
                                    NexoraNavItem("Tasks", Icons.Rounded.CheckCircle, mainState.selectedScreen == "tasks") { mainViewModel.navigateTo("tasks") }
                                    NexoraNavItem("Goals", Icons.Rounded.Flag, mainState.selectedScreen == "goals") { mainViewModel.navigateTo("goals") }
                                    NexoraNavItem("AI", Icons.Rounded.Insights, mainState.selectedScreen == "insights") { mainViewModel.navigateTo("insights") }
                                }
                            }
                        }
                    ) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                                when (mainState.selectedScreen) {
                                    "home" -> HomeScreen(
                                        tasks = mainState.tasks.toMutableStateList(),
                                        goals = mainState.goals.toMutableStateList(),
                                        progressHistory = mainState.progressHistory,
                                        onAddTask = { mainViewModel.navigateTo("addTask") },
                                        onToggleTask = { mainViewModel.toggleTask(it) },
                                        proactiveSignals = aiState.proactiveSignals,
                                        proposedAction = aiState.homeProposedAction,
                                        onApproveAction = { action ->
                                            aiViewModel.executeHomeAction(action) {
                                                // No explicit data refresh needed here as ViewModel handles it
                                            }
                                        },
                                        onDismissAction = { aiViewModel.dismissHomeAction() }
                                    )
                                    "tasks" -> TasksScreen(
                                        tasks = mainState.tasks.toMutableStateList(),
                                        onAddTask = { mainViewModel.navigateTo("addTask") },
                                        onToggleTask = { mainViewModel.toggleTask(it) },
                                        onAiAction = { mainViewModel.navigateTo("insights") },
                                        onDeleteTask = { mainViewModel.deleteTask(it) }
                                    )
                                    "goals" -> GoalScreen(
                                        goals = mainState.goals.toMutableStateList(),
                                        personalContext = aiState.personalContext,
                                        onAddGoal = { mainViewModel.navigateTo("addGoal") },
                                        onEditGoal = { mainViewModel.setEditingGoal(it); mainViewModel.navigateTo("addGoal") },
                                        onOpenGoal = { mainViewModel.setSelectedGoal(it); mainViewModel.navigateTo("goalDetails") }
                                    )
                                    "goalDetails" -> mainState.selectedGoal?.let { goal ->
                                        GoalDetailsScreen(
                                            goal = goal,
                                            relatedTasks = mainState.tasks.filter { it.goalTitle == goal.title },
                                            personalContext = aiState.personalContext,
                                            onBack = { mainViewModel.setSelectedGoal(null); mainViewModel.navigateTo("goals") },
                                            onEdit = { mainViewModel.setEditingGoal(goal); mainViewModel.navigateTo("addGoal") },
                                            onDelete = { mainViewModel.deleteGoal(goal); mainViewModel.navigateTo("goals") },
                                            onToggleTask = { mainViewModel.toggleTask(it) },
                                            onAddTask = { mainViewModel.setTaskGoal(goal); mainViewModel.navigateTo("addTask") },
                                            onDecomposeGoal = { mainViewModel.navigateTo("aiGoalDecomposer") }
                                        )
                                    }
                                    "insights" -> AiScreen(
                                        engine = aiEngine,
                                        onOpenGoalDecomposer = { mainViewModel.navigateTo("aiGoalDecomposer") },
                                        onOpenAutomations = { mainViewModel.navigateTo("aiAutomations") },
                                        onOpenEvaluation = { mainViewModel.navigateTo("aiBenchmarks") },
                                        onRecommendationAction = { rec ->
                                            rec.relatedGoalId?.let { id -> mainState.goals.find { it.id == id }?.let { mainViewModel.setSelectedGoal(it); mainViewModel.navigateTo("goalDetails") } }
                                            rec.relatedTaskId?.let { mainViewModel.navigateTo("tasks") }
                                        }
                                    )
                                    "aiGoalDecomposer" -> AiGoalDecomposerScreen(
                                        engine = aiEngine,
                                        onBack = { mainViewModel.navigateTo("insights") },
                                        onTasksCreated = {
                                            // ViewModel should handle internal refresh if needed, 
                                            // but we might need a signal to reload data.
                                            // loadData is already reactive if it was using Flows.
                                            // Since we use Lists in State, we might need a refresh method.
                                            mainViewModel.refreshAll()
                                        }
                                    )
                                    "aiAutomations" -> AiAutomationScreen(
                                        rules = aiState.automationRules,
                                        onBack = { mainViewModel.navigateTo("insights") },
                                        onToggleRule = { aiViewModel.toggleAutomationRule(it) }
                                    )
                                    "aiBenchmarks" -> {
                                        val evaluationViewModel: AiEvaluationViewModel = viewModel(
                                            factory = object : ViewModelProvider.Factory {
                                                @Suppress("UNCHECKED_CAST")
                                                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                                    return AiEvaluationViewModel(repository, aiEngine) as T
                                                }
                                            }
                                        )
                                        AiEvaluationScreen(viewModel = evaluationViewModel, onBack = { mainViewModel.navigateTo("insights") })
                                    }
                                    "addTask" -> AddTaskScreen(
                                        onBack = { mainViewModel.setTaskGoal(null); mainViewModel.navigateTo("tasks") },
                                        goals = mainState.goals,
                                        selectedGoal = mainState.taskGoal,
                                        onSave = { name, cat, dur, gt, p ->
                                            mainViewModel.addTask(name, cat, dur, gt, p)
                                            mainViewModel.setTaskGoal(null)
                                            mainViewModel.navigateTo("tasks")
                                        }
                                    )
                                    "addGoal" -> AddGoalScreen(
                                        onBack = { mainViewModel.setEditingGoal(null); mainViewModel.navigateTo("goals") },
                                        onSave = { name, cat, td, _ ->
                                            if (mainState.editingGoal == null) {
                                                mainViewModel.addGoal(name, cat, td)
                                            } else {
                                                mainViewModel.updateGoal(mainState.editingGoal!!, name, cat, td)
                                            }
                                            mainViewModel.setEditingGoal(null)
                                            mainViewModel.navigateTo("goals")
                                        },
                                        existingGoal = mainState.editingGoal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.NexoraNavItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, null, modifier = Modifier.size(24.dp)) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Green60,
            selectedTextColor = Green60,
            unselectedIconColor = Green40,
            unselectedTextColor = Green40,
            indicatorColor = Green95
        )
    )
}

@Composable
private fun NexoraRailItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, null, modifier = Modifier.size(24.dp)) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = NavigationRailItemDefaults.colors(
            selectedIconColor = Green60,
            selectedTextColor = Green60,
            unselectedIconColor = Green40,
            unselectedTextColor = Green40,
            indicatorColor = Green95
        )
    )
}

// Extension to convert List to SnapshotStateList for screens that expect it
private fun <T> List<T>.toMutableStateList(): androidx.compose.runtime.snapshots.SnapshotStateList<T> {
    val list = androidx.compose.runtime.mutableStateListOf<T>()
    list.addAll(this)
    return list
}
