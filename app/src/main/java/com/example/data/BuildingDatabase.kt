package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserEntity::class,
        ApartmentEntity::class,
        ProjectEntity::class,
        LedgerEntity::class,
        MaintenanceEntity::class,
        ElevatorEntity::class,
        AnnouncementEntity::class,
        MeetingEntity::class,
        VotingEntity::class,
        VoteRecordEntity::class,
        DocumentEntity::class,
        AuditLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class BuildingDatabase : RoomDatabase() {
    abstract fun buildingDao(): BuildingDao

    companion object {
        @Volatile
        private var INSTANCE: BuildingDatabase? = null

        fun getDatabase(context: Context): BuildingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BuildingDatabase::class.java,
                    "amarati_building.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
