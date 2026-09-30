package com.example.nexora.ai

import com.example.nexora.ai.evaluation.MockNexoraRepository
import com.example.nexora.data.NexoraRepository
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
    fun `TEST 1 - Fresh database initializes default automations exactly once and restart does not duplicate`() {
        // Fresh initialization
        val systemA = NexoraAutomationSystem(repository)
        val initialRules = systemA.getRules()
        assertEquals("Default rules should be initialized once", 6, initialRules.size)
        assertTrue(initialRules.any { it.name == "Morning Plan Assistant" })
        assertTrue(initialRules.any { it.name == "Workload Manager" })

        // Simulate app restart: destroy systemA, create systemB using the same repository
        val systemB = NexoraAutomationSystem(repository)
        val restartedRules = systemB.getRules()
        assertEquals("Restart must not duplicate default rules", 6, restartedRules.size)
        assertEquals(
            initialRules.map { it.name }.sorted(),
            restartedRules.map { it.name }.sorted()
        )
    }

    @Test
    fun `TEST 2 - User-created automation survives app restart`() {
        val systemA = NexoraAutomationSystem(repository)
        val customRule = AiAutomationRule(
            name = "Study Every Morning",
            description = "Prepares study materials at 8 AM",
            triggerType = AutomationTriggerType.DAY_STARTED,
            conditionExpression = "study",
            cooldownMillis = 1800000
        )
        val added = systemA.addRule(customRule)
        assertTrue("Rule should be added successfully", added)
        assertEquals(7, systemA.getRules().size)

        // Restart app
        val systemB = NexoraAutomationSystem(repository)
        val loadedRules = systemB.getRules()
        assertEquals(7, loadedRules.size)

        val retrievedRule = loadedRules.find { it.name == "Study Every Morning" }
        assertNotNull("Custom rule must survive app restart", retrievedRule)
        assertEquals("Prepares study materials at 8 AM", retrievedRule!!.description)
        assertEquals(AutomationTriggerType.DAY_STARTED, retrievedRule.triggerType)
        assertEquals("study", retrievedRule.conditionExpression)
        assertEquals(1800000L, retrievedRule.cooldownMillis)
        assertTrue(retrievedRule.enabled)
    }

    @Test
    fun `TEST 3 - Rule enabled-disabled toggle state survives app restart`() {
        val systemA = NexoraAutomationSystem(repository)
        
        // Disable Morning Plan Assistant
        val disabled = systemA.toggleRule("Morning Plan Assistant", enabled = false)
        assertNotNull(disabled)
        assertFalse(disabled!!.enabled)

        // Restart app
        val systemB = NexoraAutomationSystem(repository)
        val ruleInB = systemB.getRules().find { it.name == "Morning Plan Assistant" }
        assertNotNull(ruleInB)
        assertFalse("Disabled state must persist across restart", ruleInB!!.enabled)

        // Re-enable in systemB
        val enabled = systemB.toggleRule("Morning Plan Assistant", enabled = true)
        assertNotNull(enabled)
        assertTrue(enabled!!.enabled)

        // Restart app again
        val systemC = NexoraAutomationSystem(repository)
        val ruleInC = systemC.getRules().find { it.name == "Morning Plan Assistant" }
        assertNotNull(ruleInC)
        assertTrue("Re-enabled state must persist across restart", ruleInC!!.enabled)
    }

    @Test
    fun `TEST 4 - Deleted automation remains deleted after app restart`() {
        val systemA = NexoraAutomationSystem(repository)
        assertTrue(systemA.getRules().any { it.name == "Morning Plan Assistant" })

        // Delete rule
        val deleted = systemA.deleteRule("Morning Plan Assistant")
        assertTrue("Delete must succeed", deleted)
        assertFalse(systemA.getRules().any { it.name == "Morning Plan Assistant" })
        assertEquals(5, systemA.getRules().size)

        // Restart app
        val systemB = NexoraAutomationSystem(repository)
        assertFalse("Deleted rule must remain deleted after restart",
            systemB.getRules().any { it.name == "Morning Plan Assistant" })
        assertEquals(5, systemB.getRules().size)
    }

    @Test
    fun `TEST 5 - Duplicate protection persists after app restart`() {
        val systemA = NexoraAutomationSystem(repository)
        val newRule = AiAutomationRule(
            name = "Deep Work Protocol",
            description = "Blocks focus time",
            triggerType = AutomationTriggerType.DAY_STARTED
        )
        assertTrue(systemA.addRule(newRule))

        // Restart app
        val systemB = NexoraAutomationSystem(repository)
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
    fun `TEST 6 - Cooldowns survive app restart and prevent duplicate execution`() {
        val systemA = NexoraAutomationSystem(repository)
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
        val triggeredRuleB = systemB.getRules().first { it.name == "Workload Manager" }
        assertEquals("lastTriggeredAt must persist across restart", triggeredAt, triggeredRuleB.lastTriggeredAt)
        assertEquals("runCount must persist across restart", 1, triggeredRuleB.runCount)

        // 3. Immediate trigger in systemB must be suppressed by persisted cooldown
        val signals2 = systemB.evaluateTriggers(AutomationTriggerType.WORKLOAD_CHANGED, context)
        assertEquals("Cooldown must prevent immediate duplicate firing after restart", 0, signals2.size)
    }

    @Test
    fun `TEST 7 - Execution history and explainLastRun survive app restart`() {
        val systemA = NexoraAutomationSystem(repository)
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
        val explanationB = systemB.explainLastRun("Workload Manager")
        assertTrue("Execution history must survive app restart", explanationB.contains("Workload Manager"))
        assertTrue("Execution details must survive app restart", explanationB.contains("WORKLOAD_CHANGED"))
    }

    @Test
    fun `TEST 8 - Execution history is bounded in persistent storage`() = runBlocking {
        val system = NexoraAutomationSystem(repository)
        
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
        val createResult = engine.executeAction(createAction)
        assertTrue(createResult.success)
        val outcomeCreate = repository.getRecentOutcomes(1).last()
        assertEquals(AiOutcomeType.SUCCESS, outcomeCreate.type)

        // Verify in repository
        val ruleInRepo = repository.getAutomationRule("Focus Sentinel")
        assertNotNull(ruleInRepo)

        // 2. Duplicate CREATE_AUTOMATION fails and records FAILED outcome
        val duplicateResult = engine.executeAction(createAction)
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
        val toggleResult = engine.executeAction(toggleAction)
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
        val deleteResult = engine.executeAction(deleteAction)
        assertTrue(deleteResult.success)
        val outcomeDelete = repository.getRecentOutcomes(1).last()
        assertEquals(AiOutcomeType.SUCCESS, outcomeDelete.type)

        // Verify deleted from repository
        val deletedInRepo = repository.getAutomationRule("Focus Sentinel")
        assertNull(deletedInRepo)

        // 5. Stale delete action fails and records FAILED outcome
        val staleDeleteResult = engine.executeAction(deleteAction)
        assertFalse(staleDeleteResult.success)
        val outcomeStale = repository.getRecentOutcomes(1).last()
        assertEquals(AiOutcomeType.FAILED, outcomeStale.type)
    }

    @Test
    fun `TEST 10 - Persistence failure prevents false trigger signals`() {
        // A repository that intentionally rejects updates
        val failingRepo = object : MockNexoraRepository() {
            override suspend fun updateAutomationRule(rule: AiAutomationRule): Boolean {
                return false // simulate database failure
            }
        }

        val system = NexoraAutomationSystem(failingRepo)
        val context = AiContext(
            tasksPlannedToday = 20,
            adaptiveProfile = AdaptiveProfile(preferredDailyWorkload = 5, confidence = AdaptiveConfidence.HIGH)
        )

        val signals = system.evaluateTriggers(AutomationTriggerType.WORKLOAD_CHANGED, context)
        assertEquals("Trigger should NOT be emitted if persistence fails", 0, signals.size)
    }
}
