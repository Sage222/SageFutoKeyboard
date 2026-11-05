package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        KeySpatialProfileEntity::class,
        UserWordFrequencyEntity::class,
        UserBigramEntity::class,
        GesturePrototypeEntity::class,
        GestureSessionLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AdaptiveStyleDatabase : RoomDatabase() {
    abstract fun adaptiveStyleDao(): AdaptiveStyleDao

    companion object {
        @Volatile
        private var INSTANCE: AdaptiveStyleDatabase? = null

        fun getInstance(context: Context): AdaptiveStyleDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AdaptiveStyleDatabase::class.java,
                    "futo_adaptive_gesture.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
