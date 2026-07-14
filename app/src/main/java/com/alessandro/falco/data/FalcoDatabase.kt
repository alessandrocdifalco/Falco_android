package com.alessandro.falco.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [TrackEntity::class], version = 2, exportSchema = true)
abstract class FalcoDatabase : RoomDatabase() {
    abstract fun tracks(): TrackDao
    companion object {
        @Volatile private var instance: FalcoDatabase? = null
        fun get(context: Context): FalcoDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context, FalcoDatabase::class.java, "falco.db")
                .addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN waveformCache TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tracks ADD COLUMN maestCache TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tracks ADD COLUMN aiAnalyzedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tracks ADD COLUMN aiSourceModifiedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tracks ADD COLUMN aiAnalysisError TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
