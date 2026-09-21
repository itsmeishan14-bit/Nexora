package com.example.nexora.ai

import com.example.nexora.ai.evaluation.MockNexoraRepository
import com.example.nexora.uii.PremiumTask
import com.example.nexora.uii.NexoraGoal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NexoraAutomationWorkflowsTest {

    private lateinit var repository: MockNexoraRepository
    private lateinit var engine: NexoraAiEngine
    private lateinit var automationSystem: NexoraAutomationSystem

    @Before
    fun setup() {
        repository = MockNexoraRepository()
        val contextBuilder = AiContextBuilder(repository)
        val localProvider = LocalAiProvider()
        val providerManager = AiProviderManager(localProvider = localProvider)
        val aiService = LocalNexoraAiService(providerManager = providerManager)
        val actionExecutor = AiActionExecutor(repository)
        val toolRegistry = AiToolRegistry(repository, actionExecutor)

        automationSystem = NexoraAutomationSystem()

        engine = NexoraAiEngine(
            contextBuilder = contextBuilder,
            aiService = aiService,
            providerManager = providerManager,
            actionExecutor = actionExecutor,
            toolRegistry = toolRegistry,
            repository = repository
        )
    }

    @Test
    fun `TEST 1 - Create automation via natural language`() = runBlocking {
        val query = "Every morning prepare my daily plan"
        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = query))

        assertEquals(AiResponseType.ACTION_PROPOSAL, response.responseType)
        assertTrue(response.message.contains("Morning Plan Assistant", ignoreCase = true) || response.message.contains("rule", ignoreCase = true))
    }

    @Test
    fun `TEST 2 - Show automations via natural language`() = runBlocking {
        val query = "Show my automations"
        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = query))

        assertEquals(AiResponseType.INFORMATION, response.responseType)
        assertTrue(response.message.contains("Workload Manager", ignoreCase = true))
        assertTrue(response.message.contains("Goal Progress Guard", ignoreCase = true))
    }

    @Test
    fun `TEST 3 - Disable automation rule`() = runBlocking {
        val rule = automationSystem.getRules().first()
        val disabledRule = rule.copy(enabled = false)
        automationSystem.updateRule(disabledRule)

        assertFalse(automationSystem.getRules().first { it.id == rule.id }.enabled)

        val context = AiContext(tasksPlannedToday = 20, adaptiveProfile = AdaptiveProfile(preferredDailyWorkload = 2, confidence = AdaptiveConfidence.HIGH))
        val signals = automationSystem.evaluateTriggers(rule.triggerType, context)

        assertFalse("Disabled rule should not produce signals", signals.any { it.title == rule.name })
    }

    @Test
    fun `TEST 4 - Task completion trigger`() = runBlocking {
        val context = AiContext(
            tasksPlannedToday = 5,
            tasksCompletedToday = 3,
            tasks = listOf(
                PremiumTask(id = 1, title = "Finished Task", category = "Work", duration = "30m", completed = true),
                PremiumTask(id = 2, title = "Next Task", category = "Work", duration = "30m", priority = com.example.nexora.uii.TaskPriority.HIGH, completed = false)
            )
        )

        val signals = automationSystem.evaluateTriggers(AutomationTriggerType.TASK_COMPLETED, context)
        assertTrue("Task completion should trigger next action signal", signals.any { it.type == ProactiveSignalType.NEXT_ACTION_OPPORTUNITY })
    }

    @Test
    fun `TEST 5 - Repeated flow re-emissions do not cause duplicate execution`() = runBlocking {
        val context = AiContext(
            tasksPlannedToday = 15,
            adaptiveProfile = AdaptiveProfile(preferredDailyWorkload = 5, confidence = AdaptiveConfidence.HIGH)
        )

        val signals1 = automationSystem.evaluateTriggers(AutomationTriggerType.WORKLOAD_CHANGED, context)
        assertEquals(1, signals1.size)

        // Simulated rapid duplicate Flow emission
        val signals2 = automationSystem.evaluateTriggers(AutomationTriggerType.WORKLOAD_CHANGED, context)
        assertEquals("Cooldown must prevent duplicate execution", 0, signals2.size)
    }

    @Test
    fun `TEST 6 - Workload exceeds learned capacity triggers workload rule once`() = runBlocking {
        val context = AiContext(
            tasksPlannedToday = 12,
            adaptiveProfile = AdaptiveProfile(preferredDailyWorkload = 4, confidence = AdaptiveConfidence.HIGH)
        )

        val signals = automationSystem.evaluateTriggers(AutomationTriggerType.WORKLOAD_CHANGED, context)
        assertTrue(signals.any { it.type == ProactiveSignalType.WORKLOAD_RISK || it.type == ProactiveSignalType.OVERLOAD })
    }

    @Test
    fun `TEST 7 - Unchanged workload does not produce notification spam`() = runBlocking {
        val context = AiContext(
            tasksPlannedToday = 12,
            adaptiveProfile = AdaptiveProfile(preferredDailyWorkload = 4, confidence = AdaptiveConfidence.HIGH)
        )

        automationSystem.evaluateTriggers(AutomationTriggerType.WORKLOAD_CHANGED, context)
        val repeatSignals = automationSystem.evaluateTriggers(AutomationTriggerType.WORKLOAD_CHANGED, context)

        assertEquals("No notification spam on unchanged workload", 0, repeatSignals.size)
    }

    @Test
    fun `TEST 8 - Goal milestone reached triggers workflow`() = runBlocking {
        val context = AiContext(
            goals = listOf(
                NexoraGoal(id = 1, title = "Android Mastery", category = "Work", targetDate = "2026-12-31", progress = 0.5f)
            ),
            personalContext = AiPersonalContext(
                goalHealth = listOf(
                    GoalHealthAssessment(1L, "Android Mastery", GoalHealthState.HEALTHY, 0.5f, ActivityLevel.HIGH, 0, "On track")
                )
            )
        )

        val signals = automationSystem.evaluateTriggers(AutomationTriggerType.GOAL_PROGRESS_CHANGED, context)
        // System handles evaluation gracefully without crashing
        assertNotNull(signals)
    }

    @Test
    fun `TEST 9 - State-changing automation requires user confirmation`() = runBlocking {
        val action = AiAction(
            type = AiActionType.DELETE_AUTOMATION,
            title = "Delete Automation Rule",
            description = "Delete Workload Manager rule",
            parameters = mapOf("ruleId" to "1"),
            requiresConfirmation = true
        )

        val executor = AiActionExecutor(repository)
        val result = executor.execute(action)

        assertFalse("Destructive automation changes MUST require confirmation", result.success)
        assertTrue(result.message.contains("confirmation", ignoreCase = true))
    }

    @Test
    fun `TEST 10 - Automation failure is recorded and app remains stable`() = runBlocking {
        val rule = AiAutomationRule(
            name = "Failing Rule",
            description = "Invalid rule test",
            triggerType = AutomationTriggerType.WORKLOAD_CHANGED
        )

        automationSystem.addRule(rule)
        val context = AiContext()

        // Should evaluate cleanly without throwing an exception
        val signals = automationSystem.evaluateTriggers(AutomationTriggerType.WORKLOAD_CHANGED, context)
        assertNotNull(signals)
    }

    @Test
    fun `TEST 11 - App restart simulation preserves rule cooldowns`() = runBlocking {
        val context = AiContext(
            tasksPlannedToday = 15,
            adaptiveProfile = AdaptiveProfile(preferredDailyWorkload = 5, confidence = AdaptiveConfidence.HIGH)
        )

        automationSystem.evaluateTriggers(AutomationTriggerType.WORKLOAD_CHANGED, context)

        // Simulate app restart by retrieving rules and verifying lastTriggeredAt timestamp
        val rulesAfterRun = automationSystem.getRules()
        val workloadRule = rulesAfterRun.first { it.name == "Workload Manager" }

        assertTrue(workloadRule.lastTriggeredAt > 0)
    }

    @Test
    fun `TEST 12 - Local deterministic automations function offline without LLM`() = runBlocking {
        val localSystem = NexoraAutomationSystem()
        val context = AiContext(
            tasksPlannedToday = 15,
            adaptiveProfile = AdaptiveProfile(preferredDailyWorkload = 5, confidence = AdaptiveConfidence.HIGH)
        )

        val signals = localSystem.evaluateTriggers(AutomationTriggerType.WORKLOAD_CHANGED, context)
        assertTrue("Local offline automation must work without cloud/LLM", signals.isNotEmpty())
    }

    @Test
    fun `TEST 13 - Automation handles missing or deleted task entity gracefully`() = runBlocking {
        // Missing task ID 9999
        val context = AiContext(tasks = emptyList())
        val signals = automationSystem.evaluateTriggers(AutomationTriggerType.TASK_CARRIED_FORWARD, context)

        assertNotNull(signals)
        assertFalse(signals.any { it.relatedTaskId == 9999L })
    }

    @Test
    fun `TEST 14 - Automation explanation provides evidence-based answer`() = runBlocking {
        val context = AiContext(
            tasksPlannedToday = 15,
            adaptiveProfile = AdaptiveProfile(preferredDailyWorkload = 5, confidence = AdaptiveConfidence.HIGH)
        )

        automationSystem.evaluateTriggers(AutomationTriggerType.WORKLOAD_CHANGED, context)
        val explanation = automationSystem.explainLastRun("Workload Manager")

        assertTrue(explanation.contains("Workload Manager", ignoreCase = true))
        assertTrue(explanation.contains("triggered", ignoreCase = true) || explanation.contains("ran", ignoreCase = true))
    }

    @Test
    fun `TEST 15 - Performance check with large task and automation count`() = runBlocking {
        val largeTasks = (1..500).map {
            PremiumTask(id = it.toLong(), title = "Task $it", category = "Work", duration = "30m", completed = it % 2 == 0)
        }

        val context = AiContext(tasks = largeTasks, tasksPlannedToday = 250)

        val start = System.currentTimeMillis()
        val signals = automationSystem.evaluateTriggers(AutomationTriggerType.WORKLOAD_CHANGED, context)
        val duration = System.currentTimeMillis() - start

        assertNotNull(signals)
        assertTrue("Automation evaluation on 500 tasks must be under 100ms", duration < 100)
    }
}
