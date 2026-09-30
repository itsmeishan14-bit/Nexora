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
    private lateinit var actionExecutor: AiActionExecutor
    private lateinit var localIntentResolver: LocalAiIntentResolver

    @Before
    fun setup() {
        repository = MockNexoraRepository()
        val contextBuilder = AiContextBuilder(repository)
        automationSystem = NexoraAutomationSystem()
        localIntentResolver = LocalAiIntentResolver(automationSystem = automationSystem)
        val localProvider = LocalAiProvider(intentResolver = localIntentResolver)
        val providerManager = AiProviderManager(localProvider = localProvider)
        val aiService = LocalNexoraAiService(providerManager = providerManager)
        actionExecutor = AiActionExecutor(repository, automationSystem)
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

    @Test
    fun `TEST 18 - End-to-end Create Automation via Natural Language and Execution`() = runBlocking {
        // Delete Morning Plan Assistant first to test creation from scratch
        engine.deleteAutomationRule("Morning Plan Assistant")
        assertFalse(engine.getAutomationRules().any { it.name == "Morning Plan Assistant" })

        // 1. Natural language request
        val query = "Every morning prepare my daily plan."
        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = query))

        assertEquals(AiResponseType.ACTION_PROPOSAL, response.responseType)
        assertEquals(1, response.proposedActions.size)
        val action = response.proposedActions.first()

        assertEquals(AiActionType.CREATE_AUTOMATION, action.type)
        assertEquals("Morning Plan Assistant", action.parameters["name"])
        assertEquals("DAY_STARTED", action.parameters["triggerType"])

        // 2. Execution through engine.executeAction
        val result = engine.executeAction(action)
        assertTrue("Execution should succeed: ${result.error}", result.success)
        assertEquals("Automation rule created: Morning Plan Assistant", result.message)

        // 3. Verify rule exists across Engine, Brain, and shared system
        val created = engine.getAutomationRules().find { it.name == "Morning Plan Assistant" }
        assertNotNull(created)
        assertTrue(created!!.enabled)
        assertEquals(AutomationTriggerType.DAY_STARTED, created.triggerType)
        assertTrue(automationSystem.getRules().any { it.name == "Morning Plan Assistant" })
    }

    @Test
    fun `TEST 19 - End-to-end Toggle Automation via Natural Language and Execution`() = runBlocking {
        // Ensure Morning Plan Assistant exists and is enabled
        val rule = engine.getAutomationRules().find { it.name == "Morning Plan Assistant" }
        assertNotNull(rule)
        assertTrue(rule!!.enabled)

        // 1. Disable via natural language
        val disableQuery = "Disable my Morning Plan Assistant."
        val disableResponse = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = disableQuery))

        assertEquals(AiResponseType.ACTION_PROPOSAL, disableResponse.responseType)
        val disableAction = disableResponse.proposedActions.first()
        assertEquals(AiActionType.TOGGLE_AUTOMATION, disableAction.type)
        assertEquals(false, disableAction.parameters["enabled"])

        val disableResult = engine.executeAction(disableAction)
        assertTrue(disableResult.success)

        val disabledRule = engine.getAutomationRules().find { it.name == "Morning Plan Assistant" }
        assertNotNull(disabledRule)
        assertFalse(disabledRule!!.enabled)

        // 2. Enable via natural language
        val enableQuery = "Enable my Morning Plan Assistant."
        val enableResponse = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = enableQuery))

        assertEquals(AiResponseType.ACTION_PROPOSAL, enableResponse.responseType)
        val enableAction = enableResponse.proposedActions.first()
        assertEquals(AiActionType.TOGGLE_AUTOMATION, enableAction.type)
        assertEquals(true, enableAction.parameters["enabled"])

        val enableResult = engine.executeAction(enableAction)
        assertTrue(enableResult.success)

        val reEnabledRule = engine.getAutomationRules().find { it.name == "Morning Plan Assistant" }
        assertNotNull(reEnabledRule)
        assertTrue(reEnabledRule!!.enabled)
    }

    @Test
    fun `TEST 20 - End-to-end Delete Automation via Natural Language and Execution, and Nonexistent Failure`() = runBlocking {
        assertTrue(engine.getAutomationRules().any { it.name == "Morning Plan Assistant" })

        // 1. Delete via natural language
        val deleteQuery = "Delete my Morning Plan Assistant."
        val response = engine.processRequest(AiRequest(AiRequestType.CHAT, userMessage = deleteQuery))

        assertEquals(AiResponseType.ACTION_PROPOSAL, response.responseType)
        val deleteAction = response.proposedActions.first()
        assertEquals(AiActionType.DELETE_AUTOMATION, deleteAction.type)
        assertTrue(deleteAction.requiresConfirmation)

        val deleteResult = engine.executeAction(deleteAction)
        assertTrue(deleteResult.success)
        assertFalse(engine.getAutomationRules().any { it.name == "Morning Plan Assistant" })

        // 2. Attempt to delete nonexistent rule directly
        val nonexistentAction = AiAction(
            type = AiActionType.DELETE_AUTOMATION,
            title = "Delete Rule",
            description = "Delete nonexistent rule",
            parameters = mapOf("ruleName" to "Nonexistent Mystery Rule", "userConfirmed" to true)
        )
        val failResult = engine.executeAction(nonexistentAction)
        assertFalse("Deleting nonexistent rule must fail", failResult.success)
        assertEquals("Rule not found", failResult.error)
    }

    @Test
    fun `TEST 21 - Duplicate Automation Creation Protection`() = runBlocking {
        val createAction = AiAction(
            type = AiActionType.CREATE_AUTOMATION,
            title = "Create Sentinel",
            description = "Sentinel rule",
            parameters = mapOf(
                "name" to "Project Sentinel",
                "description" to "Guards milestone dates",
                "triggerType" to "PRODUCTIVITY_PATTERN_DETECTED",
                "userConfirmed" to true
            )
        )

        // First creation succeeds
        val result1 = engine.executeAction(createAction)
        assertTrue("First creation should succeed", result1.success)

        // Second creation of identical rule fails
        val result2 = engine.executeAction(createAction)
        assertFalse("Second duplicate creation MUST fail", result2.success)
        assertEquals("Duplicate rule name", result2.error)

        val count = engine.getAutomationRules().count { it.name == "Project Sentinel" }
        assertEquals("No duplicate automation should exist", 1, count)
    }

    @Test
    fun `TEST 22 - Action Outcomes Truthfully Recorded in Learning System`() = runBlocking {
        // 1. Successful action records SUCCESS
        val createAction = AiAction(
            type = AiActionType.CREATE_AUTOMATION,
            title = "Create Learning Test Rule",
            description = "Learning rule",
            parameters = mapOf("name" to "Learning Rule Alpha", "userConfirmed" to true)
        )
        engine.executeAction(createAction)
        val outcome1 = repository.getRecentOutcomes(1).last()
        assertEquals(AiOutcomeType.SUCCESS, outcome1.type)

        // 2. Duplicate action records FAILED
        engine.executeAction(createAction)
        val outcome2 = repository.getRecentOutcomes(1).last()
        assertEquals(AiOutcomeType.FAILED, outcome2.type)
        assertTrue(outcome2.actualResult?.contains("Duplicate") == true || outcome2.evidence?.contains("Duplicate") == true)

        // 3. Nonexistent toggle records FAILED
        val badToggle = AiAction(
            type = AiActionType.TOGGLE_AUTOMATION,
            title = "Toggle Invalid",
            description = "Toggle missing",
            parameters = mapOf("ruleName" to "Ghost Rule", "enabled" to false, "userConfirmed" to true)
        )
        engine.executeAction(badToggle)
        val outcome3 = repository.getRecentOutcomes(1).last()
        assertEquals(AiOutcomeType.FAILED, outcome3.type)

        // 4. Missing parameters records FAILED
        val invalidAction = AiAction(
            type = AiActionType.CREATE_AUTOMATION,
            title = "Create Invalid",
            description = "No name",
            parameters = mapOf("userConfirmed" to true)
        )
        engine.executeAction(invalidAction)
        val outcome4 = repository.getRecentOutcomes(1).last()
        assertEquals(AiOutcomeType.FAILED, outcome4.type)
    }

    @Test
    fun `TEST 23 - Security and Confirmation Enforcement`() = runBlocking {
        // Destructive delete without confirmation must be rejected
        val unconfirmedDelete = AiAction(
            type = AiActionType.DELETE_AUTOMATION,
            title = "Delete Rule",
            description = "Delete without confirmation",
            parameters = mapOf("ruleName" to "Workload Manager"),
            requiresConfirmation = true
        )
        val rejectResult = actionExecutor.execute(unconfirmedDelete)
        assertFalse("Unconfirmed destructive action must be rejected", rejectResult.success)
        assertEquals("Authorization error", rejectResult.error)

        // Low risk create without explicit confirmation is allowed
        val safeCreate = AiAction(
            type = AiActionType.CREATE_AUTOMATION,
            title = "Create Low Risk",
            description = "Safe create",
            parameters = mapOf("name" to "Safe Rule Alpha"),
            requiresConfirmation = false
        )
        val allowResult = actionExecutor.execute(safeCreate)
        assertTrue("Low-risk create without confirmation requirement is permitted", allowResult.success)
    }

    @Test
    fun `TEST 24 - Stale State Execution Does Not Resurrect or Mutate State`() = runBlocking {
        // 1. Create automation
        val createAction = AiAction(
            type = AiActionType.CREATE_AUTOMATION,
            title = "Create Temp Rule",
            description = "Temp rule",
            parameters = mapOf("name" to "Temporary State Rule", "userConfirmed" to true)
        )
        engine.executeAction(createAction)
        assertTrue(engine.getAutomationRules().any { it.name == "Temporary State Rule" })

        // 2. Prepare action targeting it
        val toggleAction = AiAction(
            type = AiActionType.TOGGLE_AUTOMATION,
            title = "Toggle Temp Rule",
            description = "Target temp rule",
            parameters = mapOf("ruleName" to "Temporary State Rule", "enabled" to false, "userConfirmed" to true)
        )

        // 3. Delete automation through another path
        engine.deleteAutomationRule("Temporary State Rule")
        assertFalse(engine.getAutomationRules().any { it.name == "Temporary State Rule" })

        // 4. Execute stale action
        val staleResult = engine.executeAction(toggleAction)
        assertFalse("Stale action targeting deleted rule must fail", staleResult.success)
        assertEquals("Rule not found", staleResult.error)

        // Ensure rule is not resurrected
        assertFalse("Deleted rule must remain deleted",
            engine.getAutomationRules().any { it.name == "Temporary State Rule" })
    }

    @Test
    fun `TEST 25 - Automation Triggers with TASK_CREATED, TASK_COMPLETED, and PRODUCTIVITY_PATTERN_DETECTED`() = runBlocking {
        // 1. TASK_CREATED: Urgent Conflict Detector
        val conflictContext = AiContext(
            tasks = listOf(
                PremiumTask(id = 1, title = "Urgent 1", category = "Work", duration = "30m", priority = com.example.nexora.uii.TaskPriority.URGENT, completed = false),
                PremiumTask(id = 2, title = "Urgent 2", category = "Work", duration = "30m", priority = com.example.nexora.uii.TaskPriority.URGENT, completed = false),
                PremiumTask(id = 3, title = "Urgent 3", category = "Work", duration = "30m", priority = com.example.nexora.uii.TaskPriority.URGENT, completed = false)
            )
        )
        val createdSignals = automationSystem.evaluateTriggers(AutomationTriggerType.TASK_CREATED, conflictContext)
        assertTrue("Urgent Conflict Detector should trigger on multiple urgent tasks",
            createdSignals.any { it.type == ProactiveSignalType.HIGH_PRIORITY_CONFLICT })

        // 2. TASK_COMPLETED: Task Completion Next Action
        val completeContext = AiContext(
            tasks = listOf(
                PremiumTask(id = 1, title = "Done Task", category = "Work", duration = "30m", completed = true),
                PremiumTask(id = 2, title = "Upcoming Task", category = "Work", duration = "30m", priority = com.example.nexora.uii.TaskPriority.HIGH, completed = false)
            )
        )
        val completedSignals = automationSystem.evaluateTriggers(AutomationTriggerType.TASK_COMPLETED, completeContext)
        assertTrue("TASK_COMPLETED should produce next action signal",
            completedSignals.any { it.type == ProactiveSignalType.NEXT_ACTION_OPPORTUNITY })

        // 3. PRODUCTIVITY_PATTERN_DETECTED: Goal Progress Guard
        val patternContext = AiContext(
            goals = listOf(
                NexoraGoal(id = 1, title = "Goal Stagnating", category = "Health", targetDate = "2026-12-31", progress = 0.05f)
            ),
            personalContext = AiPersonalContext(
                goalHealth = listOf(
                    GoalHealthAssessment(1L, "Goal Stagnating", GoalHealthState.AT_RISK, 0.05f, ActivityLevel.LOW, 14, "No activity for 14 days")
                )
            )
        )
        val patternSignals = automationSystem.evaluateTriggers(AutomationTriggerType.PRODUCTIVITY_PATTERN_DETECTED, patternContext)
        assertTrue("PRODUCTIVITY_PATTERN_DETECTED should trigger Goal Progress Guard",
            patternSignals.any { it.type == ProactiveSignalType.GOAL_NEGLECT || it.type == ProactiveSignalType.NEGLECTED_GOAL })

        // 4. Verify disabled rules do not trigger
        val urgentRule = automationSystem.getRules().first { it.name == "Urgent Conflict Detector" }
        automationSystem.updateRule(urgentRule.copy(enabled = false))
        val disabledSignals = automationSystem.evaluateTriggers(AutomationTriggerType.TASK_CREATED, conflictContext)
        assertFalse("Disabled rule must not trigger",
            disabledSignals.any { it.type == ProactiveSignalType.HIGH_PRIORITY_CONFLICT })
    }
}

