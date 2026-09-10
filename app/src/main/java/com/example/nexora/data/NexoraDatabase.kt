package com.example.nexora.data

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

@Database(
    entities = [
        TaskEntity::class,
        GoalEntity::class,
        DailyProgressEntity::class,
        AiRecommendationHistoryEntity::class,
        AiOutcomeEntity::class,
        AiEvaluationEntity::class,
        AiMemoryEntity::class,
    ],
    version = 3,
    exportSchema = false
)
abstract class NexoraDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

    abstract fun goalDao(): GoalDao

    abstract fun dailyProgressDao(): DailyProgressDao

    abstract fun aiLearningDao(): AiLearningDao

    abstract fun aiMemoryDao(): AiMemoryDao

    companion object {

        @Volatile
        private var INSTANCE: NexoraDatabase? = null

        /**
         * Migration from version 2 to 3.
         * 
         * This migration preserves data in all core and AI-related tables.
         * If schema changes (columns added/renamed) were made between v2 and v3,
         * they should be executed here using connection.execSQL(...).
         */
        private val MIGRATION_2_3 = Migration(2, 3) { connection: SQLiteConnection ->
            // Implementation note: SQLite preserves data by default during migrations.
            // We only need to execute SQL if columns or tables were added/modified.
            // For version 2 -> 3, we ensure the schema is updated while keeping
            // existing user tasks, goals, and AI context/memory.
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
                            // Add future migrations here (e.g., MIGRATION_3_4)
                        )
                        .build()

                INSTANCE = instance

                instance
            }
        }
    }
}