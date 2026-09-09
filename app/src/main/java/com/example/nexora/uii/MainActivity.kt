package com.example.nexora.uii

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.example.nexora.ai.*
import com.example.nexora.data.DailyProgressEntity
import com.example.nexora.data.NexoraDatabase
import com.example.nexora.data.NexoraRepository
import com.example.nexora.ui.theme.*
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

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

                val scope = rememberCoroutineScope()
                val tasks = remember { mutableStateListOf<PremiumTask>() }
                val goals = remember { mutableStateListOf<NexoraGoal>() }
                val progressHistory = remember { mutableStateListOf<DailyProgress>() }

                var selectedScreen by remember { mutableStateOf("home") }
                var selectedGoal by remember { mutableStateOf<NexoraGoal?>(null) }
                var editingGoal by remember { mutableStateOf<NexoraGoal?>(null) }
                var taskGoal by remember { mutableStateOf<NexoraGoal?>(null) }
                var topAiInsight by remember { mutableStateOf<String?>(null) }
                var homeProactiveInsight by remember { mutableStateOf<AiRecommendation?>(null) }
                var homeProposedAction by remember { mutableStateOf<AiAction?>(null) }
                var personalContext by remember { mutableStateOf<AiPersonalContext?>(null) }
                var proactiveSignals by remember { mutableStateOf<List<AiProactiveSignal>>(emptyList()) }

                // LOAD DATA
                LaunchedEffect(Unit) {
                    val savedTasks = repository.observeTasks().first()
                    val savedGoals = repository.observeGoals().first()
                    val savedProgress = repository.observeDailyProgress().first()

                    tasks.clear()
                    tasks.addAll(savedTasks)
                    goals.clear()
                    goals.addAll(savedGoals)
                    progressHistory.clear()
                    progressHistory.addAll(savedProgress.map { item ->
                        DailyProgress(
                            date = LocalDate.parse(item.date),
                            tasksPlanned = item.tasksPlanned,
                            tasksCompleted = item.tasksCompleted,
                            focusMinutes = item.focusMinutes,
                            goalsWorkedOn = item.goalsWorkedOn,
                            carriedTasks = item.carriedTasks
                        )
                    })

                    topAiInsight = aiEngine.getTopInsight()
                    val context = aiEngine.getContext()
                    personalContext = context.personalContext
                    
                    scope.launch {
                        val response = aiEngine.processRequest(AiRequest(AiRequestType.PROACTIVE_ANALYSIS))
                        proactiveSignals = response.proactiveSignals
                        homeProactiveInsight = response.recommendations.firstOrNull { it.priority >= AiPriority.HIGH }
                        
                        if (context.incompleteTasks.size >= 8) {
                            homeProposedAction = AiAction(
                                type = AiActionType.RESCHEDULE_TASK,
                                title = "High Workload Detected",
                                description = "You have ${context.incompleteTasks.size} tasks. Should I move lower priority items to tomorrow?",
                                reason = "Too many tasks today reduces focus.",
                                requiresConfirmation = true
                            )
                        }
                    }
                }

                fun updateTodayProgress() {
                    val today = LocalDate.now()
                    val completedCount = tasks.count { it.completed }
                    val totalCount = tasks.size
                    val goalsWorkedOn = tasks.mapNotNull { it.goalTitle }.distinct().size

                    val todayProgress = DailyProgress(
                        date = today,
                        tasksPlanned = totalCount,
                        tasksCompleted = completedCount,
                        focusMinutes = 0,
                        goalsWorkedOn = goalsWorkedOn,
                        carriedTasks = 0
                    )

                    val index = progressHistory.indexOfFirst { it.date == today }
                    if (index >= 0) progressHistory[index] = todayProgress else progressHistory.add(todayProgress)

                    scope.launch {
                        repository.saveDailyProgress(DailyProgressEntity(
                            date = today.toString(),
                            tasksPlanned = totalCount,
                            tasksCompleted = completedCount,
                            focusMinutes = 0,
                            goalsWorkedOn = goalsWorkedOn,
                            carriedTasks = 0
                        ))

                        val context = aiEngine.getContext()
                        personalContext = context.personalContext
                        val response = aiEngine.processRequest(AiRequest(AiRequestType.PROACTIVE_ANALYSIS))
                        proactiveSignals = response.proactiveSignals
                        homeProactiveInsight = response.recommendations.firstOrNull { it.priority >= AiPriority.HIGH }
                    }
                }

                fun refreshGoalProgress() {
                    goals.forEachIndexed { index, goal ->
                        val progress = calculateGoalProgress(goal = goal, tasks = tasks)
                        val updatedGoal = goal.copy(progress = progress)
                        goals[index] = updatedGoal
                        if (selectedGoal?.id == goal.id) selectedGoal = updatedGoal
                        scope.launch { repository.updateGoal(updatedGoal) }
                    }
                }

                Scaffold(
                    bottomBar = {
                        val showBar = selectedScreen in listOf("home", "tasks", "goals", "insights")
                        if (showBar) {
                            NavigationBar(
                                containerColor = Color.White,
                                tonalElevation = 0.dp
                            ) {
                                NavigationBarItem(
                                    selected = selectedScreen == "home",
                                    onClick = { selectedScreen = "home" },
                                    icon = { Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(24.dp)) },
                                    label = { Text("Home", style = MaterialTheme.typography.labelSmall) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = NexoraPrimaryGreen,
                                        selectedTextColor = NexoraPrimaryGreen,
                                        unselectedIconColor = NexoraMutedText,
                                        unselectedTextColor = NexoraMutedText,
                                        indicatorColor = NexoraSoftGreen
                                    )
                                )
                                NavigationBarItem(
                                    selected = selectedScreen == "tasks",
                                    onClick = { selectedScreen = "tasks" },
                                    icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(24.dp)) },
                                    label = { Text("Tasks", style = MaterialTheme.typography.labelSmall) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = NexoraPrimaryGreen,
                                        selectedTextColor = NexoraPrimaryGreen,
                                        unselectedIconColor = NexoraMutedText,
                                        unselectedTextColor = NexoraMutedText,
                                        indicatorColor = NexoraSoftGreen
                                    )
                                )
                                NavigationBarItem(
                                    selected = selectedScreen == "goals",
                                    onClick = { selectedScreen = "goals" },
                                    icon = { Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(24.dp)) },
                                    label = { Text("Goals", style = MaterialTheme.typography.labelSmall) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = NexoraPrimaryGreen,
                                        selectedTextColor = NexoraPrimaryGreen,
                                        unselectedIconColor = NexoraMutedText,
                                        unselectedTextColor = NexoraMutedText,
                                        indicatorColor = NexoraSoftGreen
                                    )
                                )
                                NavigationBarItem(
                                    selected = selectedScreen == "insights",
                                    onClick = { selectedScreen = "insights" },
                                    icon = { Icon(Icons.Default.Insights, contentDescription = null, modifier = Modifier.size(24.dp)) },
                                    label = { Text("AI", style = MaterialTheme.typography.labelSmall) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = NexoraPrimaryGreen,
                                        selectedTextColor = NexoraPrimaryGreen,
                                        unselectedIconColor = NexoraMutedText,
                                        unselectedTextColor = NexoraMutedText,
                                        indicatorColor = NexoraSoftGreen
                                    )
                                )
                            }
                        }
                    }
                ) { paddingValues ->
                    Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                        when (selectedScreen) {
                            "home" -> HomeScreen(
                                tasks = tasks,
                                goals = goals,
                                progressHistory = progressHistory,
                                onAddTask = { selectedScreen = "addTask" },
                                onToggleTask = { task ->
                                    val index = tasks.indexOfFirst { it.id == task.id }
                                    if (index >= 0) {
                                        val updated = task.copy(completed = !task.completed)
                                        tasks[index] = updated
                                        scope.launch { repository.updateTask(updated) }
                                        refreshGoalProgress()
                                        updateTodayProgress()
                                    }
                                },
                                proactiveSignals = proactiveSignals,
                                proposedAction = homeProposedAction,
                                onApproveAction = { action ->
                                    scope.launch {
                                        aiEngine.executeAction(action)
                                        homeProposedAction = null
                                        val savedTasks = repository.observeTasks().first()
                                        tasks.clear()
                                        tasks.addAll(savedTasks)
                                        updateTodayProgress()
                                    }
                                },
                                onDismissAction = { homeProposedAction = null }
                            )
                            "tasks" -> TasksScreen(
                                tasks = tasks,
                                onAddTask = { selectedScreen = "addTask" },
                                onToggleTask = { task ->
                                    val index = tasks.indexOfFirst { it.id == task.id }
                                    if (index >= 0) {
                                        val updated = task.copy(completed = !task.completed)
                                        tasks[index] = updated
                                        scope.launch { repository.updateTask(updated) }
                                        refreshGoalProgress()
                                        updateTodayProgress()
                                    }
                                },
                                onAiAction = { selectedScreen = "insights" },
                                onDeleteTask = { task ->
                                    scope.launch {
                                        repository.deleteTask(task)
                                        tasks.removeAll { it.id == task.id }
                                        refreshGoalProgress()
                                        updateTodayProgress()
                                    }
                                }
                            )
                            "goals" -> GoalScreen(
                                goals = goals,
                                personalContext = personalContext,
                                onAddGoal = { selectedScreen = "addGoal" },
                                onEditGoal = { goal -> editingGoal = goal; selectedScreen = "addGoal" },
                                onOpenGoal = { goal -> selectedGoal = goal; selectedScreen = "goalDetails" }
                            )
                            "goalDetails" -> selectedGoal?.let { goal ->
                                GoalDetailsScreen(
                                    goal = goal,
                                    relatedTasks = tasks.filter { it.goalTitle == goal.title },
                                    personalContext = personalContext,
                                    onBack = { selectedGoal = null; selectedScreen = "goals" },
                                    onEdit = { editingGoal = goal; selectedScreen = "addGoal" },
                                    onDelete = {
                                        scope.launch {
                                            repository.deleteGoal(goal)
                                            val linkedTasks = tasks.filter { it.goalTitle == goal.title }
                                            linkedTasks.forEach { repository.updateTask(it.copy(goalTitle = null)) }
                                            goals.removeAll { it.id == goal.id }
                                            tasks.replaceAll { if (it.goalTitle == goal.title) it.copy(goalTitle = null) else it }
                                            selectedGoal = null
                                            updateTodayProgress()
                                            selectedScreen = "goals"
                                        }
                                    },
                                    onToggleTask = { task ->
                                        val index = tasks.indexOfFirst { it.id == task.id }
                                        if (index >= 0) {
                                            val updated = task.copy(completed = !task.completed)
                                            tasks[index] = updated
                                            scope.launch { repository.updateTask(updated) }
                                            refreshGoalProgress()
                                            updateTodayProgress()
                                        }
                                    },
                                    onAddTask = { taskGoal = goal; selectedScreen = "addTask" },
                                    onDecomposeGoal = { selectedScreen = "aiGoalDecomposer" }
                                )
                            }
                            "insights" -> AiScreen(
                                engine = aiEngine,
                                onOpenGoalDecomposer = { selectedScreen = "aiGoalDecomposer" },
                                onOpenAutomations = { selectedScreen = "aiAutomations" },
                                onOpenEvaluation = { selectedScreen = "aiBenchmarks" },
                                onRecommendationAction = { rec ->
                                    rec.relatedGoalId?.let { id -> goals.find { it.id == id }?.let { selectedGoal = it; selectedScreen = "goalDetails" } }
                                    rec.relatedTaskId?.let { selectedScreen = "tasks" }
                                }
                            )
                            "aiGoalDecomposer" -> AiGoalDecomposerScreen(
                                engine = aiEngine,
                                onBack = { selectedScreen = "insights" },
                                onTasksCreated = {
                                    scope.launch {
                                        val savedTasks = repository.observeTasks().first()
                                        tasks.clear()
                                        tasks.addAll(savedTasks)
                                        refreshGoalProgress()
                                        updateTodayProgress()
                                    }
                                }
                            )
                            "aiAutomations" -> {
                                val aiViewModel: NexoraAiViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                                    factory = object : ViewModelProvider.Factory {
                                        @Suppress("UNCHECKED_CAST")
                                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                            return NexoraAiViewModel(engine = aiEngine) as T
                                        }
                                    }
                                )
                                val aiUiState by aiViewModel.uiState.collectAsState()
                                AiAutomationScreen(
                                    rules = aiUiState.automationRules,
                                    onBack = { selectedScreen = "insights" },
                                    onToggleRule = { aiViewModel.toggleAutomationRule(it) }
                                )
                            }
                            "aiBenchmarks" -> {
                                val evaluationViewModel: com.example.nexora.ai.evaluation.AiEvaluationViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                                    factory = object : ViewModelProvider.Factory {
                                        @Suppress("UNCHECKED_CAST")
                                        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                            return com.example.nexora.ai.evaluation.AiEvaluationViewModel(repository = repository, engine = aiEngine) as T
                                        }
                                    }
                                )
                                AiEvaluationScreen(viewModel = evaluationViewModel, onBack = { selectedScreen = "insights" })
                            }
                            "addTask" -> AddTaskScreen(
                                onBack = { taskGoal = null; selectedScreen = "tasks" },
                                goals = goals,
                                selectedGoal = taskGoal,
                                onSave = { name, cat, dur, gt, p ->
                                    val newTask = PremiumTask(title = name, category = cat, duration = dur, goalTitle = gt, priority = p)
                                    scope.launch {
                                        repository.addTask(newTask)
                                        val savedTasks = repository.observeTasks().first()
                                        tasks.clear()
                                        tasks.addAll(savedTasks)
                                        refreshGoalProgress()
                                        updateTodayProgress()
                                    }
                                    taskGoal = null
                                    selectedScreen = "tasks"
                                }
                            )
                            "addGoal" -> AddGoalScreen(
                                existingGoal = editingGoal,
                                onBack = { editingGoal = null; selectedScreen = "goals" },
                                onSave = { name, cat, td, _ ->
                                    if (editingGoal == null) {
                                        val newGoal = NexoraGoal(title = name, category = cat, targetDate = td, progress = 0f)
                                        scope.launch {
                                            repository.addGoal(newGoal)
                                            val savedGoals = repository.observeGoals().first()
                                            goals.clear()
                                            goals.addAll(savedGoals)
                                            refreshGoalProgress()
                                            updateTodayProgress()
                                        }
                                    } else {
                                        val updatedGoal = editingGoal!!.copy(title = name, category = cat, targetDate = td)
                                        val index = goals.indexOfFirst { it.id == editingGoal!!.id }
                                        if (index >= 0) goals[index] = updatedGoal
                                        scope.launch { repository.updateGoal(updatedGoal) }
                                    }
                                    editingGoal = null
                                    refreshGoalProgress()
                                    updateTodayProgress()
                                    selectedScreen = "goals"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
