package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_directories")
data class ScanDirectory(
    @PrimaryKey val path: String,
    val titlesFound: Int,
    val lastScanTime: Long
)
