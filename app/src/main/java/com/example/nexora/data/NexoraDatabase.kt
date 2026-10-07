package com.example.nexora.data

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

import androidx.sqlite.execSQL

@Database(
    entities = [
        TaskEntity::class,
        GoalEntity::class,
        DailyProgressEntity::class,
        AiRecommendationHistoryEntity::class,
        AiOutcomeEntity::class,
        AiEvaluationEntity::class,
        AiMemoryEntity::class,
        AiAutomationRuleEntity::class,
        AutomationExecutionEntity::class,
        AiExecutionRecordEntity::class,
        AiActionExecutionEntity::class,
    ],
    version = 5,
    exportSchema = false
)
abstract class NexoraDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

    abstract fun goalDao(): GoalDao

    abstract fun dailyProgressDao(): DailyProgressDao

    abstract fun aiLearningDao(): AiLearningDao

    abstract fun aiMemoryDao(): AiMemoryDao

    abstract fun aiAutomationDao(): AiAutomationDao

    abstract fun aiExecutionDao(): AiExecutionDao

    companion object {

        @Volatile
        private var INSTANCE: NexoraDatabase? = null

        /**
         * Migration from version 2 to 3.
         */
        private val MIGRATION_2_3 = Migration(2, 3) { connection: SQLiteConnection ->
            // Schema updated between v2 and v3
        }

        /**
         * Migration from version 3 to 4.
         * Creates persistent tables for AI automation rules and execution records.
         * All existing task, goal, progress, and learning data is preserved.
         */
        private val MIGRATION_3_4 = Migration(3, 4) { connection: SQLiteConnection ->
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ai_automation_rule` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `triggerType` TEXT NOT NULL,
                    `enabled` INTEGER NOT NULL,
                    `cooldownMillis` INTEGER NOT NULL,
                    `lastTriggeredAt` INTEGER NOT NULL,
                    `lastTriggeredFingerprint` TEXT,
                    `isStateChanging` INTEGER NOT NULL,
                    `targetActionType` TEXT,
                    `conditionExpression` TEXT,
                    `lastRunReason` TEXT,
                    `runCount` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `automation_execution_record` (
                    `id` TEXT NOT NULL,
                    `ruleId` TEXT NOT NULL,
                    `ruleName` TEXT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `triggerType` TEXT NOT NULL,
                    `conditionMatched` TEXT NOT NULL,
                    `evidence` TEXT NOT NULL,
                    `actionTaken` TEXT NOT NULL,
                    `success` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
        }

        /**
         * Migration from version 4 to 5.
         * Creates persistent tables for AI execution history and individual child action execution items.
         * All existing task, goal, progress, automation, and learning data is preserved.
         */
        private val MIGRATION_4_5 = Migration(4, 5) { connection: SQLiteConnection ->
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ai_execution_record` (
                    `id` TEXT NOT NULL,
                    `userPrompt` TEXT,
                    `detectedIntent` TEXT,
                    `overallStatus` TEXT NOT NULL,
                    `confirmationRequired` INTEGER NOT NULL,
                    `userConfirmed` INTEGER,
                    `totalProposedActions` INTEGER NOT NULL,
                    `executedActionCount` INTEGER NOT NULL,
                    `successActionCount` INTEGER NOT NULL,
                    `failedActionCount` INTEGER NOT NULL,
                    `skippedActionCount` INTEGER NOT NULL,
                    `summaryMessage` TEXT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            connection.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_ai_execution_record_timestamp` ON `ai_execution_record` (`timestamp`)
                """.trimIndent()
            )
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ai_action_execution_record` (
                    `id` TEXT NOT NULL,
                    `executionRecordId` TEXT NOT NULL,
                    `actionId` TEXT NOT NULL,
                    `executionOrder` INTEGER NOT NULL,
                    `actionType` TEXT NOT NULL,
                    `actionTitle` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `affectedTaskId` INTEGER,
                    `affectedGoalId` INTEGER,
                    `message` TEXT NOT NULL,
                    `error` TEXT,
                    `failureReason` TEXT,
                    `timestamp` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            connection.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_ai_action_execution_record_executionRecordId` ON `ai_action_execution_record` (`executionRecordId`)
                """.trimIndent()
            )
            connection.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_ai_action_execution_record_timestamp` ON `ai_action_execution_record` (`timestamp`)
                """.trimIndent()
            )
        }

        fun getDatabase(context: Context): NexoraDatabase {

            return INSTANCE ?: synchronized(this) {

                val instance =
                    Room.databaseBuilder<NexoraDatabase>(
                        context.applicationContext,
                        "nexora_database"
                    )
                        .setDriver(
                            BundledSQLiteDriver()
                        )
                        .addMigrations(
                            MIGRATION_2_3,
                            MIGRATION_3_4,
                            MIGRATION_4_5
                        )
                        .build()

                INSTANCE = instance

                instance
            }
        }
    }
}