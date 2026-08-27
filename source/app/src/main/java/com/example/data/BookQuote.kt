package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "book_quotes")
data class BookQuote(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bookId: Int,
    val bookTitle: String,
    val quoteText: String,
    val pageReference: String = "", // e.g., "Page 15" or "Ch 3"
    val timestamp: Long = System.currentTimeMillis()
)
