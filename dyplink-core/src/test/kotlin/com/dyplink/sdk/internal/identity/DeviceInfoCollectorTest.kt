package com.dyplink.sdk.internal.identity

import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowTelephonyManager
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
class DeviceInfoCollectorTest {

    private lateinit var context: Context
    private lateinit var collector: DeviceInfoCollector

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        collector = DeviceInfoCollector(context)
    }

    @Test
    fun `collect returns all expected keys`() {
        val info = collector.collect()

        val expectedKeys = listOf(
            "os", "osVersion", "manufacturer", "brand", "model",
            "screenWidth", "screenHeight", "carrier",
            "locale", "language", "timezone",
        )
        for (key in expectedKeys) {
            assertTrue("Missing key: $key", info.containsKey(key))
        }
    }

    @Test
    fun `collect returns correct static values`() {
        val info = collector.collect()

        assertEquals("Android", info["os"])
        assertEquals(Build.VERSION.RELEASE, info["osVersion"])
        assertEquals(Build.MANUFACTURER, info["manufacturer"])
        assertEquals(Build.BRAND, info["brand"])
        assertEquals(Build.MODEL, info["model"])
    }

    @Test
    fun `collect returns valid screen dimensions`() {
        val info = collector.collect()
        val width = info["screenWidth"] as Int
        val height = info["screenHeight"] as Int

        assertTrue("screenWidth should be > 0", width > 0)
        assertTrue("screenHeight should be > 0", height > 0)
    }

    @Test
    fun `collect returns current locale and language`() {
        val info = collector.collect()

        assertEquals(Locale.getDefault().toString(), info["locale"])
        assertEquals(Locale.getDefault().language, info["language"])
    }

    @Test
    fun `collect returns current timezone`() {
        val info = collector.collect()

        assertEquals(TimeZone.getDefault().id, info["timezone"])
    }

    @Test
    fun `collect returns carrier name from TelephonyManager`() {
        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val shadow: ShadowTelephonyManager = shadowOf(telephonyManager)
        shadow.setNetworkOperatorName("TestCarrier")

        val info = collector.collect()

        assertEquals("TestCarrier", info["carrier"])
    }

    @Test
    fun `carrier falls back to empty string when TelephonyManager unavailable`() {
        // In a default Robolectric context, the carrier name is typically
        // empty or the system service returns a default. We just verify it
        // does not crash and returns a string.
        val info = collector.collect()
        assertNotNull(info["carrier"])
        assertTrue(info["carrier"] is String)
    }
}
