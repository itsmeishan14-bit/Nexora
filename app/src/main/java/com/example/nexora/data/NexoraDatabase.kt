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
    ],
    version = 4,
    exportSchema = false
)
abstract class NexoraDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

    abstract fun goalDao(): GoalDao

    abstract fun dailyProgressDao(): DailyProgressDao

    abstract fun aiLearningDao(): AiLearningDao

    abstract fun aiMemoryDao(): AiMemoryDao

    abstract fun aiAutomationDao(): AiAutomationDao

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
                            MIGRATION_3_4
                        )
                        .build()

                INSTANCE = instance

                instance
            }
        }
    }
}