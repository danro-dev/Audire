package com.aistudio.sanctuary.audpbk.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audiobooks")
data class Audiobook(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val author: String,
    val durationMillis: Long,
    val filePath: String,
    val coverUrl: String,
    val currentPositionMillis: Long = 0,
    val lastListenedTime: Long = 0,
    val isCompleted: Boolean = false,
    val isFavorite: Boolean = false
)
