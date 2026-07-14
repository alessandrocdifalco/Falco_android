package com.alessandro.falco.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TrackEntity::class], version = 1, exportSchema = true)
abstract class FalcoDatabase : RoomDatabase() {
    abstract fun tracks(): TrackDao
    companion object {
        @Volatile private var instance: FalcoDatabase? = null
        fun get(context: Context): FalcoDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context, FalcoDatabase::class.java, "falco.db").build().also { instance = it }
        }
    }
}
