package com.example.nexora.ai

import com.example.nexora.ai.evaluation.MockNexoraRepository
import com.example.nexora.data.NexoraRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NexoraAutomationPersistenceTest {

    private lateinit var repository: MockNexoraRepository

    @Before
    fun setup() {
        repository = MockNexoraRepository()
    }

    @Test
    fun `TEST 1 - Fresh database initializes default automations exactly once and restart does not duplicate`() = runBlocking {
        // Fresh initialization
        val systemA = NexoraAutomationSystem(repository)
        systemA.awaitInitialization()
        val initialRules = systemA.getRules()
        assertEquals("Default rules should be initialized once", 6, initialRules.size)
        assertTrue(initialRules.any { it.name == "Morning Plan Assistant" })
        assertTrue(initialRules.any { it.name == "Workload Manager" })

        // Simulate app restart: destroy systemA, create systemB using the same repository
        val systemB = NexoraAutomationSystem(repository)
        systemB.awaitInitialization()
        val restartedRules = systemB.getRules()
        assertEquals("Restart must not duplicate default rules", 6, restartedRules.size)
        assertEquals(
            initialRules.map { it.name }.sorted(),
            restartedRules.map { it.name }.sorted()
        )
    }

    @Test
    fun `TEST 2 - User-created automation survives app restart`() = runBlocking {
        val systemA = NexoraAutomationSystem(repository)
        systemA.awaitInitialization()
        val customRule = AiAutomationRule(
            name = "Study Every Morning",
            description = "Prepares study materials at 8 AM",
            triggerType = AutomationTriggerType.DAY_STARTED,
            conditionExpression = "morning",
            cooldownMillis = 1800000
        )
        val added = systemA.addRule(customRule)
        assertTrue("Rule should be added successfully", added)
        assertEquals(7, systemA.getRules().size)

        // Restart app
        val systemB = NexoraAutomationSystem(repository)
        systemB.awaitInitialization()
        val loadedRules = systemB.getRules()
        assertEquals(7, loadedRules.size)

        val retrievedRule = loadedRules.find { it.name == "Study Every Morning" }
        assertNotNull("Custom rule must survive app restart", retrievedRule)
        assertEquals("Prepares study materials at 8 AM", retrievedRule!!.description)
        assertEquals(AutomationTriggerType.DAY_STARTED, retrievedRule.triggerType)
        assertEquals("morning", retrievedRule.conditionExpression)
        assertEquals(1800000L, retrievedRule.cooldownMillis)
        assertTrue(retrievedRule.enabled)
    }

    @Test
    fun `TEST 3 - Rule enabled-disabled toggle state survives app restart`() = runBlocking {
        val systemA = NexoraAutomationSystem(repository)
        systemA.awaitInitialization()
        
        // Disable Morning Plan Assistant
        val disabled = systemA.toggleRule("Morning Plan Assistant", enabled = false)
        assertNotNull(disabled)
        assertFalse(disabled!!.enabled)

        // Restart app
        val systemB = NexoraAutomationSystem(repository)
        systemB.awaitInitialization()
        val ruleInB = systemB.getRules().find { it.name == "Morning Plan Assistant" }
        assertNotNull(ruleInB)
        assertFalse("Disabled state must persist across restart", ruleInB!!.enabled)

        // Re-enable in systemB
        val enabled = systemB.toggleRule("Morning Plan Assistant", enabled = true)
        assertNotNull(enabled)
        assertTrue(enabled!!.enabled)

        // Restart app again
        val systemC = NexoraAutomationSystem(repository)
        systemC.awaitInitialization()
        val ruleInC = systemC.getRules().find { it.name == "Morning Plan Assistant" }
        assertNotNull(ruleInC)
        assertTrue("Re-enabled state must persist across restart", ruleInC!!.enabled)
    }

    @Test
    fun `TEST 4 - Deleted automation remains deleted after app restart`() = runBlocking {
        val systemA = NexoraAutomationSystem(repository)
        systemA.awaitInitialization()
        assertTrue(systemA.getRules().any { it.name == "Morning Plan Assistant" })

        // Delete rule
        val deleted = systemA.deleteRule("Morning Plan Assistant")
        assertTrue("Delete must succeed", deleted)
        assertFalse(systemA.getRules().any { it.name == "Morning Plan Assistant" })
        assertEquals(5, systemA.getRules().size)

        // Restart app
        val systemB = NexoraAutomationSystem(repository)
        systemB.awaitInitialization()
        assertFalse("Deleted rule must remain deleted after restart",
            systemB.getRules().any { it.name == "Morning Plan Assistant" })
        assertEquals(5, systemB.getRules().size)
    }

    @Test
    fun `TEST 5 - Duplicate protection persists after app restart`() = runBlocking {
        val systemA = NexoraAutomationSystem(repository)
        systemA.awaitInitialization()
        val newRule = AiAutomationRule(
            name = "Deep Work Protocol",
            description = "Blocks focus time",
            triggerType = AutomationTriggerType.DAY_STARTED
        )
        assertTrue(systemA.addRule(newRule))

        // Restart app
        val systemB = NexoraAutomationSystem(repository)
        systemB.awaitInitialization()
        val duplicateAttempt = AiAutomationRule(
            name = "Deep Work Protocol",
            description = "Different description",
            triggerType = AutomationTriggerType.WORKLOAD_CHANGED
        )
        val duplicateResult = systemB.addRule(duplicateAttempt)
        assertFalse("Duplicate creation must be rejected after restart", duplicateResult)
        assertEquals(1, systemB.getRules().count { it.name.equals("Deep Work Protocol", ignoreCase = true) })
    }

    @Test
    fun `TEST 6 - Cooldowns survive app restart and prevent duplicate execution`() = runBlocking {
        val systemA = NexoraAutomationSystem(repository)
        systemA.awaitInitialization()
        val context = AiContext(
            tasksPlannedToday = 20,
            adaptiveProfile = AdaptiveProfile(
                preferredDailyWorkload = 5,
                confidence = AdaptiveConfidence.HIGH
            )
        )

        // 1. Initial trigger fires Workload Manager
        val signals1 = systemA.evaluateTriggers(AutomationTriggerType.WORKLOAD_CHANGED, context)
        assertEquals(1, signals1.size)
        val triggeredRuleA = systemA.getRules().first { it.name == "Workload Manager" }
        val triggeredAt = triggeredRuleA.lastTriggeredAt
        assertTrue("lastTriggeredAt must be recorded", triggeredAt > 0)
        assertEquals(1, triggeredRuleA.runCount)

        // 2. Restart app
        val systemB = NexoraAutomationSystem(repository)
        systemB.awaitInitialization()
        val triggeredRuleB = systemB.getRules().first { it.name == "Workload Manager" }
        assertEquals("lastTriggeredAt must persist across restart", triggeredAt, triggeredRuleB.lastTriggeredAt)
        assertEquals("runCount must persist across restart", 1, triggeredRuleB.runCount)

        // 3. Immediate trigger in systemB must be suppressed by persisted cooldown
        val signals2 = systemB.evaluateTriggers(AutomationTriggerType.WORKLOAD_CHANGED, context)
        assertEquals("Cooldown must prevent immediate duplicate firing after restart", 0, signals2.size)
    }

    @Test
    fun `TEST 7 - Execution history and explainLastRun survive app restart`() = runBlocking {
        val systemA = NexoraAutomationSystem(repository)
        systemA.awaitInitialization()
        val context = AiContext(
            tasksPlannedToday = 20,
            adaptiveProfile = AdaptiveProfile(
                preferredDailyWorkload = 5,
                confidence = AdaptiveConfidence.HIGH
            )
        )

        // Trigger Workload Manager
        systemA.evaluateTriggers(AutomationTriggerType.WORKLOAD_CHANGED, context)
        val explanationA = systemA.explainLastRun("Workload Manager")
        assertTrue(explanationA.contains("Workload Manager"))
        assertTrue(explanationA.contains("WORKLOAD_CHANGED"))

        // Restart app
        val systemB = NexoraAutomationSystem(repository)
        systemB.awaitInitialization()
        val explanationB = systemB.explainLastRun("Workload Manager")
        assertTrue("Execution history must survive app restart", explanationB.contains("Workload Manager"))
        assertTrue("Execution details must survive app restart", explanationB.contains("WORKLOAD_CHANGED"))
    }

    @Test
    fun `TEST 8 - Execution history is bounded in persistent storage`() = runBlocking {
        val system = NexoraAutomationSystem(repository)
        system.awaitInitialization()
        
        // Insert 60 executions into repository
        for (i in 1..60) {
            val record = AutomationExecutionRecord(
                ruleId = "rule_$i",
                ruleName = "Rule $i",
                timestamp = System.currentTimeMillis() + i * 10,
                triggerType = AutomationTriggerType.WORKLOAD_CHANGED,
                conditionMatched = "Overload condition",
                evidence = "Evidence $i",
                actionTaken = "Action $i",
                success = true
            )
            repository.insertAutomationExecution(record)
            repository.trimAutomationExecutions(50)
        }

        val stored = repository.getRecentAutomationExecutions(100)
        assertEquals("Execution logs must be bounded to 50 records", 50, stored.size)
    }

    @Test
    fun `TEST 9 - End-to-end Action Executor CRUD with persistence and outcome integrity`() = runBlocking {
        val automationSystem = NexoraAutomationSystem(repository)
        automationSystem.awaitInitialization()
        val actionExecutor = AiActionExecutor(repository, automationSystem)
        val contextBuilder = AiContextBuilder(repository)
        val localIntentResolver = LocalAiIntentResolver(automationSystem = automationSystem)
        val localProvider = LocalAiProvider(intentResolver = localIntentResolver)
        val providerManager = AiProviderManager(localProvider = localProvider)
        val aiService = LocalNexoraAiService(providerManager = providerManager)
        val toolRegistry = AiToolRegistry(repository, actionExecutor)

        val engine = NexoraAiEngine(
            contextBuilder = contextBuilder,
            aiService = aiService,
            providerManager = providerManager,
            actionExecutor = actionExecutor,
            toolRegistry = toolRegistry,
            repository = repository,
            automationSystem = automationSystem
        )

        // 1. CREATE_AUTOMATION
        val createAction = AiAction(
            type = AiActionType.CREATE_AUTOMATION,
            title = "Create Focus Sentinel",
            description = "Focus rule",
            parameters = mapOf("name" to "Focus Sentinel", "triggerType" to "DAY_STARTED", "userConfirmed" to true)
        )
        val createResult = engine.confirmPendingAction(createAction)
        assertTrue(createResult.success)
        val outcomeCreate = repository.getRecentOutcomes(1).last()
        assertEquals(AiOutcomeType.SUCCESS, outcomeCreate.type)

        // Verify in repository
        val ruleInRepo = repository.getAutomationRule("Focus Sentinel")
        assertNotNull(ruleInRepo)

        // 2. Duplicate CREATE_AUTOMATION fails and records FAILED outcome
        val duplicateResult = engine.confirmPendingAction(createAction)
        assertFalse(duplicateResult.success)
        val outcomeDuplicate = repository.getRecentOutcomes(1).last()
        assertEquals(AiOutcomeType.FAILED, outcomeDuplicate.type)

        // 3. TOGGLE_AUTOMATION
        val toggleAction = AiAction(
            type = AiActionType.TOGGLE_AUTOMATION,
            title = "Toggle Focus Sentinel",
            description = "Disable focus rule",
            parameters = mapOf("ruleName" to "Focus Sentinel", "enabled" to false, "userConfirmed" to true)
        )
        val toggleResult = engine.confirmPendingAction(toggleAction)
        assertTrue(toggleResult.success)
        val outcomeToggle = repository.getRecentOutcomes(1).last()
        assertEquals(AiOutcomeType.SUCCESS, outcomeToggle.type)

        // Verify disabled in repository
        val disabledInRepo = repository.getAutomationRule("Focus Sentinel")
        assertNotNull(disabledInRepo)
        assertFalse(disabledInRepo!!.enabled)

        // 4. DELETE_AUTOMATION
        val deleteAction = AiAction(
            type = AiActionType.DELETE_AUTOMATION,
            title = "Delete Focus Sentinel",
            description = "Remove focus rule",
            parameters = mapOf("ruleName" to "Focus Sentinel", "userConfirmed" to true),
            requiresConfirmation = true
        )
        val deleteResult = engine.confirmPendingAction(deleteAction)
        assertTrue(deleteResult.success)
        val outcomeDelete = repository.getRecentOutcomes(1).last()
        assertEquals(AiOutcomeType.SUCCESS, outcomeDelete.type)

        // Verify deleted from repository
        val deletedInRepo = repository.getAutomationRule("Focus Sentinel")
        assertNull(deletedInRepo)

        // 5. Stale delete action fails and records FAILED outcome
        val staleDeleteResult = engine.confirmPendingAction(deleteAction)
        assertFalse(staleDeleteResult.success)
        val outcomeStale = repository.getRecentOutcomes(1).last()
        assertEquals(AiOutcomeType.FAILED, outcomeStale.type)
    }

    @Test
    fun `TEST 10 - Persistence failure prevents false trigger signals`() = runBlocking {
        // A repository that intentionally rejects updates
        val failingRepo = object : MockNexoraRepository() {
            override suspend fun updateAutomationRule(rule: AiAutomationRule): Boolean {
                return false // simulate database failure
            }
        }

        val system = NexoraAutomationSystem(failingRepo)
        system.awaitInitialization()
        val context = AiContext(
            tasksPlannedToday = 20,
            adaptiveProfile = AdaptiveProfile(preferredDailyWorkload = 5, confidence = AdaptiveConfidence.HIGH)
        )

        val signals = system.evaluateTriggers(AutomationTriggerType.WORKLOAD_CHANGED, context)
        assertEquals("Trigger should NOT be emitted if persistence fails", 0, signals.size)
    }

    @Test
    fun `TEST 11 - Full 11-step lifecycle create, runtime, persist, reload, toggle, reload, delete, reload`() = runBlocking {
        // 1. Create automation
        val system1 = NexoraAutomationSystem(repository)
        system1.awaitInitialization()
        val rule = AiAutomationRule(
            id = "custom_lifecycle_1",
            name = "Productivity Guardian",
            description = "Monitors daily productivity patterns",
            triggerType = AutomationTriggerType.DAY_STARTED,
            conditionExpression = "morning",
            enabled = true
        )
        val created = system1.addRule(rule)
        assertTrue("Rule creation must succeed", created)

        // 2. Confirm it appears in runtime state
        val inRuntime1 = system1.getRules().find { it.name == "Productivity Guardian" }
        assertNotNull("Rule must appear in runtime state", inRuntime1)
        assertTrue("Rule must be enabled", inRuntime1!!.enabled)

        // 3. Confirm it is persisted in repository
        val persisted1 = repository.getAutomationRule("Productivity Guardian")
        assertNotNull("Rule must be persisted in repository", persisted1)
        assertTrue(persisted1!!.enabled)

        // 4. Reload / recreate the automation manager
        val system2 = NexoraAutomationSystem(repository)
        system2.awaitInitialization()

        // 5. Confirm the rule still exists
        val inRuntime2 = system2.getRules().find { it.name == "Productivity Guardian" }
        assertNotNull("Rule must exist after manager reload", inRuntime2)

        // 6. Toggle it (disable)
        val toggled = system2.toggleRule("Productivity Guardian", enabled = false)
        assertNotNull("Toggle must return updated rule", toggled)
        assertFalse("Toggled rule must be disabled", toggled!!.enabled)

        // 7. Reload again
        val system3 = NexoraAutomationSystem(repository)
        system3.awaitInitialization()

        // 8. Confirm the toggle persisted
        val inRuntime3 = system3.getRules().find { it.name == "Productivity Guardian" }
        assertNotNull(inRuntime3)
        assertFalse("Disabled state must persist across reload", inRuntime3!!.enabled)

        // 9. Delete it
        val deleted = system3.deleteRule("Productivity Guardian")
        assertTrue("Delete must succeed", deleted)
        assertNull(system3.getRules().find { it.name == "Productivity Guardian" })

        // 10. Reload again
        val system4 = NexoraAutomationSystem(repository)
        system4.awaitInitialization()

        // 11. Confirm it remains deleted
        val inRuntime4 = system4.getRules().find { it.name == "Productivity Guardian" }
        assertNull("Rule must remain deleted across reload", inRuntime4)
        assertNull("Rule must be gone from persistent storage", repository.getAutomationRule("Productivity Guardian"))
    }

    @Test
    fun `TEST 12 - Custom condition engine handles supported, unsupported, blank, and malformed expressions deterministically`() = runBlocking {
        val system = NexoraAutomationSystem(repository)
        system.awaitInitialization()

        val contextOverloaded = AiContext(tasksPlannedToday = 20, adaptiveProfile = AdaptiveProfile(preferredDailyWorkload = 5, confidence = AdaptiveConfidence.HIGH))
        val contextUnderloaded = AiContext(tasksPlannedToday = 2, adaptiveProfile = AdaptiveProfile(preferredDailyWorkload = 5, confidence = AdaptiveConfidence.HIGH))

        // 1. Supported condition: True
        val ruleOverload = AiAutomationRule(
            id = "c_overload",
            name = "Overload Sentinel",
            description = "Overload detection rule",
            triggerType = AutomationTriggerType.WORKLOAD_CHANGED,
            conditionExpression = "overload"
        )
        system.addRule(ruleOverload)
        val signalsTrue = system.evaluateTriggers(AutomationTriggerType.WORKLOAD_CHANGED, contextOverloaded)
        assertTrue("Supported condition evaluated to true should emit signal", signalsTrue.any { it.title == "Overload Sentinel" })

        // 2. Supported condition: False
        val systemFresh = NexoraAutomationSystem(repository)
        systemFresh.awaitInitialization()
        val signalsFalse = systemFresh.evaluateTriggers(AutomationTriggerType.WORKLOAD_CHANGED, contextUnderloaded)
        assertFalse("Supported condition evaluated to false should not emit signal", signalsFalse.any { it.title == "Overload Sentinel" })

        // 3. Unsupported condition: must return false and NOT silently match
        val ruleUnsupported = AiAutomationRule(
            id = "c_unsupported",
            name = "Fake Condition Rule",
            description = "Rule with unsupported condition",
            triggerType = AutomationTriggerType.WORKLOAD_CHANGED,
            conditionExpression = "xyz_arbitrary_unsupported_condition_123"
        )
        system.addRule(ruleUnsupported)
        val signalsUnsupported = system.evaluateTriggers(AutomationTriggerType.WORKLOAD_CHANGED, contextOverloaded)
        assertFalse("Unsupported condition must never silently match", signalsUnsupported.any { it.title == "Fake Condition Rule" })

        // 4. Blank condition: must return false and not fire
        val ruleBlank = AiAutomationRule(
            id = "c_blank",
            name = "Blank Rule",
            description = "Blank condition rule",
            triggerType = AutomationTriggerType.WORKLOAD_CHANGED,
            conditionExpression = "   "
        )
        system.addRule(ruleBlank)
        val signalsBlank = system.evaluateTriggers(AutomationTriggerType.WORKLOAD_CHANGED, contextOverloaded)
        assertFalse("Blank condition must not fire", signalsBlank.any { it.title == "Blank Rule" })

        // 5. Malformed condition: must return false and not fire
        val ruleMalformed = AiAutomationRule(
            id = "c_malformed",
            name = "Malformed Rule",
            description = "Malformed condition rule",
            triggerType = AutomationTriggerType.WORKLOAD_CHANGED,
            conditionExpression = "&& || == !"
        )
        system.addRule(ruleMalformed)
        val signalsMalformed = system.evaluateTriggers(AutomationTriggerType.WORKLOAD_CHANGED, contextOverloaded)
        assertFalse("Malformed condition must not fire", signalsMalformed.any { it.title == "Malformed Rule" })

        // 6. Disabled rule: must never execute
        system.toggleRule("Overload Sentinel", enabled = false)
        val signalsDisabled = system.evaluateTriggers(AutomationTriggerType.WORKLOAD_CHANGED, contextOverloaded)
        assertFalse("Disabled rule must never fire", signalsDisabled.any { it.title == "Overload Sentinel" })
    }

    @Test
    fun `TEST 13 - Automation execution truthfulness distinguishes proposal from blocked stages`() = runBlocking {
        val system = NexoraAutomationSystem(repository)
        system.awaitInitialization()
        val context = AiContext(tasksPlannedToday = 20, adaptiveProfile = AdaptiveProfile(preferredDailyWorkload = 5, confidence = AdaptiveConfidence.HIGH))

        // First run emits ACTION_PROPOSED
        val signals1 = system.evaluateTriggers(AutomationTriggerType.WORKLOAD_CHANGED, context)
        assertEquals(1, signals1.size)
        val history1 = repository.getRecentAutomationExecutions(5)
        val lastRecord1 = history1.first { it.ruleName == "Workload Manager" }
        assertEquals(AutomationExecutionStage.ACTION_PROPOSED, lastRecord1.stage)

        // Immediate second run is blocked by cooldown
        val signals2 = system.evaluateTriggers(AutomationTriggerType.WORKLOAD_CHANGED, context)
        assertEquals(0, signals2.size)
        val history2 = repository.getRecentAutomationExecutions(5)
        val lastRecord2 = history2.first { it.ruleName == "Workload Manager" }
        assertEquals(AutomationExecutionStage.BLOCKED_BY_COOLDOWN, lastRecord2.stage)

        // Disabled rule run is blocked by disabled rule
        system.toggleRule("Workload Manager", enabled = false)
        val signals3 = system.evaluateTriggers(AutomationTriggerType.WORKLOAD_CHANGED, context)
        assertEquals(0, signals3.size)
        val history3 = repository.getRecentAutomationExecutions(5)
        val lastRecord3 = history3.first { it.ruleName == "Workload Manager" }
        assertEquals(AutomationExecutionStage.BLOCKED_BY_DISABLED_RULE, lastRecord3.stage)
    }

    @Test
    fun `TEST 14 - updateRule does not update runtime state or rulesFlow when persistence fails`() = runBlocking {
        val system = NexoraAutomationSystem(repository)
        system.awaitInitialization()
        val originalRule = system.getRules().first { it.name == "Morning Plan Assistant" }
        val originalDesc = originalRule.description

        repository.failUpdateAutomation = true
        val updated = system.updateRule(originalRule.copy(description = "Should never persist or appear in memory"))
        assertFalse("updateRule must return false on persistence failure", updated)

        val currentRule = system.getRules().first { it.name == "Morning Plan Assistant" }
        assertEquals("Runtime state must retain original description", originalDesc, currentRule.description)
        assertEquals("rulesFlow must retain original description", originalDesc,
            system.rulesFlow.value.first { it.name == "Morning Plan Assistant" }.description)
    }

    @Test
    fun `TEST 15 - toggleRule does not update runtime state when persistence fails`() = runBlocking {
        val system = NexoraAutomationSystem(repository)
        system.awaitInitialization()
        val originalRule = system.getRules().first { it.name == "Morning Plan Assistant" }
        assertTrue(originalRule.enabled)

        repository.failUpdateAutomation = true
        val result = system.toggleRule("Morning Plan Assistant", enabled = false)
        assertNull("toggleRule must return null when persistence fails", result)

        val currentRule = system.getRules().first { it.name == "Morning Plan Assistant" }
        assertTrue("Runtime state must remain enabled when persistence fails", currentRule.enabled)
        assertTrue("rulesFlow must remain enabled when persistence fails",
            system.rulesFlow.value.first { it.name == "Morning Plan Assistant" }.enabled)
    }

    @Test
    fun `TEST 16 - deleteRule does not remove rule from runtime state when persistence fails`() = runBlocking {
        val system = NexoraAutomationSystem(repository)
        system.awaitInitialization()
        assertTrue(system.getRules().any { it.name == "Morning Plan Assistant" })

        repository.failDeleteAutomation = true
        val deleted = system.deleteRule("Morning Plan Assistant")
        assertFalse("deleteRule must return false when persistence fails", deleted)

        assertTrue("Runtime state must still contain rule after failed delete",
            system.getRules().any { it.name == "Morning Plan Assistant" })
        assertTrue("rulesFlow must still contain rule after failed delete",
            system.rulesFlow.value.any { it.name == "Morning Plan Assistant" })
    }

    @Test
    fun `TEST 17 - addRule does not add rule to runtime state when persistence fails`() = runBlocking {
        val system = NexoraAutomationSystem(repository)
        system.awaitInitialization()
        val initialCount = system.getRules().size

        repository.failInsertAutomation = true
        val custom = AiAutomationRule(name = "Unsaved Rule", description = "Will fail", triggerType = AutomationTriggerType.DAY_STARTED)
        val added = system.addRule(custom)
        assertFalse("addRule must return false when persistence fails", added)

        assertEquals("Runtime rule count must not change", initialCount, system.getRules().size)
        assertFalse("Unsaved rule must not exist in runtime state",
            system.getRules().any { it.name == "Unsaved Rule" })
    }

    @Test
    fun `TEST 18 - Failed initial load sets Failed state and does not create duplicate defaults`() = runBlocking {
        val failingRepo = MockNexoraRepository()
        failingRepo.failGetAutomationRules = true

        val system = NexoraAutomationSystem(failingRepo)
        system.awaitInitialization()

        val state = system.initializationState
        assertTrue("Initialization state must be Failed", state is AutomationInitState.Failed)
        assertEquals("Rules must remain empty on failed initialization", 0, system.getRules().size)
    }

    @Test
    fun `TEST 19 - Execution history logging failure does not add unpersisted record to memory`() = runBlocking {
        val system = NexoraAutomationSystem(repository)
        system.awaitInitialization()
        val rule = system.getRules().first { it.name == "Morning Plan Assistant" }

        repository.failInsertExecution = true
        val record = AutomationExecutionRecord(
            ruleId = rule.id,
            ruleName = rule.name,
            timestamp = System.currentTimeMillis(),
            triggerType = rule.triggerType,
            conditionMatched = "test",
            evidence = "test",
            actionTaken = "test detail",
            success = true,
            stage = AutomationExecutionStage.ACTION_EXECUTED
        )
        system.recordExecution(record)

        val recentInMemory = system.getRecentExecutions(10)
        assertFalse("Unpersisted execution should not be retained in runtime logs",
            recentInMemory.any { it.actionTaken == "test detail" })
    }

    @Test
    fun `TEST 20 - Concurrent addRule requests reject duplicate rule creation`() = runBlocking {
        val system = NexoraAutomationSystem(repository)
        system.awaitInitialization()
        val initialCount = system.getRules().size

        // Launch concurrent attempts with same name
        val jobs = (1..5).map { index ->
            async(Dispatchers.Default) {
                system.addRule(AiAutomationRule(
                    name = "Concurrent Unique Rule",
                    description = "Attempt $index",
                    triggerType = AutomationTriggerType.DAY_STARTED
                ))
            }
        }

        val results = jobs.awaitAll()
        val successCount = results.count { it }
        assertEquals("Exactly one concurrent creation must succeed", 1, successCount)
        assertEquals("Only one rule should be added", initialCount + 1, system.getRules().size)
        assertEquals(1, system.getRules().count { it.name == "Concurrent Unique Rule" })
    }
}

