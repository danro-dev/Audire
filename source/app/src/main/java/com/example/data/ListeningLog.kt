package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "listening_logs")
data class ListeningLog(
    @PrimaryKey val date: String, // Format: "yyyy-MM-dd"
    val durationMillis: Long
)
