package com.example.nexora.uii

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.nexora.ai.AiAction
import com.example.nexora.ai.AiActionExecutor
import com.example.nexora.ai.AiContextBuilder
import com.example.nexora.ai.AiRecommendation
import com.example.nexora.ai.AiRecommendationType
import com.example.nexora.ai.LocalNexoraAiService
import com.example.nexora.ai.NexoraAiEngine
import com.example.nexora.data.DailyProgressEntity
import com.example.nexora.data.NexoraDatabase
import com.example.nexora.data.NexoraRepository
import com.example.nexora.ui.theme.NexoraTheme
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {

            NexoraTheme {

                val context = LocalContext.current

                val database = remember {
                    NexoraDatabase.getDatabase(context)
                }

                val repository = remember {
                    NexoraRepository(database)
                }

                // ============================================================
                // NEXORA AI
                // ============================================================

                val aiEngine = remember {

                    val contextBuilder =
                        AiContextBuilder(repository)

                    // 1. Define Providers
                    val localProvider = com.example.nexora.ai.LocalAiProvider()
                    
                    // 2. Manage Providers (Cloud config can be loaded securely here)
                    val providerManager = com.example.nexora.ai.AiProviderManager(
                        localProvider = localProvider
                    )

                    // 3. Orchestrate with Service
                    val aiService = com.example.nexora.ai.LocalNexoraAiService(
                        providerManager = providerManager
                    )
                    
                    val actionExecutor = AiActionExecutor(repository)
                    
                    val toolRegistry = com.example.nexora.ai.AiToolRegistry(repository, actionExecutor)

                    NexoraAiEngine(
                        contextBuilder = contextBuilder,
                        aiService = aiService,
                        actionExecutor = actionExecutor,
                        toolRegistry = toolRegistry,
                        repository = repository
                    )
                }

                val scope = rememberCoroutineScope()

                // ============================================================
                // APP DATA
                // ============================================================

                val tasks = remember {
                    mutableStateListOf<PremiumTask>()
                }

                val goals = remember {
                    mutableStateListOf<NexoraGoal>()
                }

                val progressHistory = remember {
                    mutableStateListOf<DailyProgress>()
                }

                // ============================================================
                // NAVIGATION
                // ============================================================

                var selectedScreen by remember {
                    mutableStateOf("home")
                }

                var selectedGoal by remember {
                    mutableStateOf<NexoraGoal?>(null)
                }

                var editingGoal by remember {
                    mutableStateOf<NexoraGoal?>(null)
                }

                var taskGoal by remember {
                    mutableStateOf<NexoraGoal?>(null)
                }

                var topAiInsight by remember {
                    mutableStateOf<String?>(null)
                }

                var homeProactiveInsight by remember {
                    mutableStateOf<AiRecommendation?>(null)
                }

                var homeProposedAction by remember {
                    mutableStateOf<AiAction?>(null)
                }

                // ============================================================
                // LOAD DATA FROM ROOM
                // ============================================================

                LaunchedEffect(Unit) {

                    val savedTasks =
                        repository
                            .observeTasks()
                            .first()

                    val savedGoals =
                        repository
                            .observeGoals()
                            .first()

                    val savedProgress =
                        repository
                            .observeDailyProgress()
                            .first()

                    tasks.clear()
                    tasks.addAll(savedTasks)

                    goals.clear()
                    goals.addAll(savedGoals)

                    progressHistory.clear()

                    progressHistory.addAll(
                        savedProgress.map { item ->

                            DailyProgress(
                                date = LocalDate.parse(item.date),
                                tasksPlanned = item.tasksPlanned,
                                tasksCompleted = item.tasksCompleted,
                                focusMinutes = item.focusMinutes,
                                goalsWorkedOn = item.goalsWorkedOn,
                                carriedTasks = item.carriedTasks
                            )
                        }
                    )

                    // Fetch top AI insight
                    topAiInsight = aiEngine.getTopInsight()

                    val proactive = aiEngine.getProactiveInsights()
                    homeProactiveInsight = proactive.firstOrNull { it.priority >= com.example.nexora.ai.AiPriority.HIGH }
                    
                    // Simple heuristic for Home proposal: if many incomplete tasks, suggest rescheduling
                    val context = aiEngine.getContext()
                    if (context.incompleteTasks.size >= 8) {
                        homeProposedAction = AiAction(
                            type = com.example.nexora.ai.AiActionType.RESCHEDULE_TASK,
                            title = "High Workload Detected",
                            description = "You have ${context.incompleteTasks.size} tasks. Should I move lower priority items to tomorrow?",
                            reason = "Too many tasks today reduces focus.",
                            requiresConfirmation = true
                        )
                    }
                }

                // ============================================================
                // TODAY'S PROGRESS
                // ============================================================

                fun updateTodayProgress() {

                    val today = LocalDate.now()

                    val completed =
                        tasks.count { it.completed }

                    val total =
                        tasks.size

                    val goalsWorkedOn =
                        tasks
                            .mapNotNull { it.goalTitle }
                            .distinct()
                            .size

                    val todayProgress =
                        DailyProgress(
                            date = today,
                            tasksPlanned = total,
                            tasksCompleted = completed,
                            focusMinutes = 0,
                            goalsWorkedOn = goalsWorkedOn,
                            carriedTasks = 0
                        )

                    val index =
                        progressHistory.indexOfFirst {
                            it.date == today
                        }

                    if (index >= 0) {

                        progressHistory[index] =
                            todayProgress

                    } else {

                        progressHistory.add(
                            todayProgress
                        )
                    }

                    scope.launch {

                        repository.saveDailyProgress(
                            DailyProgressEntity(
                                date = today.toString(),
                                tasksPlanned = total,
                                tasksCompleted = completed,
                                focusMinutes = 0,
                                goalsWorkedOn = goalsWorkedOn,
                                carriedTasks = 0
                            )
                        )

                        // Refresh proactive insight
                        val proactive = aiEngine.getProactiveInsights()
                        homeProactiveInsight = proactive.firstOrNull { it.priority >= com.example.nexora.ai.AiPriority.HIGH }
                    }
                }

                // ============================================================
                // GOAL PROGRESS
                // ============================================================

                fun refreshGoalProgress() {

                    goals.forEachIndexed { index, goal ->

                        val progress =
                            calculateGoalProgress(
                                goal = goal,
                                tasks = tasks
                            )

                        val updatedGoal =
                            goal.copy(
                                progress = progress
                            )

                        goals[index] =
                            updatedGoal

                        if (
                            selectedGoal?.id ==
                            goal.id
                        ) {

                            selectedGoal =
                                updatedGoal
                        }

                        scope.launch {

                            repository.updateGoal(
                                updatedGoal
                            )
                        }
                    }
                }

                // ============================================================
                // APP
                // ============================================================

                Scaffold(

                    bottomBar = {

                        if (
                            selectedScreen != "addTask" &&
                            selectedScreen != "addGoal" &&
                            selectedScreen != "goalDetails" &&
                            selectedScreen != "aiGoalDecomposer"
                        ) {

                            NavigationBar {

                                NavigationBarItem(
                                    selected =
                                        selectedScreen == "home",

                                    onClick = {
                                        selectedScreen = "home"
                                    },

                                    icon = {
                                        Icon(
                                            imageVector =
                                                Icons.Default.Home,
                                            contentDescription =
                                                "Home"
                                        )
                                    },

                                    label = {
                                        Text("Home")
                                    }
                                )

                                NavigationBarItem(
                                    selected =
                                        selectedScreen == "tasks",

                                    onClick = {
                                        selectedScreen = "tasks"
                                    },

                                    icon = {
                                        Icon(
                                            imageVector =
                                                Icons.Default.CheckCircle,
                                            contentDescription =
                                                "Tasks"
                                        )
                                    },

                                    label = {
                                        Text("Tasks")
                                    }
                                )

                                NavigationBarItem(
                                    selected =
                                        selectedScreen == "goals",

                                    onClick = {
                                        selectedScreen = "goals"
                                    },

                                    icon = {
                                        Icon(
                                            imageVector =
                                                Icons.Default.Flag,
                                            contentDescription =
                                                "Goals"
                                        )
                                    },

                                    label = {
                                        Text("Goals")
                                    }
                                )

                                NavigationBarItem(
                                    selected =
                                        selectedScreen == "insights",

                                    onClick = {
                                        selectedScreen = "insights"
                                    },

                                    icon = {
                                        Icon(
                                            imageVector =
                                                Icons.Default.Insights,
                                            contentDescription =
                                                "Insights"
                                        )
                                    },

                                    label = {
                                        Text("AI")
                                    }
                                )
                            }
                        }
                    }

                ) { paddingValues ->

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {

                        when (selectedScreen) {

                            // ==================================================
                            // HOME
                            // ==================================================

                            "home" -> {

                                HomeScreen(

                                    tasks = tasks,

                                    progressHistory =
                                        progressHistory,

                                    topPattern = topAiInsight,
                                    
                                    proactiveInsight = homeProactiveInsight,
                                    
                                    proposedAction = homeProposedAction,
                                    
                                    onApproveAction = { action ->
                                        scope.launch {
                                            aiEngine.executeAction(action)
                                            homeProposedAction = null
                                            
                                            // Refresh data
                                            val savedTasks = repository.observeTasks().first()
                                            tasks.clear()
                                            tasks.addAll(savedTasks)
                                            updateTodayProgress()
                                        }
                                    },
                                    
                                    onDismissAction = {
                                        homeProposedAction = null
                                    },

                                    onAddTask = {

                                        taskGoal = null
                                        selectedScreen = "addTask"
                                    },

                                    onToggleTask = { task ->

                                        val index =
                                            tasks.indexOfFirst {
                                                it.id == task.id
                                            }

                                        if (index >= 0) {

                                            val updated =
                                                task.copy(
                                                    completed =
                                                        !task.completed
                                                )

                                            tasks[index] =
                                                updated

                                            scope.launch {

                                                repository.updateTask(
                                                    updated
                                                )
                                            }

                                            refreshGoalProgress()
                                            updateTodayProgress()
                                        }
                                    }
                                )
                            }

                            // ==================================================
                            // TASKS
                            // ==================================================

                            "tasks" -> {

                                TasksScreen(

                                    tasks = tasks,

                                    onAddTask = {

                                        taskGoal = null
                                        selectedScreen = "addTask"
                                    },

                                    onToggleTask = { task ->

                                        val index =
                                            tasks.indexOfFirst {
                                                it.id == task.id
                                            }

                                        if (index >= 0) {

                                            val updated =
                                                task.copy(
                                                    completed =
                                                        !task.completed
                                                )

                                            tasks[index] =
                                                updated

                                            scope.launch {

                                                repository.updateTask(
                                                    updated
                                                )
                                            }

                                            refreshGoalProgress()
                                            updateTodayProgress()
                                        }
                                    },

                                    onDeleteTask = { task ->

                                        scope.launch {

                                            repository.deleteTask(
                                                task
                                            )

                                            tasks.removeAll {
                                                it.id == task.id
                                            }

                                            refreshGoalProgress()
                                            updateTodayProgress()
                                        }
                                    }
                                )
                            }

                            // ==================================================
                            // GOALS
                            // ==================================================

                            "goals" -> {

                                GoalScreen(

                                    goals = goals,

                                    onAddGoal = {

                                        editingGoal = null
                                        selectedGoal = null
                                        selectedScreen = "addGoal"
                                    },

                                    onEditGoal = { goal ->

                                        editingGoal = goal
                                        selectedGoal = goal
                                        selectedScreen = "addGoal"
                                    },

                                    onOpenGoal = { goal ->

                                        selectedGoal = goal
                                        selectedScreen = "goalDetails"
                                    }
                                )
                            }

                            // ==================================================
                            // GOAL DETAILS
                            // ==================================================

                            "goalDetails" -> {

                                selectedGoal?.let { goal ->

                                    GoalDetailsScreen(

                                        goal = goal,

                                        relatedTasks =
                                            tasks.filter {
                                                it.goalTitle ==
                                                        goal.title
                                            },

                                        onBack = {

                                            selectedGoal = null
                                            selectedScreen = "goals"
                                        },

                                        onEdit = {

                                            editingGoal = goal
                                            selectedScreen = "addGoal"
                                        },

                                        onDelete = {

                                            scope.launch {

                                                repository.deleteGoal(
                                                    goal
                                                )

                                                val linkedTasks =
                                                    tasks.filter {
                                                        it.goalTitle ==
                                                                goal.title
                                                    }

                                                linkedTasks.forEach { task ->

                                                    repository.updateTask(
                                                        task.copy(
                                                            goalTitle = null
                                                        )
                                                    )
                                                }

                                                goals.removeAll {
                                                    it.id == goal.id
                                                }

                                                tasks.replaceAll { task ->

                                                    if (
                                                        task.goalTitle ==
                                                        goal.title
                                                    ) {

                                                        task.copy(
                                                            goalTitle = null
                                                        )

                                                    } else {

                                                        task
                                                    }
                                                }

                                                selectedGoal = null
                                                taskGoal = null

                                                updateTodayProgress()

                                                selectedScreen = "goals"
                                            }
                                        },

                                        onToggleTask = { task ->

                                            val index =
                                                tasks.indexOfFirst {
                                                    it.id == task.id
                                                }

                                            if (index >= 0) {

                                                val updated =
                                                    task.copy(
                                                        completed =
                                                            !task.completed
                                                    )

                                                tasks[index] =
                                                    updated

                                                scope.launch {

                                                    repository.updateTask(
                                                        updated
                                                    )
                                                }

                                                refreshGoalProgress()
                                                updateTodayProgress()
                                            }
                                        },

                                        onAddTask = {

                                            taskGoal = goal
                                            selectedScreen = "addTask"
                                        }
                                    )
                                }
                            }

                            // ==================================================
                            // AI
                            // ==================================================

                            "insights" -> {

                                AiScreen(

                                    engine = aiEngine,

                                    onOpenGoalDecomposer = {

                                        selectedScreen =
                                            "aiGoalDecomposer"
                                    },

                                    onRecommendationAction = { recommendation ->

                                        recommendation.relatedGoalId?.let { goalId ->

                                            val goal =
                                                goals.find {
                                                    it.id == goalId
                                                }

                                            if (goal != null) {

                                                selectedGoal = goal
                                                selectedScreen = "goalDetails"
                                            }
                                        }

                                        recommendation.relatedTaskId?.let { _ ->

                                            selectedScreen = "tasks"
                                        }
                                    },

                                    onTaskAction = { taskId ->
                                        selectedScreen = "tasks"
                                    }
                                )
                            }

                            // ==================================================
                            // AI GOAL DECOMPOSER
                            // ==================================================

                            "aiGoalDecomposer" -> {

                                AiGoalDecomposerScreen(

                                    engine = aiEngine,

                                    onBack = {

                                        selectedScreen =
                                            "insights"
                                    },

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
                            }

                            // ==================================================
                            // ADD TASK
                            // ==================================================

                            "addTask" -> {

                                AddTaskScreen(

                                    onBack = {

                                        taskGoal = null
                                        selectedScreen = "tasks"
                                    },

                                    goals = goals,

                                    selectedGoal = taskGoal,

                                    onSave = {
                                            title,
                                            category,
                                            duration,
                                            goalTitle,
                                            priority ->

                                        val newTask =
                                            PremiumTask(
                                                title = title,
                                                category = category,
                                                duration = duration,
                                                goalTitle = goalTitle,
                                                priority = priority,
                                                completed = false
                                            )

                                        scope.launch {

                                            repository.addTask(
                                                newTask
                                            )

                                            val savedTasks =
                                                repository
                                                    .observeTasks()
                                                    .first()

                                            tasks.clear()
                                            tasks.addAll(
                                                savedTasks
                                            )

                                            refreshGoalProgress()
                                            updateTodayProgress()
                                        }

                                        taskGoal = null
                                        selectedScreen = "tasks"
                                    }
                                )
                            }

                            // ==================================================
                            // ADD / EDIT GOAL
                            // ==================================================

                            "addGoal" -> {

                                AddGoalScreen(

                                    existingGoal =
                                        editingGoal,

                                    onBack = {

                                        editingGoal = null
                                        selectedGoal = null
                                        selectedScreen = "goals"
                                    },

                                    onSave = {
                                            title,
                                            category,
                                            targetDate,
                                            _ ->

                                        if (
                                            editingGoal == null
                                        ) {

                                            val newGoal =
                                                NexoraGoal(
                                                    title = title,
                                                    category = category,
                                                    targetDate =
                                                        targetDate,
                                                    progress = 0f
                                                )

                                            scope.launch {

                                                repository.addGoal(
                                                    newGoal
                                                )

                                                val savedGoals =
                                                    repository
                                                        .observeGoals()
                                                        .first()

                                                goals.clear()
                                                goals.addAll(
                                                    savedGoals
                                                )

                                                refreshGoalProgress()
                                                updateTodayProgress()
                                            }

                                        } else {

                                            val oldGoal =
                                                editingGoal!!

                                            val updatedGoal =
                                                oldGoal.copy(
                                                    title = title,
                                                    category = category,
                                                    targetDate =
                                                        targetDate
                                                )

                                            val index =
                                                goals.indexOfFirst {
                                                    it.id ==
                                                            oldGoal.id
                                                }

                                            if (index >= 0) {

                                                goals[index] =
                                                    updatedGoal
                                            }

                                            scope.launch {

                                                repository.updateGoal(
                                                    updatedGoal
                                                )
                                            }
                                        }

                                        editingGoal = null
                                        selectedGoal = null

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
}