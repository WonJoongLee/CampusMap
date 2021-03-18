package com.cbnumap.cbnumap.server

import androidx.room.Dao
import androidx.room.Query

@Dao
interface CoordinateDao {
    @Query("SELECT * FROM coordinate")
    fun getAll(): List<Coordinate>
}