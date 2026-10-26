package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tts_models")
data class TtsModelEntity(
    @PrimaryKey
    val id: String, // e.g. "hexgrad/Kokoro-82M", "onnx-community/Kokoro-82M-v1.0-ONNX"
    val name: String,
    val description: String,
    val source: String, // "HUGGING_FACE" or "LOCAL_IMPORT"
    val repoId: String,
    val fileName: String,
    val sizeBytes: Long,
    val isDownloaded: Boolean = false,
    val localFilePath: String? = null,
    val sampleRate: Int = 24000,
    val isSelected: Boolean = false
)

@Entity(tableName = "voice_profiles")
data class VoiceProfileEntity(
    @PrimaryKey
    val id: String, // e.g. "af_heart", "am_adam", "bf_emma"
    val name: String,
    val language: String = "en-us",
    val gender: String = "Female",
    val accent: String = "American",
    val description: String = "Warm & Natural",
    val pitch: Float = 1.0f,
    val speed: Float = 1.0f,
    val energy: Float = 1.0f,
    val isCustomImport: Boolean = false,
    val isSelected: Boolean = false
)
