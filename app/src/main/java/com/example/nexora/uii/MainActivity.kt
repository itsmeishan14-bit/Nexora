package com.example.nexora.uii

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.nexora.uii.GoalScreen
import com.example.nexora.uii.HomeScreen
import com.example.nexora.uii.InsightScreen
import com.example.nexora.uii.TasksScreen
import com.example.nexora.ui.theme.NexoraTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NexoraTheme {
                NexoraApp()
            }
        }
    }
}

@Composable
fun NexoraApp() {

    var selectedScreen by remember {
        mutableIntStateOf(0)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),

        bottomBar = {
            NavigationBar {

                NavigationBarItem(
                    selected = selectedScreen == 0,
                    onClick = { selectedScreen = 0 },
                    icon = {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = "Home"
                        )
                    },
                    label = { Text("Home") }
                )

                NavigationBarItem(
                    selected = selectedScreen == 1,
                    onClick = { selectedScreen = 1 },
                    icon = {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Tasks"
                        )
                    },
                    label = { Text("Tasks") }
                )

                NavigationBarItem(
                    selected = selectedScreen == 2,
                    onClick = { selectedScreen = 2 },
                    icon = {
                        Icon(
                            Icons.Default.Flag,
                            contentDescription = "Goals"
                        )
                    },
                    label = { Text("Goals") }
                )

                NavigationBarItem(
                    selected = selectedScreen == 3,
                    onClick = { selectedScreen = 3 },
                    icon = {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "Insights"
                        )
                    },
                    label = { Text("Insights") }
                )
            }
        }
    ) {

        when (selectedScreen) {

            0 -> HomeScreen(
                onAddTask = {
                    selectedScreen = 1
                }
            )

            1 -> TasksScreen()

            2 -> GoalScreen()

            3 -> InsightScreen()
        }
    }
}