package com.example.nexora.uii

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
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

                val scope = rememberCoroutineScope()
                val tasks = remember { mutableStateListOf<PremiumTask>() }
                val goals = remember { mutableStateListOf<NexoraGoal>() }
                val progressHistory = remember { mutableStateListOf<DailyProgress>() }

                var selectedScreen by remember { mutableStateOf("home") }
                var selectedGoal by remember { mutableStateOf<NexoraGoal?>(null) }
                var editingGoal by remember { mutableStateOf<NexoraGoal?>(null) }
                var taskGoal by remember { mutableStateOf<NexoraGoal?>(null) }
                var personalContext by remember { mutableStateOf<AiPersonalContext?>(null) }
                var proactiveSignals by remember { mutableStateOf<List<AiProactiveSignal>>(emptyList()) }
                var homeProposedAction by remember { mutableStateOf<AiAction?>(null) }

                val windowSize = calculateWindowSizeClass(this)
                val isWide = windowSize.widthSizeClass != WindowWidthSizeClass.Compact

                // DATA LOADING
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

                    val context = aiEngine.getContext()
                    personalContext = context.personalContext
                    
                    scope.launch {
                        val response = aiEngine.processRequest(AiRequest(AiRequestType.PROACTIVE_ANALYSIS))
                        proactiveSignals = response.proactiveSignals
                        
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

                fun refreshGoalProgress() {
                    goals.forEachIndexed { index, goal ->
                        val progress = calculateGoalProgress(goal = goal, tasks = tasks)
                        val updatedGoal = goal.copy(progress = progress)
                        goals[index] = updatedGoal
                        if (selectedGoal?.id == goal.id) selectedGoal = updatedGoal
                        scope.launch { repository.updateGoal(updatedGoal) }
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
                    }
                }

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
                            NexoraRailItem("Home", Icons.Rounded.Home, selectedScreen == "home") { selectedScreen = "home" }
                            NexoraRailItem("Tasks", Icons.Rounded.CheckCircle, selectedScreen == "tasks") { selectedScreen = "tasks" }
                            NexoraRailItem("Goals", Icons.Rounded.Flag, selectedScreen == "goals") { selectedScreen = "goals" }
                            NexoraRailItem("AI", Icons.Rounded.Insights, selectedScreen == "insights") { selectedScreen = "insights" }
                            Spacer(Modifier.weight(1f))
                        }
                    }

                    Scaffold(
                        containerColor = NexoraBackgroundLight,
                        bottomBar = {
                            if (!isWide && selectedScreen in listOf("home", "tasks", "goals", "insights")) {
                                NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                                    NexoraNavItem("Home", Icons.Rounded.Home, selectedScreen == "home") { selectedScreen = "home" }
                                    NexoraNavItem("Tasks", Icons.Rounded.CheckCircle, selectedScreen == "tasks") { selectedScreen = "tasks" }
                                    NexoraNavItem("Goals", Icons.Rounded.Flag, selectedScreen == "goals") { selectedScreen = "goals" }
                                    NexoraNavItem("AI", Icons.Rounded.Insights, selectedScreen == "insights") { selectedScreen = "insights" }
                                }
                            }
                        }
                    ) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                                when (selectedScreen) {
                                    "home" -> HomeScreen(
                                        tasks = tasks,
                                        goals = goals,
                                        progressHistory = progressHistory,
                                        onAddTask = { selectedScreen = "addTask" },
                                        onToggleTask = { task ->
                                            val index = tasks.indexOfFirst { it.id == task.id }
                                            if (index >= 0) {
                                                tasks[index] = task.copy(completed = !task.completed)
                                                scope.launch { repository.updateTask(tasks[index]) }
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
                                                tasks.clear()
                                                tasks.addAll(repository.observeTasks().first())
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
                                                tasks[index] = task.copy(completed = !task.completed)
                                                scope.launch { repository.updateTask(tasks[index]) }
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
                                        onEditGoal = { editingGoal = it; selectedScreen = "addGoal" },
                                        onOpenGoal = { selectedGoal = it; selectedScreen = "goalDetails" }
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
                                                    tasks.forEach { if (it.goalTitle == goal.title) repository.updateTask(it.copy(goalTitle = null)) }
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
                                                    tasks[index] = task.copy(completed = !task.completed)
                                                    scope.launch { repository.updateTask(tasks[index]) }
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
                                                val tasksFromRepo = repository.observeTasks().first()
                                                tasks.clear()
                                                tasks.addAll(tasksFromRepo)
                                                refreshGoalProgress()
                                                updateTodayProgress()
                                            }
                                        }
                                    )
                                    "aiAutomations" -> AiAutomationScreen(
                                        rules = emptyList(), // Load rules via viewmodel properly in production
                                        onBack = { selectedScreen = "insights" },
                                        onToggleRule = { /* Handle properly */ }
                                    )
                                    "aiBenchmarks" -> { /* Open screen properly */ }
                                    "addTask" -> AddTaskScreen(
                                        onBack = { taskGoal = null; selectedScreen = "tasks" },
                                        goals = goals,
                                        selectedGoal = taskGoal,
                                        onSave = { name, cat, dur, gt, p ->
                                            val newTask = PremiumTask(title = name, category = cat, duration = dur, goalTitle = gt, priority = p)
                                            scope.launch {
                                                repository.addTask(newTask)
                                                tasks.clear()
                                                tasks.addAll(repository.observeTasks().first())
                                                refreshGoalProgress()
                                                updateTodayProgress()
                                            }
                                            taskGoal = null
                                            selectedScreen = "tasks"
                                        }
                                    )
                                    "addGoal" -> AddGoalScreen(
                                        onBack = { editingGoal = null; selectedScreen = "goals" },
                                        onSave = { name, cat, td, _ ->
                                            scope.launch {
                                                if (editingGoal == null) {
                                                    repository.addGoal(NexoraGoal(title = name, category = cat, targetDate = td, progress = 0f))
                                                } else {
                                                    repository.updateGoal(editingGoal!!.copy(title = name, category = cat, targetDate = td))
                                                }
                                                goals.clear()
                                                goals.addAll(repository.observeGoals().first())
                                                editingGoal = null
                                                refreshGoalProgress()
                                                updateTodayProgress()
                                                selectedScreen = "goals"
                                            }
                                        },
                                        existingGoal = editingGoal
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
