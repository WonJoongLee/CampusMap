package com.cbnumap.cbnumap.server

data class Point(
    val id: Int,
    val latitude: Double,
    val longitude: Double,
    val kor_name: String,
    val weight : Int
) : Comparable<Point> {
    override fun compareTo(other: Point): Int = weight - other.weight
}