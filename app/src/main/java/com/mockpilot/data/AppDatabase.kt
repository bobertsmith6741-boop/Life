package com.mockpilot.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [SavedPlace::class, RouteEntity::class, ScheduleEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedPlaceDao(): SavedPlaceDao
    abstract fun routeDao(): RouteDao
    abstract fun scheduleDao(): ScheduleDao
}
