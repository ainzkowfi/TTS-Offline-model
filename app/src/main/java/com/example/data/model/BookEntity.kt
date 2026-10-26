package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val author: String = "Unknown Author",
    val content: String,
    val totalSentences: Int = 0,
    val currentSentenceIndex: Int = 0,
    val lastReadAt: Long = System.currentTimeMillis(),
    val isPreset: Boolean = false
)
