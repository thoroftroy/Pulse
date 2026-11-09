package dev.wonderdesk.shared

import android.os.BatteryManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BatteryStatusTest {
    @Test fun labelsReportUnavailableAndActualStates() {
        assertEquals("Charging", batteryStatusLabel(BatteryManager.BATTERY_STATUS_CHARGING))
        assertEquals("Full", batteryStatusLabel(BatteryManager.BATTERY_STATUS_FULL))
        assertEquals("Unavailable", batteryStatusLabel(-1))
        assertEquals("Not plugged in", batteryPowerSourceLabel(0))
        assertEquals("Unavailable", batteryPowerSourceLabel(-1))
        assertEquals("USB", batteryPowerSourceLabel(BatteryManager.BATTERY_PLUGGED_USB))
        assertEquals("AC / USB", batteryPowerSourceLabel(BatteryManager.BATTERY_PLUGGED_AC or BatteryManager.BATTERY_PLUGGED_USB))
    }
}
