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
import androidx.compose.runtime.Composable
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
                            title = "Complete Java assignment",
                            category = "Academic",
                            duration = "30 minutes",
                            completed = true
                        ),

                        PremiumTask(
                            title = "Study Operating Systems",
                            category = "Academic",
                            duration = "45 minutes",
                            completed = false
                        ),

                        PremiumTask(
                            title = "Build Nexora",
                            category = "Personal project",
                            duration = "60 minutes",
                            completed = false
                        ),

                        PremiumTask(
                            title = "Read 20 pages",
                            category = "Personal",
                            duration = "25 minutes",
                            completed = false
                        )
                    )
                }

                var selectedScreen by remember {
                    mutableStateOf("home")
                }

                Scaffold(

                    bottomBar = {

                        if (selectedScreen != "addTask") {

                            NavigationBar {

                                NavigationBarItem(
                                    selected = selectedScreen == "home",
                                    onClick = {
                                        selectedScreen = "home"
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.Home,
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
                                            imageVector = Icons.Default.CheckCircle,
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
                                            imageVector = Icons.Default.Flag,
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
                                            imageVector = Icons.Default.Insights,
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

                                GoalScreen()
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
                        }
                    }
                }
            }
        }
    }
}