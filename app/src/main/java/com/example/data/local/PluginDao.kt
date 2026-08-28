package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PluginDao {
    @Query("SELECT * FROM plugin_extensions ORDER BY isEnabled DESC, name ASC")
    fun getAllPlugins(): Flow<List<PluginExtension>>

    @Query("SELECT * FROM plugin_extensions WHERE isEnabled = 1")
    fun getActivePlugins(): Flow<List<PluginExtension>>

    @Query("SELECT * FROM plugin_extensions WHERE packageId = :packageId LIMIT 1")
    suspend fun getPluginByPackageId(packageId: String): PluginExtension?

    @Query("SELECT * FROM plugin_extensions WHERE id = :id")
    suspend fun getPluginById(id: Long): PluginExtension?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlugin(plugin: PluginExtension): Long

    @Update
    suspend fun updatePlugin(plugin: PluginExtension)

    @Query("DELETE FROM plugin_extensions WHERE id = :id")
    suspend fun deletePluginById(id: Long)

    @Query("UPDATE plugin_extensions SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun togglePluginEnabled(id: Long, isEnabled: Boolean)

    @Query("UPDATE plugin_extensions SET version = :version, changelog = :changelog, actionsJson = :actionsJson, sourceCode = :sourceCode, lastUpdatedAt = :timestamp WHERE id = :id")
    suspend fun upgradePlugin(id: Long, version: String, changelog: String, actionsJson: String, sourceCode: String, timestamp: Long)
}
