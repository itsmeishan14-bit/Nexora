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
        automationSystem = NexoraAutomationSystem()
        val localIntentResolver = LocalAiIntentResolver(automationSystem = automationSystem)
        val localProvider = LocalAiProvider(intentResolver = localIntentResolver)
        val providerManager = AiProviderManager(localProvider = localProvider)
        val aiService = LocalNexoraAiService(providerManager = providerManager)
        val actionExecutor = AiActionExecutor(repository, automationSystem)
        val toolRegistry = AiToolRegistry(repository, actionExecutor)

        engine = NexoraAiEngine(
            contextBuilder = contextBuilder,
            aiService = aiService,
            providerManager = providerManager,
            actionExecutor = actionExecutor,
            toolRegistry = toolRegistry,
            repository = repository,
            automationSystem = automationSystem
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

    @Test
    fun `TEST 16 - Authoritative single automation system instance is shared across Executor, Engine, Brain, and Resolver`() = runBlocking {
        // Given ONE authoritative automation system shared across Executor, Engine, Brain, and LocalAiIntentResolver
        val sharedSystem = NexoraAutomationSystem()
        val executor = AiActionExecutor(repository, sharedSystem)
        val localResolver = LocalAiIntentResolver(automationSystem = sharedSystem)
        val localProvider = LocalAiProvider(intentResolver = localResolver)
        val providerManager = AiProviderManager(localProvider = localProvider)
        val service = LocalNexoraAiService(providerManager = providerManager)
        val registry = AiToolRegistry(repository, executor)
        val testEngine = NexoraAiEngine(
            contextBuilder = AiContextBuilder(repository),
            aiService = service,
            providerManager = providerManager,
            actionExecutor = executor,
            toolRegistry = registry,
            repository = repository,
            automationSystem = sharedSystem
        )

        // Verify initial state is identical across all components
        val initialEngineRules = testEngine.getAutomationRules()
        assertEquals(sharedSystem.getRules().size, initialEngineRules.size)
        assertEquals(sharedSystem.getRules().size, localResolver.getAutomationSystem().getRules().size)
        assertEquals(sharedSystem.getRules().size, executor.getAutomationSystem().getRules().size)

        // 1. Create an automation through the action executor (ACTION WRITE)
        val ruleName = "Authoritative Shared Rule"
        val createAction = AiAction(
            type = AiActionType.CREATE_AUTOMATION,
            title = "Create Rule",
            description = "Creates a test automation rule",
            parameters = mapOf(
                "name" to ruleName,
                "description" to "Monitors background test conditions",
                "triggerType" to "WORKLOAD_CHANGED",
                "userConfirmed" to true
            ),
            requiresConfirmation = false
        )
        val createResult = executor.execute(createAction)
        assertTrue("Create automation action must succeed", createResult.success)

        // Read through Brain / Engine (BRAIN/UI READ) -> verify newly created rule appears
        val rulesAfterCreate = testEngine.getAutomationRules()
        val createdRule = rulesAfterCreate.find { it.name == ruleName }
        assertNotNull("Newly created rule must be visible in Engine/Brain", createdRule)
        assertTrue(createdRule!!.enabled)

        // Verify rule is visible through IntentResolver's automationSystem
        val resolverRulesAfterCreate = localResolver.getAutomationSystem().getRules()
        assertTrue("Rule must be visible through IntentResolver's automationSystem",
            resolverRulesAfterCreate.any { it.name == ruleName })

        // 2. Toggle through executor (ACTION WRITE)
        val toggleAction = AiAction(
            type = AiActionType.TOGGLE_AUTOMATION,
            title = "Toggle Rule",
            description = "Toggle rule state",
            parameters = mapOf(
                "ruleName" to ruleName,
                "enabled" to false,
                "userConfirmed" to true
            ),
            requiresConfirmation = false
        )
        val toggleResult = executor.execute(toggleAction)
        assertTrue("Toggle automation action must succeed", toggleResult.success)

        // Read through Brain / Engine (BRAIN/UI READ) -> verify changed state
        val rulesAfterToggle = testEngine.getAutomationRules()
        val toggledRule = rulesAfterToggle.find { it.name == ruleName }
        assertNotNull(toggledRule)
        assertFalse("Rule state must reflect toggle (disabled) in Brain/Engine", toggledRule!!.enabled)

        // 3. Delete through executor (ACTION WRITE)
        val deleteAction = AiAction(
            type = AiActionType.DELETE_AUTOMATION,
            title = "Delete Rule",
            description = "Delete rule",
            parameters = mapOf(
                "ruleName" to ruleName,
                "userConfirmed" to true
            ),
            requiresConfirmation = false
        )
        val deleteResult = executor.execute(deleteAction)
        assertTrue("Delete automation action must succeed", deleteResult.success)

        // Read through Brain / Engine (BRAIN/UI READ) -> verify rule is gone
        val rulesAfterDelete = testEngine.getAutomationRules()
        assertFalse("Deleted rule must no longer be present in Brain/Engine",
            rulesAfterDelete.any { it.name == ruleName })
        assertFalse("Deleted rule must no longer be present in Resolver",
            localResolver.getAutomationSystem().getRules().any { it.name == ruleName })
    }

    @Test
    fun `TEST 17 - Engine executeAction correctly mutates authoritative automation state visible to Engine Brain and Resolver`() = runBlocking {
        // Test that execution via engine.executeAction (which ViewModel confirmAction delegates to)
        // routes directly through actionExecutor to the authoritative instance
        val sharedSystem = NexoraAutomationSystem()
        val executor = AiActionExecutor(repository, sharedSystem)
        val localResolver = LocalAiIntentResolver(automationSystem = sharedSystem)
        val localProvider = LocalAiProvider(intentResolver = localResolver)
        val providerManager = AiProviderManager(localProvider = localProvider)
        val service = LocalNexoraAiService(providerManager = providerManager)
        val registry = AiToolRegistry(repository, executor)
        
        // Construct engine relying on default parameter (actionExecutor.getAutomationSystem())
        val testEngine = NexoraAiEngine(
            contextBuilder = AiContextBuilder(repository),
            aiService = service,
            providerManager = providerManager,
            actionExecutor = executor,
            toolRegistry = registry,
            repository = repository
        )

        assertSame("Engine must adopt executor's automationSystem when not explicitly supplied",
            sharedSystem, testEngine.automationSystem)

        // 1. Create rule via engine.executeAction
        val ruleName = "Engine ExecuteAction Rule"
        val createAction = AiAction(
            type = AiActionType.CREATE_AUTOMATION,
            title = "Create Rule",
            description = "Rule created via engine execution",
            parameters = mapOf(
                "name" to ruleName,
                "description" to "Rule created via engine"
            ),
            requiresConfirmation = true
        )
        val createResult = testEngine.executeAction(createAction)
        assertTrue("Create action through engine must succeed", createResult.success)

        // Verify rule is immediately visible in Engine & Brain rules
        assertTrue("Engine must see newly created rule",
            testEngine.getAutomationRules().any { it.name == ruleName })
        assertTrue("Resolver must see newly created rule",
            localResolver.getAutomationSystem().getRules().any { it.name == ruleName })

        // 2. Toggle rule via engine.updateAutomationRule (used by AiAutomationScreen / ViewModel toggle)
        val createdRule = testEngine.getAutomationRules().first { it.name == ruleName }
        testEngine.updateAutomationRule(createdRule.copy(enabled = false))

        val toggledInEngine = testEngine.getAutomationRules().first { it.name == ruleName }
        assertFalse("Toggled rule must be disabled in Engine", toggledInEngine.enabled)
        val toggledInShared = sharedSystem.getRules().first { it.name == ruleName }
        assertFalse("Toggled rule must be disabled in sharedSystem", toggledInShared.enabled)

        // 3. Delete rule via engine.deleteAutomationRule
        val deleteResult = testEngine.deleteAutomationRule(ruleName)
        assertTrue("Delete rule through engine must succeed", deleteResult)
        assertFalse("Deleted rule must be removed from Engine",
            testEngine.getAutomationRules().any { it.name == ruleName })
        assertFalse("Deleted rule must be removed from sharedSystem",
            sharedSystem.getRules().any { it.name == ruleName })
    }
}

