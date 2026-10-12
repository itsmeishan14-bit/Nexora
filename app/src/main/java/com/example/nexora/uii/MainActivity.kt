package com.example.nexora.uii

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nexora.ai.*
import com.example.nexora.ai.evaluation.AiEvaluationViewModel
import com.example.nexora.data.NexoraDatabase
import com.example.nexora.data.NexoraRepository
import androidx.compose.ui.text.font.FontWeight
import com.example.nexora.ui.theme.*

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            NexoraTheme {
                val context = LocalContext.current
                val database = remember { NexoraDatabase.getDatabase(context) }
                val repository = remember { NexoraRepository(database) }

                val aiEngine = remember {
                    val automationSystem = NexoraAutomationSystem(repository)
                    val contextBuilder = AiContextBuilder(repository)
                    val localIntentResolver = LocalAiIntentResolver(automationSystem = automationSystem)
                    val localProvider = LocalAiProvider(intentResolver = localIntentResolver)
                    
                    // Cloud provider configuration (Loaded securely from local.properties / BuildConfig / env)
                    val cloudConfig = CloudAiConfig.loadFromEnvironment(context.applicationContext)
                    val cloudProvider = CloudAiProvider(cloudConfig, context.applicationContext)
                    
                    val providerManager = AiProviderManager(
                        localProvider = localProvider,
                        cloudProvider = cloudProvider
                    )
                    
                    val aiService = LocalNexoraAiService(providerManager = providerManager)
                    val actionExecutor = AiActionExecutor(repository, automationSystem)
                    val toolRegistry = AiToolRegistry(repository, actionExecutor)

                    NexoraAiEngine(
                        contextBuilder = contextBuilder,
                        aiService = aiService,
                        providerManager = providerManager,
                        actionExecutor = actionExecutor,
                        toolRegistry = toolRegistry,
                        repository = repository,
                        automationSystem = automationSystem
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
                            return NexoraAiViewModel(aiEngine, context.applicationContext) as T
                        }
                    }
                )

                val mainState by mainViewModel.uiState.collectAsState()
                val aiState by aiViewModel.uiState.collectAsState()

                val windowSize = calculateWindowSizeClass(this)
                val isWide = windowSize.widthSizeClass != WindowWidthSizeClass.Compact

                Row(Modifier.fillMaxSize()) {
                    if (isWide) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary,
                                header = {
                                    IconBox(
                                        icon = Icons.Rounded.Splitscreen,
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.primary,
                                        size = 44
                                    )
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
                    }

                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.background,
                        bottomBar = {
                            if (!isWide && mainState.selectedScreen in listOf("home", "tasks", "goals", "insights")) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 0.dp
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surface)
                                    ) {
                                        HorizontalDivider(
                                            thickness = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                        NavigationBar(
                                            modifier = Modifier.fillMaxWidth(),
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            contentColor = MaterialTheme.colorScheme.onSurface,
                                            tonalElevation = 0.dp,
                                            windowInsets = NavigationBarDefaults.windowInsets
                                        ) {
                                            NexoraNavItem("Home", Icons.Rounded.Home, mainState.selectedScreen == "home") { mainViewModel.navigateTo("home") }
                                            NexoraNavItem("Tasks", Icons.Rounded.CheckCircle, mainState.selectedScreen == "tasks") { mainViewModel.navigateTo("tasks") }
                                            NexoraNavItem("Goals", Icons.Rounded.Flag, mainState.selectedScreen == "goals") { mainViewModel.navigateTo("goals") }
                                            NexoraNavItem("AI", Icons.Rounded.Insights, mainState.selectedScreen == "insights") { mainViewModel.navigateTo("insights") }
                                        }
                                    }
                                }
                            }
                        }
                    ) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                                when (mainState.selectedScreen) {
                                    "home" -> HomeScreen(
                                        tasks = mainState.tasks,
                                        goals = mainState.goals,
                                        progressHistory = mainState.progressHistory,
                                        onAddTask = { mainViewModel.navigateTo("addTask") },
                                        onToggleTask = { 
                                            mainViewModel.toggleTask(it)
                                            aiViewModel.analyze()
                                        },
                                        onNavigateToTasks = { mainViewModel.navigateTo("tasks") },
                                        onNavigateToGoals = { mainViewModel.navigateTo("goals") },
                                        onNavigateToGoalDetails = { goal ->
                                            mainViewModel.setSelectedGoal(goal)
                                            mainViewModel.navigateTo("goalDetails")
                                        },
                                        onNavigateToAi = { mainViewModel.navigateTo("insights") },
                                        proactiveSignals = aiState.proactiveSignals,
                                        aiRecommendations = aiState.recommendations,
                                        proposedAction = aiState.homeProposedAction,
                                        lastActionResult = aiState.lastActionResult,
                                        lastExecutionRecord = aiState.lastExecutionRecord,
                                        onApproveAction = { action ->
                                            aiViewModel.executeHomeAction(action) {
                                                mainViewModel.refreshAll()
                                            }
                                        },
                                        onDismissAction = { aiViewModel.dismissHomeAction() },
                                        onDismissResult = { aiViewModel.dismissResult() },
                                        onRecommendationAction = { rec ->
                                            rec.relatedGoalId?.let { id -> mainState.goals.find { it.id == id }?.let { mainViewModel.setSelectedGoal(it); mainViewModel.navigateTo("goalDetails") } }
                                            rec.relatedTaskId?.let { mainViewModel.navigateTo("tasks") }
                                        }
                                    )
                                    "tasks" -> TasksScreen(
                                        tasks = mainState.tasks,
                                        topRecommendation = aiState.recommendations.find { it.type == AiRecommendationType.NEXT_TASK },
                                        onAddTask = { mainViewModel.navigateTo("addTask") },
                                        onToggleTask = { 
                                            mainViewModel.toggleTask(it)
                                            aiViewModel.analyze()
                                        },
                                        onAiAction = { mainViewModel.navigateTo("insights") },
                                        onDeleteTask = { 
                                            mainViewModel.deleteTask(it)
                                            aiViewModel.analyze()
                                        }
                                    )
                                    "goals" -> GoalScreen(
                                        goals = mainState.goals,
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
                                            onToggleTask = { 
                                                mainViewModel.toggleTask(it)
                                                aiViewModel.analyze()
                                            },
                                            onAddTask = { mainViewModel.setTaskGoal(goal); mainViewModel.navigateTo("addTask") },
                                            onDecomposeGoal = { mainViewModel.navigateTo("aiGoalDecomposer") },
                                            onUpdateProgress = { newProgress -> 
                                                mainViewModel.updateGoalProgress(goal, newProgress)
                                                aiViewModel.analyze()
                                            }
                                        )
                                    }
                                    "insights" -> AiScreen(
                                        viewModel = aiViewModel,
                                        onOpenGoalDecomposer = { mainViewModel.navigateTo("aiGoalDecomposer") },
                                        onOpenAutomations = { 
                                            aiViewModel.loadAutomationRules()
                                            mainViewModel.navigateTo("aiAutomations") 
                                        },
                                        onOpenEvaluation = { mainViewModel.navigateTo("aiBenchmarks") },
                                        onRecommendationAction = { rec ->
                                            rec.relatedGoalId?.let { id -> mainState.goals.find { it.id == id }?.let { mainViewModel.setSelectedGoal(it); mainViewModel.navigateTo("goalDetails") } }
                                            rec.relatedTaskId?.let { mainViewModel.navigateTo("tasks") }
                                        },
                                        onTaskAction = { taskId ->
                                            mainViewModel.navigateTo("tasks")
                                        }
                                    )
                                    "aiGoalDecomposer" -> AiGoalDecomposerScreen(
                                        engine = aiEngine,
                                        initialGoalTitle = mainState.selectedGoal?.title ?: "",
                                        initialGoalDescription = "",
                                        onBack = {
                                            if (mainState.selectedGoal != null) {
                                                mainViewModel.navigateTo("goalDetails")
                                            } else {
                                                mainViewModel.navigateTo("insights")
                                            }
                                        },
                                        onTasksCreated = {
                                            mainViewModel.refreshAll()
                                            aiViewModel.analyze()
                                            if (mainState.selectedGoal != null) {
                                                mainViewModel.navigateTo("goalDetails")
                                            } else {
                                                mainViewModel.navigateTo("tasks")
                                            }
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
                                            aiViewModel.analyze()
                                            mainViewModel.navigateTo("tasks")
                                        }
                                    )
                                    "addGoal" -> AddGoalScreen(
                                        onBack = { mainViewModel.setEditingGoal(null); mainViewModel.navigateTo("goals") },
                                        onSave = { name, cat, td, prog ->
                                            if (mainState.editingGoal == null) {
                                                mainViewModel.addGoal(name, cat, td, prog)
                                            } else {
                                                mainViewModel.updateGoal(mainState.editingGoal!!, name, cat, td, prog)
                                            }
                                            mainViewModel.setEditingGoal(null)
                                            aiViewModel.analyze()
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
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant

    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(22.dp)
            )
        },
        label = { 
            Text(
                text = label, 
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
            ) 
        },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = activeColor,
            selectedTextColor = activeColor,
            unselectedIconColor = inactiveColor,
            unselectedTextColor = inactiveColor,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}

@Composable
private fun NexoraRailItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant

    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, null, modifier = Modifier.size(22.dp)) },
        label = { 
            Text(
                text = label, 
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
            ) 
        },
        colors = NavigationRailItemDefaults.colors(
            selectedIconColor = activeColor,
            selectedTextColor = activeColor,
            unselectedIconColor = inactiveColor,
            unselectedTextColor = inactiveColor,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}

