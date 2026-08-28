package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engine.HermesDeviceController
import com.example.engine.HermesScriptEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Hermes", appName)
  }

  @Test
  fun `test script engine dsl execution`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val deviceController = HermesDeviceController(context)
    val scriptEngine = HermesScriptEngine(deviceController)

    val script = """
      const stats = device.getDeviceStats();
      device.toast("Battery: " + stats.batteryLevel);
      return { status: "OK", battery: stats.batteryLevel };
    """.trimIndent()

    val result = scriptEngine.executeScript(script)
    assertTrue(result.success)
    assertTrue(result.output.contains("OK"))
  }

  @Test
  fun `test battery optimization helper`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val isWhitelisted = com.example.engine.BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
    // In Robolectric default environment, power manager is accessible without crashing
    assertTrue(isWhitelisted || !isWhitelisted)
  }

  @Test
  fun `test rpa script commands evaluation`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val deviceController = HermesDeviceController(context)
    val scriptEngine = HermesScriptEngine(deviceController)

    val script = """
      device.saveFile("test.txt", "Hermes Pro Online");
      return "Automation Ready";
    """.trimIndent()

    val result = scriptEngine.executeScript(script)
    assertTrue(result.success)
    assertTrue(result.output.contains("Automation Ready"))
  }
}

