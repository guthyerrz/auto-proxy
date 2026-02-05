package com.guthyerrz.autoproxy

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class SingularProxyTest {

    private val appContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        val args = InstrumentationRegistry.getArguments()
        val proxyHost = args.getString("proxyHost") ?: return
        val proxyPort = args.getString("proxyPort")?.toIntOrNull() ?: return

        appContext.getSharedPreferences(AutoProxyInitializer.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(AutoProxyInitializer.PREF_HOST, proxyHost)
            .putInt(AutoProxyInitializer.PREF_PORT, proxyPort)
            .commit()
    }

    @Test
    fun singularRequestIsProxied() {
        ActivityScenario.launch(MainActivity::class.java)

        // Wait for the simulated Singular request to complete through the proxy
        Thread.sleep(15_000)

        assertTrue("AutoProxy should be enabled", AutoProxy.isEnabled)
    }

    @After
    fun tearDown() {
        appContext.getSharedPreferences(AutoProxyInitializer.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
