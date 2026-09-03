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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.nexora.ui.theme.NexoraTheme
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {

            NexoraTheme {

                // --------------------------------
                // TASKS
                // --------------------------------

                val tasks = remember {

                    mutableStateListOf(

                        PremiumTask(
                            title = "Complete Java assignment",
                            category = "Academic",
                            duration = "30 minutes",
                            goalTitle = "Master Java",
                            completed = true
                        ),

                        PremiumTask(
                            title = "Study Java inheritance",
                            category = "Academic",
                            duration = "40 minutes",
                            goalTitle = "Master Java",
                            completed = false
                        ),

                        PremiumTask(
                            title = "Practice Java problems",
                            category = "Academic",
                            duration = "30 minutes",
                            goalTitle = "Master Java",
                            completed = false
                        ),

                        PremiumTask(
                            title = "Study Operating Systems",
                            category = "Academic",
                            duration = "45 minutes",
                            goalTitle = null,
                            completed = false
                        ),

                        PremiumTask(
                            title = "Build Nexora",
                            category = "Personal Project",
                            duration = "60 minutes",
                            goalTitle = "Build Nexora",
                            completed = false
                        ),

                        PremiumTask(
                            title = "Design Nexora dashboard",
                            category = "Personal Project",
                            duration = "45 minutes",
                            goalTitle = "Build Nexora",
                            completed = false
                        ),

                        PremiumTask(
                            title = "Read 20 pages",
                            category = "Personal",
                            duration = "25 minutes",
                            goalTitle = "Read 5 Books",
                            completed = false
                        )
                    )
                }

                // --------------------------------
                // GOALS
                // --------------------------------

                val goals = remember {

                    mutableStateListOf(

                        NexoraGoal(
                            title = "Master Java",
                            category = "Academic",
                            targetDate = "September 30",
                            progress = 0f
                        ),

                        NexoraGoal(
                            title = "Build Nexora",
                            category = "Personal Project",
                            targetDate = "October 15",
                            progress = 0f
                        ),

                        NexoraGoal(
                            title = "Read 5 Books",
                            category = "Personal",
                            targetDate = "December 31",
                            progress = 0f
                        )
                    )
                }

                // --------------------------------
                // DAILY PROGRESS HISTORY
                // --------------------------------

                val progressHistory = remember {

                    mutableStateListOf<DailyProgress>()
                }

                // --------------------------------
                // NAVIGATION STATE
                // --------------------------------

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

                // --------------------------------
                // UPDATE TODAY'S PROGRESS
                // --------------------------------

                fun updateTodayProgress() {

                    val today = LocalDate.now()

                    val completedTasks = tasks.count {
                        it.completed
                    }

                    val totalTasks = tasks.size

                    val goalsWorkedOn = tasks
                        .filter {
                            it.goalTitle != null
                        }
                        .mapNotNull {
                            it.goalTitle
                        }
                        .distinct()
                        .size

                    val todayProgress = DailyProgress(
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
                }

                // --------------------------------
                // GOAL PROGRESS
                // --------------------------------

                fun refreshGoalProgress() {

                    goals.indices.forEach { index ->

                        val goal = goals[index]

                        val newProgress =
                            calculateGoalProgress(
                                goal = goal,
                                tasks = tasks
                            )

                        val updatedGoal =
                            goal.copy(
                                progress = newProgress
                            )

                        goals[index] = updatedGoal

                        if (
                            selectedGoal?.title ==
                            goal.title
                        ) {

                            selectedGoal =
                                updatedGoal
                        }
                    }
                }

                // --------------------------------
                // INITIAL DATA CALCULATION
                // --------------------------------
                //
                // IMPORTANT:
                // Do not modify Compose state directly
                // during composition.
                //

                LaunchedEffect(Unit) {

                    refreshGoalProgress()
                    updateTodayProgress()
                }

                // --------------------------------
                // APP
                // --------------------------------

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

                                // TASKS

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

                                // GOALS

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

                                // INSIGHTS

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
                                        Text("Insights")
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

                            // ====================================
                            // HOME
                            // ====================================

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
                                            tasks.indexOf(task)

                                        if (index >= 0) {

                                            tasks[index] =
                                                task.copy(
                                                    completed =
                                                        !task.completed
                                                )

                                            refreshGoalProgress()

                                            updateTodayProgress()
                                        }
                                    }
                                )
                            }

                            // ====================================
                            // TASKS
                            // ====================================

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
                                            tasks.indexOf(task)

                                        if (index >= 0) {

                                            tasks[index] =
                                                task.copy(
                                                    completed =
                                                        !task.completed
                                                )

                                            refreshGoalProgress()

                                            updateTodayProgress()
                                        }
                                    }
                                )
                            }

                            // ====================================
                            // GOALS
                            // ====================================

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

                                        editingGoal = goal

                                        selectedGoal = goal

                                        selectedScreen =
                                            "addGoal"
                                    },

                                    onOpenGoal = { goal ->

                                        selectedGoal = goal

                                        selectedScreen =
                                            "goalDetails"
                                    }
                                )
                            }

                            // ====================================
                            // GOAL DETAILS
                            // ====================================

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

                                            selectedScreen =
                                                "goals"
                                        },

                                        onEdit = {

                                            editingGoal = goal

                                            selectedScreen =
                                                "addGoal"
                                        },

                                        onDelete = {

                                            goals.remove(goal)

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

                                            selectedGoal = null

                                            taskGoal = null

                                            refreshGoalProgress()

                                            updateTodayProgress()

                                            selectedScreen =
                                                "goals"
                                        },

                                        onToggleTask = { task ->

                                            val index =
                                                tasks.indexOf(task)

                                            if (index >= 0) {

                                                tasks[index] =
                                                    task.copy(
                                                        completed =
                                                            !task.completed
                                                    )

                                                refreshGoalProgress()

                                                updateTodayProgress()
                                            }
                                        },

                                        onAddTask = {

                                            taskGoal = goal

                                            selectedScreen =
                                                "addTask"
                                        }
                                    )
                                }
                            }

                            // ====================================
                            // INSIGHTS
                            // ====================================

                            "insights" -> {

                                InsightScreen()
                            }

                            // ====================================
                            // ADD TASK
                            // ====================================

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

                                    onSave = {
                                            title,
                                            category,
                                            duration,
                                            goalTitle ->

                                        tasks.add(

                                            PremiumTask(
                                                title = title,
                                                category = category,
                                                duration = duration,
                                                goalTitle =
                                                    goalTitle,
                                                completed = false
                                            )
                                        )

                                        refreshGoalProgress()

                                        updateTodayProgress()

                                        taskGoal = null

                                        selectedScreen =
                                            "tasks"
                                    }
                                )
                            }

                            // ====================================
                            // ADD / EDIT GOAL
                            // ====================================

                            "addGoal" -> {

                                AddGoalScreen(

                                    existingGoal =
                                        editingGoal,

                                    onBack = {

                                        editingGoal = null

                                        selectedGoal = null

                                        selectedScreen =
                                            "goals"
                                    },

                                    onSave = {
                                            title,
                                            category,
                                            targetDate,
                                            _ ->

                                        if (
                                            editingGoal == null
                                        ) {

                                            goals.add(

                                                NexoraGoal(
                                                    title = title,
                                                    category =
                                                        category,
                                                    targetDate =
                                                        targetDate,
                                                    progress = 0f
                                                )
                                            )

                                        } else {

                                            val index =
                                                goals.indexOf(
                                                    editingGoal
                                                )

                                            if (index >= 0) {

                                                val oldGoal =
                                                    goals[index]

                                                goals[index] =
                                                    NexoraGoal(
                                                        title =
                                                            title,
                                                        category =
                                                            category,
                                                        targetDate =
                                                            targetDate,
                                                        progress =
                                                            oldGoal.progress
                                                    )
                                            }
                                        }

                                        refreshGoalProgress()

                                        updateTodayProgress()

                                        editingGoal = null

                                        selectedGoal = null

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