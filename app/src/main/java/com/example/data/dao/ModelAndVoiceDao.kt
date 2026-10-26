package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.TtsModelEntity
import com.example.data.model.VoiceProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {
    @Query("SELECT * FROM tts_models ORDER BY isSelected DESC, isDownloaded DESC, name ASC")
    fun getAllModels(): Flow<List<TtsModelEntity>>

    @Query("SELECT * FROM tts_models ORDER BY isSelected DESC, isDownloaded DESC, name ASC")
    suspend fun getAllModelsSync(): List<TtsModelEntity>

    @Query("SELECT * FROM tts_models WHERE isSelected = 1 LIMIT 1")
    fun getSelectedModel(): Flow<TtsModelEntity?>

    @Query("SELECT * FROM tts_models WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedModelSync(): TtsModelEntity?

    @Query("SELECT * FROM tts_models WHERE id = :id LIMIT 1")
    suspend fun getModelById(id: String): TtsModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: TtsModelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModels(models: List<TtsModelEntity>)

    @Update
    suspend fun updateModel(model: TtsModelEntity)

    @Query("UPDATE tts_models SET isSelected = CASE WHEN id = :selectedId THEN 1 ELSE 0 END")
    suspend fun setSelectedModel(selectedId: String)

    @Query("DELETE FROM tts_models WHERE id = :id")
    suspend fun deleteModel(id: String)
}

@Dao
interface VoiceDao {
    @Query("SELECT * FROM voice_profiles ORDER BY isSelected DESC, isCustomImport DESC, name ASC")
    fun getAllVoices(): Flow<List<VoiceProfileEntity>>

    @Query("SELECT * FROM voice_profiles WHERE isSelected = 1 LIMIT 1")
    fun getSelectedVoice(): Flow<VoiceProfileEntity?>

    @Query("SELECT * FROM voice_profiles WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedVoiceSync(): VoiceProfileEntity?

    @Query("SELECT * FROM voice_profiles WHERE id = :id LIMIT 1")
    suspend fun getVoiceById(id: String): VoiceProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoice(voice: VoiceProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoices(voices: List<VoiceProfileEntity>)

    @Update
    suspend fun updateVoice(voice: VoiceProfileEntity)

    @Query("UPDATE voice_profiles SET isSelected = CASE WHEN id = :selectedId THEN 1 ELSE 0 END")
    suspend fun setSelectedVoice(selectedId: String)

    @Query("DELETE FROM voice_profiles WHERE id = :id")
    suspend fun deleteVoice(id: String)
}
