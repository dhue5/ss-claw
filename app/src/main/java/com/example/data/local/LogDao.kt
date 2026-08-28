package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Query("SELECT * FROM execution_logs ORDER BY timestamp DESC LIMIT 100")
    fun getAllLogs(): Flow<List<ExecutionLog>>

    @Query("SELECT * FROM execution_logs WHERE id = :id")
    suspend fun getLogById(id: Long): ExecutionLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ExecutionLog): Long

    @Update
    suspend fun updateLog(log: ExecutionLog)

    @Query("DELETE FROM execution_logs")
    suspend fun clearAllLogs()

    @Query("DELETE FROM execution_logs WHERE id = :id")
    suspend fun deleteLogById(id: Long)
}
