package com.apexaurum.pocket.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CachedMessage::class, CachedAgent::class, CachedMemory::class, CachedCortexMemory::class, OfflineAction::class],
    version = 2,
    exportSchema = false,
)
abstract class ApexDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun agentDao(): AgentDao
    abstract fun memoryDao(): MemoryDao
    abstract fun cortexDao(): CortexDao
    abstract fun offlineActionDao(): OfflineActionDao

    companion object {
        @Volatile
        private var INSTANCE: ApexDatabase? = null

        fun getInstance(context: Context): ApexDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ApexDatabase::class.java,
                    "apex_pocket.db",
                ).fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
