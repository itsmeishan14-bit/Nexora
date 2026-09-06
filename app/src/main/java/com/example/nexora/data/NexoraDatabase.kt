package com.example.nexora.data

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

@Database(
    entities = [
        TaskEntity::class,
        GoalEntity::class,
        DailyProgressEntity::class,
        AiRecommendationHistoryEntity::class,
        AiOutcomeEntity::class,
        AiEvaluationEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class NexoraDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

    abstract fun goalDao(): GoalDao

    abstract fun dailyProgressDao(): DailyProgressDao

    abstract fun aiLearningDao(): AiLearningDao

    companion object {

        @Volatile
        private var INSTANCE: NexoraDatabase? = null

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
                        .build()

                INSTANCE = instance

                instance
            }
        }
    }
}