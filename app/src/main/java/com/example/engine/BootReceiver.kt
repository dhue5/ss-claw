package com.example.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.HermesApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED) {

            val app = context.applicationContext as? HermesApplication ?: return
            CoroutineScope(Dispatchers.IO).launch {
                val config = app.repository.getConfig()
                if (config.enableBackgroundDaemon && config.autoStartOnBoot) {
                    HermesBackgroundService.start(context)
                }
            }
        }
    }
}
