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
    val progressHistory: List<DailyProgress> = emptyList(),
    val isProcessing: Boolean = false
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
            launch {
                repository.observeTasks().collect { savedTasks ->
                    _uiState.value = _uiState.value.copy(tasks = savedTasks)
                }
            }
            launch {
                repository.observeGoals().collect { savedGoals ->
                    _uiState.value = _uiState.value.copy(goals = savedGoals)
                }
            }
            launch {
                repository.observeDailyProgress().collect { savedProgress ->
                    _uiState.value = _uiState.value.copy(
                        progressHistory = savedProgress.map { item ->
                            DailyProgress(
                                date = LocalDate.parse(item.date),
                                tasksPlanned = item.tasksPlanned,
                                tasksCompleted = item.tasksCompleted,
                                focusMinutes = item.focusMinutes,
                                goalsWorkedOn = item.goalsWorkedOn,
                                carriedTasks = item.carriedTasks
                            )
                        }.sortedByDescending { it.date }
                    )
                }
            }
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
        if (_uiState.value.isProcessing) return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            try {
                val updatedTask = task.copy(completed = !task.completed)
                repository.updateTask(updatedTask)
                
                // Single update flow
                val currentTasks = repository.observeTasks().first()
                val currentGoals = repository.observeGoals().first()
                
                // Recalculate goals that might have changed
                currentGoals.forEach { goal ->
                    val progress = calculateGoalProgress(goal = goal, tasks = currentTasks)
                    if (progress != goal.progress) {
                        repository.updateGoal(goal.copy(progress = progress))
                    }
                }
                
                updateTodayProgressInternal(currentTasks)
                refreshData()
            } finally {
                _uiState.value = _uiState.value.copy(isProcessing = false)
            }
        }
    }

    fun deleteTask(task: PremiumTask) {
        if (_uiState.value.isProcessing) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            try {
                repository.deleteTask(task)
                
                val currentTasks = repository.observeTasks().first()
                val currentGoals = repository.observeGoals().first()
                
                currentGoals.forEach { goal ->
                    val progress = calculateGoalProgress(goal = goal, tasks = currentTasks)
                    if (progress != goal.progress) {
                        repository.updateGoal(goal.copy(progress = progress))
                    }
                }
                
                updateTodayProgressInternal(currentTasks)
                refreshData()
            } finally {
                _uiState.value = _uiState.value.copy(isProcessing = false)
            }
        }
    }

    fun addGoal(name: String, category: String, targetDate: String) {
        if (_uiState.value.isProcessing) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            try {
                repository.addGoal(NexoraGoal(title = name, category = category, targetDate = targetDate, progress = 0f))
                updateTodayProgress()
            } finally {
                _uiState.value = _uiState.value.copy(isProcessing = false)
            }
        }
    }

    fun updateGoal(goal: NexoraGoal, name: String, category: String, targetDate: String) {
        if (_uiState.value.isProcessing) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            try {
                repository.updateGoal(goal.copy(title = name, category = category, targetDate = targetDate))
                updateTodayProgress()
            } finally {
                _uiState.value = _uiState.value.copy(isProcessing = false)
            }
        }
    }

    fun deleteGoal(goal: NexoraGoal) {
        if (_uiState.value.isProcessing) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            try {
                repository.deleteGoal(goal)
                // Unlink tasks
                val currentTasks = repository.observeTasks().first()
                currentTasks.filter { it.goalTitle == goal.title }.forEach { 
                    repository.updateTask(it.copy(goalTitle = null)) 
                }
                updateTodayProgress()
            } finally {
                _uiState.value = _uiState.value.copy(isProcessing = false)
            }
        }
    }

    fun addTask(name: String, cat: String, dur: String, gt: String?, p: TaskPriority) {
        if (_uiState.value.isProcessing) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            try {
                val newTask = PremiumTask(title = name, category = cat, duration = dur, goalTitle = gt, priority = p)
                repository.addTask(newTask)
                
                val currentTasks = repository.observeTasks().first()
                if (gt != null) {
                    repository.observeGoals().first().find { it.title == gt }?.let { goal ->
                        val progress = calculateGoalProgress(goal = goal, tasks = currentTasks)
                        repository.updateGoal(goal.copy(progress = progress))
                    }
                }
                
                updateTodayProgressInternal(currentTasks)
                refreshData()
            } finally {
                _uiState.value = _uiState.value.copy(isProcessing = false)
            }
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
            }.sortedByDescending { it.date }
        )
    }

    private suspend fun updateTodayProgress() {
        updateTodayProgressInternal(repository.observeTasks().first())
        refreshData()
    }

    private suspend fun updateTodayProgressInternal(currentTasks: List<PremiumTask>) {
        val today = LocalDate.now()
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
    }
}
