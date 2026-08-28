package com.example.data.repository

import com.example.data.local.*
import kotlinx.coroutines.flow.Flow

class HermesRepository(
    private val scriptDao: ScriptDao,
    private val pluginDao: PluginDao,
    private val logDao: LogDao,
    private val configDao: ConfigDao,
    private val modelEndpointDao: ModelEndpointDao
) {
    // AI Model Endpoints (Multi-Model & Multi-API support)
    val allModels: Flow<List<AiModelEndpoint>> = modelEndpointDao.getAllModelsFlow()
    val activeModelFlow: Flow<AiModelEndpoint?> = modelEndpointDao.getActiveModelFlow()

    suspend fun getAllModels(): List<AiModelEndpoint> = modelEndpointDao.getAllModels()
    suspend fun getActiveModel(): AiModelEndpoint? = modelEndpointDao.getActiveModel()
    suspend fun getModelById(id: Long): AiModelEndpoint? = modelEndpointDao.getModelById(id)
    suspend fun insertModel(model: AiModelEndpoint): Long = modelEndpointDao.insertModel(model)
    suspend fun updateModel(model: AiModelEndpoint) = modelEndpointDao.updateModel(model)
    suspend fun deleteModel(id: Long) = modelEndpointDao.deleteModelById(id)
    suspend fun setActiveModel(id: Long) = modelEndpointDao.setActiveModel(id)
    suspend fun updateModelLatency(id: Long, latencyMs: Long, status: String) {
        modelEndpointDao.updateLatency(id, latencyMs, status, System.currentTimeMillis())
    }

    // Scripts
    val allScripts: Flow<List<AutomationScript>> = scriptDao.getAllScripts()
    val activeScripts: Flow<List<AutomationScript>> = scriptDao.getActiveScripts()

    suspend fun getScriptsByTrigger(triggerType: String): List<AutomationScript> = scriptDao.getScriptsByTrigger(triggerType)
    suspend fun getScriptById(id: Long): AutomationScript? = scriptDao.getScriptById(id)
    suspend fun insertScript(script: AutomationScript): Long = scriptDao.insertScript(script)
    suspend fun updateScript(script: AutomationScript) = scriptDao.updateScript(script)
    suspend fun deleteScript(id: Long) = scriptDao.deleteScriptById(id)
    suspend fun toggleScript(id: Long, isEnabled: Boolean) = scriptDao.toggleScriptEnabled(id, isEnabled)
    suspend fun recordScriptExecution(id: Long) = scriptDao.incrementExecution(id, System.currentTimeMillis())

    // Plugins
    val allPlugins: Flow<List<PluginExtension>> = pluginDao.getAllPlugins()
    val activePlugins: Flow<List<PluginExtension>> = pluginDao.getActivePlugins()

    suspend fun getPluginById(id: Long): PluginExtension? = pluginDao.getPluginById(id)
    suspend fun getPluginByPackageId(packageId: String): PluginExtension? = pluginDao.getPluginByPackageId(packageId)
    suspend fun insertPlugin(plugin: PluginExtension): Long = pluginDao.insertPlugin(plugin)
    suspend fun updatePlugin(plugin: PluginExtension) = pluginDao.updatePlugin(plugin)
    suspend fun deletePlugin(id: Long) = pluginDao.deletePluginById(id)
    suspend fun togglePlugin(id: Long, isEnabled: Boolean) = pluginDao.togglePluginEnabled(id, isEnabled)
    suspend fun upgradePlugin(id: Long, version: String, changelog: String, actionsJson: String, sourceCode: String) {
        pluginDao.upgradePlugin(id, version, changelog, actionsJson, sourceCode, System.currentTimeMillis())
    }

    // Execution Logs
    val allLogs: Flow<List<ExecutionLog>> = logDao.getAllLogs()

    suspend fun insertLog(log: ExecutionLog): Long = logDao.insertLog(log)
    suspend fun updateLog(log: ExecutionLog) = logDao.updateLog(log)
    suspend fun clearAllLogs() = logDao.clearAllLogs()
    suspend fun deleteLog(id: Long) = logDao.deleteLogById(id)

    // Config
    val configFlow: Flow<ApiConfig?> = configDao.getConfigFlow()
    suspend fun getConfig(): ApiConfig = configDao.getConfig() ?: ApiConfig()
    suspend fun saveConfig(config: ApiConfig) = configDao.saveConfig(config)
}
