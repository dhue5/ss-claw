package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ConfigDao {
    @Query("SELECT * FROM api_configs WHERE id = 'default_config' LIMIT 1")
    fun getConfigFlow(): Flow<ApiConfig?>

    @Query("SELECT * FROM api_configs WHERE id = 'default_config' LIMIT 1")
    suspend fun getConfig(): ApiConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfig(config: ApiConfig)
}
