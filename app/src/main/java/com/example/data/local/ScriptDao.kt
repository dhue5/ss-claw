package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScriptDao {
    @Query("SELECT * FROM automation_scripts ORDER BY updatedAt DESC")
    fun getAllScripts(): Flow<List<AutomationScript>>

    @Query("SELECT * FROM automation_scripts WHERE isEnabled = 1 ORDER BY name ASC")
    fun getActiveScripts(): Flow<List<AutomationScript>>

    @Query("SELECT * FROM automation_scripts WHERE triggerType = :triggerType")
    suspend fun getScriptsByTrigger(triggerType: String): List<AutomationScript>

    @Query("SELECT * FROM automation_scripts WHERE id = :id")
    suspend fun getScriptById(id: Long): AutomationScript?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScript(script: AutomationScript): Long

    @Update
    suspend fun updateScript(script: AutomationScript)

    @Query("DELETE FROM automation_scripts WHERE id = :id")
    suspend fun deleteScriptById(id: Long)

    @Query("UPDATE automation_scripts SET executionCount = executionCount + 1, lastExecutedAt = :timestamp WHERE id = :id")
    suspend fun incrementExecution(id: Long, timestamp: Long)

    @Query("UPDATE automation_scripts SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun toggleScriptEnabled(id: Long, isEnabled: Boolean)
}
