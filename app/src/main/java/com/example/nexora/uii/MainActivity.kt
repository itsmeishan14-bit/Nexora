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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.nexora.ui.theme.NexoraTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {

            NexoraTheme {

                val tasks = remember {
                    mutableStateListOf(
                        PremiumTask(
                            "Complete Java assignment",
                            "Academic",
                            "30 minutes",
                            true
                        ),
                        PremiumTask(
                            "Study Operating Systems",
                            "Academic",
                            "45 minutes",
                            false
                        ),
                        PremiumTask(
                            "Build Nexora",
                            "Personal project",
                            "60 minutes",
                            false
                        ),
                        PremiumTask(
                            "Read 20 pages",
                            "Personal",
                            "25 minutes",
                            false
                        )
                    )
                }

                val goals = remember {
                    mutableStateListOf(
                        NexoraGoal(
                            "Master Java",
                            "Academic",
                            "September 30",
                            0.65f
                        ),
                        NexoraGoal(
                            "Build Nexora",
                            "Personal Project",
                            "October 15",
                            0.40f
                        ),
                        NexoraGoal(
                            "Read 5 Books",
                            "Personal",
                            "December 31",
                            0.20f
                        )
                    )
                }

                var selectedScreen by remember {
                    mutableStateOf("home")
                }

                var editingGoal by remember {
                    mutableStateOf<NexoraGoal?>(null)
                }

                Scaffold(

                    bottomBar = {

                        if (
                            selectedScreen != "addTask" &&
                            selectedScreen != "addGoal"
                        ) {

                            NavigationBar {

                                NavigationBarItem(
                                    selected = selectedScreen == "home",
                                    onClick = {
                                        selectedScreen = "home"
                                    },
                                    icon = {
                                        Icon(
                                            Icons.Default.Home,
                                            contentDescription = "Home"
                                        )
                                    },
                                    label = {
                                        Text("Home")
                                    }
                                )

                                NavigationBarItem(
                                    selected = selectedScreen == "tasks",
                                    onClick = {
                                        selectedScreen = "tasks"
                                    },
                                    icon = {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Tasks"
                                        )
                                    },
                                    label = {
                                        Text("Tasks")
                                    }
                                )

                                NavigationBarItem(
                                    selected = selectedScreen == "goals",
                                    onClick = {
                                        selectedScreen = "goals"
                                    },
                                    icon = {
                                        Icon(
                                            Icons.Default.Flag,
                                            contentDescription = "Goals"
                                        )
                                    },
                                    label = {
                                        Text("Goals")
                                    }
                                )

                                NavigationBarItem(
                                    selected = selectedScreen == "insights",
                                    onClick = {
                                        selectedScreen = "insights"
                                    },
                                    icon = {
                                        Icon(
                                            Icons.Default.Insights,
                                            contentDescription = "Insights"
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

                            "home" -> {

                                HomeScreen(
                                    tasks = tasks,
                                    onAddTask = {
                                        selectedScreen = "addTask"
                                    }
                                )
                            }

                            "tasks" -> {

                                TasksScreen(
                                    tasks = tasks,
                                    onAddTask = {
                                        selectedScreen = "addTask"
                                    }
                                )
                            }

                            "goals" -> {

                                GoalScreen(
                                    goals = goals,

                                    onAddGoal = {
                                        editingGoal = null
                                        selectedScreen = "addGoal"
                                    },

                                    onEditGoal = { goal ->
                                        editingGoal = goal
                                        selectedScreen = "addGoal"
                                    }
                                )
                            }

                            "insights" -> {

                                InsightScreen()
                            }

                            "addTask" -> {

                                AddTaskScreen(

                                    onBack = {
                                        selectedScreen = "tasks"
                                    },

                                    onSave = { title, category, duration ->

                                        tasks.add(
                                            PremiumTask(
                                                title = title,
                                                category = category,
                                                duration = duration,
                                                completed = false
                                            )
                                        )

                                        selectedScreen = "tasks"
                                    }
                                )
                            }

                            "addGoal" -> {

                                AddGoalScreen(

                                    existingGoal = editingGoal,

                                    onBack = {
                                        editingGoal = null
                                        selectedScreen = "goals"
                                    },

                                    onSave = {
                                            title,
                                            category,
                                            targetDate,
                                            progress ->

                                        if (editingGoal == null) {

                                            goals.add(
                                                NexoraGoal(
                                                    title = title,
                                                    category = category,
                                                    targetDate = targetDate,
                                                    progress = progress
                                                )
                                            )

                                        } else {

                                            val index =
                                                goals.indexOf(editingGoal)

                                            if (index >= 0) {

                                                goals[index] =
                                                    NexoraGoal(
                                                        title = title,
                                                        category = category,
                                                        targetDate = targetDate,
                                                        progress = progress
                                                    )
                                            }
                                        }

                                        editingGoal = null
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