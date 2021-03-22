package com.cbnumap.cbnumap.server

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "coordinate")
class Coordinate(
    @PrimaryKey(autoGenerate = true) var id: Int,
    @ColumnInfo(name = "latitude") var latitude: Double,
    @ColumnInfo(name = "longitude") var longitude: Double,
    @ColumnInfo(name = "kor_name") var kor_name: String,
    @ColumnInfo(name = "building_id") var building_id: String
) {
    constructor() : this( -1,0.0, 0.0, "", "")
}