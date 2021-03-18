package com.cbnumap.cbnumap.server

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Coordinate::class], version = 1)
abstract class CoordinateDB : RoomDatabase() {
    abstract fun coordinateDao(): CoordinateDao

    companion object {
        private var INSTANCE: CoordinateDB? = null

        fun getInstance(context: Context): CoordinateDB? {
            if (INSTANCE == null) {
                synchronized(CoordinateDB::class) {
                    INSTANCE = Room.databaseBuilder(
                        //context.applicationContext,
                        context,
                        CoordinateDB::class.java, "nowCoordinateDB.db"
                    //).fallbackToDestructiveMigration()
                    ).createFromAsset("CoordinateDB.db")
                        .build()
                }
            }
            return INSTANCE
        }

        fun destroyInstance(){
            INSTANCE = null
        }
    }
}