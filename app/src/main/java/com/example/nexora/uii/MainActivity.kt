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

                // ============================================================
                // DATABASE
                // ============================================================

                val database = remember {
                    NexoraDatabase.getDatabase(context)
                }

                val repository = remember {
                    NexoraRepository(database)
                }

                val scope = rememberCoroutineScope()

                // ============================================================
                // APP STATE
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

                // ============================================================
                // UPDATE TODAY'S PROGRESS
                // ============================================================

                fun updateTodayProgress() {

                    val today = LocalDate.now()

                    val completedTasks =
                        tasks.count { it.completed }

                    val totalTasks =
                        tasks.size

                    val goalsWorkedOn =
                        tasks
                            .mapNotNull { it.goalTitle }
                            .distinct()
                            .size

                    val todayProgress =
                        DailyProgress(
                            date = today,
                            tasksPlanned = totalTasks,
                            tasksCompleted = completedTasks,
                            focusMinutes = 0,
                            goalsWorkedOn = goalsWorkedOn,
                            carriedTasks = 0
                        )

                    val existingIndex =
                        progressHistory.indexOfFirst {
                            it.date == today
                        }

                    if (existingIndex >= 0) {

                        progressHistory[existingIndex] =
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
                                tasksPlanned = totalTasks,
                                tasksCompleted = completedTasks,
                                focusMinutes = 0,
                                goalsWorkedOn = goalsWorkedOn,
                                carriedTasks = 0
                            )
                        )
                    }
                }

                // ============================================================
                // REFRESH GOAL PROGRESS
                // ============================================================

                fun refreshGoalProgress() {

                    goals.indices.forEach { index ->

                        val goal =
                            goals[index]

                        val newProgress =
                            calculateGoalProgress(
                                goal = goal,
                                tasks = tasks
                            )

                        val updatedGoal =
                            goal.copy(
                                progress = newProgress
                            )

                        goals[index] =
                            updatedGoal

                        if (
                            selectedGoal?.id ==
                            updatedGoal.id
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

                    // ========================================================
                    // IMPORTANT
                    //
                    // THERE IS NO SEEDING.
                    //
                    // Room is the single source of truth.
                    // ========================================================

                    tasks.clear()
                    tasks.addAll(savedTasks)

                    goals.clear()
                    goals.addAll(savedGoals)

                    progressHistory.clear()

                    progressHistory.addAll(
                        savedProgress.map { entity ->

                            DailyProgress(
                                date =
                                    LocalDate.parse(
                                        entity.date
                                    ),
                                tasksPlanned =
                                    entity.tasksPlanned,
                                tasksCompleted =
                                    entity.tasksCompleted,
                                focusMinutes =
                                    entity.focusMinutes,
                                goalsWorkedOn =
                                    entity.goalsWorkedOn,
                                carriedTasks =
                                    entity.carriedTasks
                            )
                        }
                    )

                    // ========================================================
                    // RECALCULATE GOAL PROGRESS
                    // ========================================================

                    goals.indices.forEach { index ->

                        val goal =
                            goals[index]

                        val calculatedProgress =
                            calculateGoalProgress(
                                goal = goal,
                                tasks = tasks
                            )

                        val updatedGoal =
                            goal.copy(
                                progress =
                                    calculatedProgress
                            )

                        goals[index] =
                            updatedGoal

                        scope.launch {

                            repository.updateGoal(
                                updatedGoal
                            )
                        }
                    }

                    // ========================================================
                    // UPDATE TODAY
                    // ========================================================

                    updateTodayProgress()
                }

                // ============================================================
                // MAIN SCAFFOLD
                // ============================================================

                Scaffold(

                    bottomBar = {

                        if (
                            selectedScreen != "addTask" &&
                            selectedScreen != "addGoal" &&
                            selectedScreen != "goalDetails"
                        ) {

                            NavigationBar {

                                // HOME
                                NavigationBarItem(

                                    selected =
                                        selectedScreen == "home",

                                    onClick = {
                                        selectedScreen =
                                            "home"
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

                                // TASKS
                                NavigationBarItem(

                                    selected =
                                        selectedScreen == "tasks",

                                    onClick = {
                                        selectedScreen =
                                            "tasks"
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

                                // GOALS
                                NavigationBarItem(

                                    selected =
                                        selectedScreen == "goals",

                                    onClick = {
                                        selectedScreen =
                                            "goals"
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

                                // INSIGHTS
                                NavigationBarItem(

                                    selected =
                                        selectedScreen == "insights",

                                    onClick = {
                                        selectedScreen =
                                            "insights"
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
                                        Text("Insights")
                                    }
                                )
                            }
                        }
                    }

                ) { paddingValues ->

                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(
                                    paddingValues
                                )
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

                                    onAddTask = {

                                        taskGoal = null

                                        selectedScreen =
                                            "addTask"
                                    },

                                    onToggleTask = { task ->

                                        val index =
                                            tasks.indexOfFirst {
                                                it.id == task.id
                                            }

                                        if (index >= 0) {

                                            val updatedTask =
                                                task.copy(
                                                    completed =
                                                        !task.completed
                                                )

                                            tasks[index] =
                                                updatedTask

                                            scope.launch {

                                                repository.updateTask(
                                                    updatedTask
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

                                        selectedScreen =
                                            "addTask"
                                    },

                                    onToggleTask = { task ->

                                        val index =
                                            tasks.indexOfFirst {
                                                it.id == task.id
                                            }

                                        if (index >= 0) {

                                            val updatedTask =
                                                task.copy(
                                                    completed =
                                                        !task.completed
                                                )

                                            tasks[index] =
                                                updatedTask

                                            scope.launch {

                                                repository.updateTask(
                                                    updatedTask
                                                )
                                            }

                                            refreshGoalProgress()
                                            updateTodayProgress()
                                        }
                                    },

                                    // ==================================================
                                    // DELETE TASK
                                    // ==================================================

                                    onDeleteTask = { task ->

                                        scope.launch {

                                            // Delete from Room FIRST
                                            repository.deleteTask(
                                                task
                                            )

                                            // Then remove from UI
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

                                        selectedScreen =
                                            "addGoal"
                                    },

                                    onEditGoal = { goal ->

                                        editingGoal =
                                            goal

                                        selectedGoal =
                                            goal

                                        selectedScreen =
                                            "addGoal"
                                    },

                                    onOpenGoal = { goal ->

                                        selectedGoal =
                                            goal

                                        selectedScreen =
                                            "goalDetails"
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

                                            selectedGoal =
                                                null

                                            selectedScreen =
                                                "goals"
                                        },

                                        onEdit = {

                                            editingGoal =
                                                goal

                                            selectedScreen =
                                                "addGoal"
                                        },

                                        // ==================================================
                                        // DELETE GOAL
                                        // ==================================================

                                        onDelete = {

                                            scope.launch {

                                                // Delete goal
                                                repository.deleteGoal(
                                                    goal
                                                )

                                                // Remove goal relation
                                                tasks
                                                    .filter {
                                                        it.goalTitle ==
                                                                goal.title
                                                    }
                                                    .forEach { task ->

                                                        repository.updateTask(

                                                            task.copy(
                                                                goalTitle =
                                                                    null
                                                            )
                                                        )
                                                    }

                                                // Update UI
                                                val updatedTasks =
                                                    tasks.map { task ->

                                                        if (
                                                            task.goalTitle ==
                                                            goal.title
                                                        ) {

                                                            task.copy(
                                                                goalTitle =
                                                                    null
                                                            )

                                                        } else {

                                                            task
                                                        }
                                                    }

                                                tasks.clear()

                                                tasks.addAll(
                                                    updatedTasks
                                                )

                                                goals.removeAll {
                                                    it.id ==
                                                            goal.id
                                                }

                                                selectedGoal =
                                                    null

                                                taskGoal =
                                                    null

                                                updateTodayProgress()

                                                selectedScreen =
                                                    "goals"
                                            }
                                        },

                                        // ==================================================
                                        // TOGGLE TASK INSIDE GOAL
                                        // ==================================================

                                        onToggleTask = { task ->

                                            val index =
                                                tasks.indexOfFirst {
                                                    it.id == task.id
                                                }

                                            if (index >= 0) {

                                                val updatedTask =
                                                    task.copy(
                                                        completed =
                                                            !task.completed
                                                    )

                                                tasks[index] =
                                                    updatedTask

                                                scope.launch {

                                                    repository.updateTask(
                                                        updatedTask
                                                    )
                                                }

                                                refreshGoalProgress()
                                                updateTodayProgress()
                                            }
                                        },

                                        // ==================================================
                                        // ADD TASK TO GOAL
                                        // ==================================================

                                        onAddTask = {

                                            taskGoal =
                                                goal

                                            selectedScreen =
                                                "addTask"
                                        }
                                    )
                                }
                            }

                            // ==================================================
                            // INSIGHTS
                            // ==================================================

                            "insights" -> {

                                InsightScreen()
                            }

                            // ==================================================
                            // ADD TASK
                            // ==================================================

                            "addTask" -> {

                                AddTaskScreen(

                                    onBack = {

                                        taskGoal = null

                                        selectedScreen =
                                            "tasks"
                                    },

                                    goals = goals,

                                    selectedGoal =
                                        taskGoal,

                                    // ==================================================
                                    // SAVE TASK
                                    // ==================================================

                                    onSave = {
                                            title,
                                            category,
                                            duration,
                                            goalTitle,
                                            priority ->

                                        val newTask =
                                            PremiumTask(

                                                title =
                                                    title,

                                                category =
                                                    category,

                                                duration =
                                                    duration,

                                                goalTitle =
                                                    goalTitle,

                                                priority =
                                                    priority,

                                                completed =
                                                    false
                                            )

                                        scope.launch {

                                            // Save to Room
                                            repository.addTask(
                                                newTask
                                            )

                                            // Reload from Room
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

                                        taskGoal =
                                            null

                                        selectedScreen =
                                            "tasks"
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

                                        editingGoal =
                                            null

                                        selectedGoal =
                                            null

                                        selectedScreen =
                                            "goals"
                                    },

                                    onSave = {
                                            title,
                                            category,
                                            targetDate,
                                            _ ->

                                        // ==================================================
                                        // NEW GOAL
                                        // ==================================================

                                        if (
                                            editingGoal ==
                                            null
                                        ) {

                                            val newGoal =
                                                NexoraGoal(

                                                    title =
                                                        title,

                                                    category =
                                                        category,

                                                    targetDate =
                                                        targetDate,

                                                    progress =
                                                        0f
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

                                        }

                                        // ==================================================
                                        // EDIT GOAL
                                        // ==================================================

                                        else {

                                            val oldGoal =
                                                editingGoal

                                            val index =
                                                goals.indexOfFirst {

                                                    it.id ==
                                                            oldGoal?.id
                                                }

                                            if (index >= 0) {

                                                val previousTitle =
                                                    goals[index].title

                                                val updatedGoal =
                                                    goals[index].copy(

                                                        title =
                                                            title,

                                                        category =
                                                            category,

                                                        targetDate =
                                                            targetDate
                                                    )

                                                goals[index] =
                                                    updatedGoal

                                                if (
                                                    selectedGoal?.id ==
                                                    updatedGoal.id
                                                ) {

                                                    selectedGoal =
                                                        updatedGoal
                                                }

                                                scope.launch {

                                                    repository.updateGoal(
                                                        updatedGoal
                                                    )

                                                    // Update tasks linked
                                                    // to the old goal title.
                                                    tasks
                                                        .filter {

                                                            it.goalTitle ==
                                                                    previousTitle
                                                        }
                                                        .forEach { task ->

                                                            val updatedTask =
                                                                task.copy(

                                                                    goalTitle =
                                                                        title
                                                                )

                                                            repository.updateTask(
                                                                updatedTask
                                                            )

                                                            val taskIndex =
                                                                tasks.indexOfFirst {

                                                                    it.id ==
                                                                            task.id
                                                                }

                                                            if (
                                                                taskIndex >=
                                                                0
                                                            ) {

                                                                tasks[taskIndex] =
                                                                    updatedTask
                                                            }
                                                        }

                                                    refreshGoalProgress()
                                                    updateTodayProgress()
                                                }
                                            }
                                        }

                                        editingGoal =
                                            null

                                        selectedGoal =
                                            null

                                        selectedScreen =
                                            "goals"
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