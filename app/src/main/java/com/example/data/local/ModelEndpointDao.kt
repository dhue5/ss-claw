package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelEndpointDao {
    @Query("SELECT * FROM ai_model_endpoints ORDER BY isPreset DESC, id ASC")
    fun getAllModelsFlow(): Flow<List<AiModelEndpoint>>

    @Query("SELECT * FROM ai_model_endpoints")
    suspend fun getAllModels(): List<AiModelEndpoint>

    @Query("SELECT * FROM ai_model_endpoints WHERE isActive = 1 LIMIT 1")
    fun getActiveModelFlow(): Flow<AiModelEndpoint?>

    @Query("SELECT * FROM ai_model_endpoints WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveModel(): AiModelEndpoint?

    @Query("SELECT * FROM ai_model_endpoints WHERE id = :id LIMIT 1")
    suspend fun getModelById(id: Long): AiModelEndpoint?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: AiModelEndpoint): Long

    @Update
    suspend fun updateModel(model: AiModelEndpoint)

    @Query("DELETE FROM ai_model_endpoints WHERE id = :id")
    suspend fun deleteModelById(id: Long)

    @Query("UPDATE ai_model_endpoints SET isActive = 0")
    suspend fun clearActiveFlag()

    @Transaction
    suspend fun setActiveModel(id: Long) {
        clearActiveFlag()
        setModelActiveById(id)
    }

    @Query("UPDATE ai_model_endpoints SET isActive = 1 WHERE id = :id")
    suspend fun setModelActiveById(id: Long)

    @Query("UPDATE ai_model_endpoints SET latencyMs = :latencyMs, lastTestStatus = :status, lastTestTime = :time WHERE id = :id")
    suspend fun updateLatency(id: Long, latencyMs: Long, status: String, time: Long)

    @Query("DELETE FROM ai_model_endpoints WHERE isPreset = 1")
    suspend fun deletePresets()
}
