package com.example.nexora.uii

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexora.data.DailyProgressEntity
import com.example.nexora.data.NexoraRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

data class NexoraMainUiState(
    val selectedScreen: String = "home",
    val selectedGoal: NexoraGoal? = null,
    val editingGoal: NexoraGoal? = null,
    val taskGoal: NexoraGoal? = null,
    val tasks: List<PremiumTask> = emptyList(),
    val goals: List<NexoraGoal> = emptyList(),
    val progressHistory: List<DailyProgress> = emptyList()
)

class NexoraMainViewModel(
    private val repository: NexoraRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NexoraMainUiState())
    val uiState: StateFlow<NexoraMainUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val savedTasks = repository.observeTasks().first()
            val savedGoals = repository.observeGoals().first()
            val savedProgress = repository.observeDailyProgress().first()

            _uiState.value = _uiState.value.copy(
                tasks = savedTasks,
                goals = savedGoals,
                progressHistory = savedProgress.map { item ->
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
        }
    }

    fun navigateTo(screen: String) {
        _uiState.value = _uiState.value.copy(selectedScreen = screen)
    }

    fun setSelectedGoal(goal: NexoraGoal?) {
        _uiState.value = _uiState.value.copy(selectedGoal = goal)
    }

    fun setEditingGoal(goal: NexoraGoal?) {
        _uiState.value = _uiState.value.copy(editingGoal = goal)
    }

    fun setTaskGoal(goal: NexoraGoal?) {
        _uiState.value = _uiState.value.copy(taskGoal = goal)
    }

    fun toggleTask(task: PremiumTask) {
        viewModelScope.launch {
            val updatedTask = task.copy(completed = !task.completed)
            repository.updateTask(updatedTask)
            refreshData()
            refreshGoalProgress()
            updateTodayProgress()
        }
    }

    fun deleteTask(task: PremiumTask) {
        viewModelScope.launch {
            repository.deleteTask(task)
            refreshData()
            refreshGoalProgress()
            updateTodayProgress()
        }
    }

    fun addGoal(name: String, category: String, targetDate: String) {
        viewModelScope.launch {
            repository.addGoal(NexoraGoal(title = name, category = category, targetDate = targetDate, progress = 0f))
            refreshData()
            updateTodayProgress()
        }
    }

    fun updateGoal(goal: NexoraGoal, name: String, category: String, targetDate: String) {
        viewModelScope.launch {
            repository.updateGoal(goal.copy(title = name, category = category, targetDate = targetDate))
            refreshData()
            updateTodayProgress()
        }
    }

    fun deleteGoal(goal: NexoraGoal) {
        viewModelScope.launch {
            repository.deleteGoal(goal)
            // Unlink tasks
            val tasks = _uiState.value.tasks.filter { it.goalTitle == goal.title }
            tasks.forEach { repository.updateTask(it.copy(goalTitle = null)) }
            refreshData()
            updateTodayProgress()
        }
    }

    fun addTask(name: String, cat: String, dur: String, gt: String?, p: TaskPriority) {
        viewModelScope.launch {
            val newTask = PremiumTask(title = name, category = cat, duration = dur, goalTitle = gt, priority = p)
            repository.addTask(newTask)
            refreshAll()
            refreshGoalProgress()
            updateTodayProgress()
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            refreshData()
        }
    }

    private suspend fun refreshData() {
        val savedTasks = repository.observeTasks().first()
        val savedGoals = repository.observeGoals().first()
        val savedProgress = repository.observeDailyProgress().first()

        _uiState.value = _uiState.value.copy(
            tasks = savedTasks,
            goals = savedGoals,
            progressHistory = savedProgress.map { item ->
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
    }

    private suspend fun refreshGoalProgress() {
        val currentTasks = _uiState.value.tasks
        val currentGoals = _uiState.value.goals
        currentGoals.forEach { goal ->
            val progress = calculateGoalProgress(goal = goal, tasks = currentTasks)
            if (progress != goal.progress) {
                repository.updateGoal(goal.copy(progress = progress))
            }
        }
        val updatedGoals = repository.observeGoals().first()
        _uiState.value = _uiState.value.copy(goals = updatedGoals)
    }

    private suspend fun updateTodayProgress() {
        val today = LocalDate.now()
        val currentTasks = _uiState.value.tasks
        val completedCount = currentTasks.count { it.completed }
        val totalCount = currentTasks.size
        val goalsWorkedOn = currentTasks.mapNotNull { it.goalTitle }.distinct().size

        repository.saveDailyProgress(
            DailyProgressEntity(
                date = today.toString(),
                tasksPlanned = totalCount,
                tasksCompleted = completedCount,
                focusMinutes = 0,
                goalsWorkedOn = goalsWorkedOn,
                carriedTasks = 0
            )
        )
        refreshData()
    }
}
