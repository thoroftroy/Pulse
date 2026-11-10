package dev.wonderdesk.pulse

import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaCodecList
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.os.storage.StorageManager
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.File
import kotlin.math.sqrt

/** Rolling history for charts. */
class Series(val capacity: Int = 90) {
    val values = ArrayDeque<Float>()
    fun add(v: Float) { values.addLast(v); while (values.size > capacity) values.removeFirst() }
    val last get() = values.lastOrNull() ?: 0f
    val max get() = values.maxOrNull() ?: 0f
}

data class CpuCore(val index: Int, val cur: Long, val min: Long, val max: Long, val online: Boolean, val governor: String)

object Probe {
    private fun read(path: String): String? = runCatching { File(path).readText().trim() }.getOrNull()

    private var lastStat: LongArray? = null
    /** Whole-device CPU use in percent. /proc/stat when Android allows it, otherwise an estimate from core frequencies. */
    fun cpuPercent(): Float? {
        read("/proc/stat")?.lineSequence()?.firstOrNull()?.split(Regex("\\s+"))?.drop(1)?.mapNotNull { it.toLongOrNull() }?.toLongArray()?.let { now ->
            val prev = lastStat; lastStat = now
            if (prev != null && prev.size == now.size && now.size >= 4) {
                val idle = (now[3] + now.getOrElse(4) { 0 }) - (prev[3] + prev.getOrElse(4) { 0 })
                val total = now.sum() - prev.sum()
                if (total > 0) return (100f * (total - idle) / total).coerceIn(0f, 100f)
            }
            return null
        }
        val cores = cores().filter { it.online && it.max > 0 }
        if (cores.isEmpty()) return null
        return (100f * cores.sumOf { it.cur - it.min }.toFloat() / cores.sumOf { (it.max - it.min).coerceAtLeast(1) }).coerceIn(0f, 100f)
    }

    fun cores(): List<CpuCore> = (0 until Runtime.getRuntime().availableProcessors().coerceAtLeast(1)).map { i ->
        val base = "/sys/devices/system/cpu/cpu$i"
        CpuCore(i, read("$base/cpufreq/scaling_cur_freq")?.toLongOrNull() ?: 0, read("$base/cpufreq/cpuinfo_min_freq")?.toLongOrNull() ?: 0,
            read("$base/cpufreq/cpuinfo_max_freq")?.toLongOrNull() ?: 0, read("$base/online")?.let { it == "1" } ?: true, read("$base/cpufreq/scaling_governor") ?: "")
    }

    fun cpuName(): String {
        if (Build.VERSION.SDK_INT >= 31 && Build.SOC_MODEL != Build.UNKNOWN) return "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}"
        return read("/proc/cpuinfo")?.lineSequence()?.firstOrNull { it.startsWith("Hardware") || it.startsWith("model name") }?.substringAfter(':')?.trim() ?: Build.HARDWARE
    }

    fun memory(c: Context): ActivityManager.MemoryInfo = ActivityManager.MemoryInfo().also { c.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }

    data class Battery(val level: Int, val status: String, val plugged: String, val health: String, val tech: String, val tempC: Float, val voltageMv: Int,
                       val currentMa: Int?, val chargeMah: Int?, val cycles: Int?, val capacityMah: Int?)

    fun battery(c: Context): Battery {
        val i = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val bm = c.getSystemService(BatteryManager::class.java)
        val level = i?.let { it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / it.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1) } ?: -1
        val status = when (i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) { BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"; BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "On battery"; else -> "Unknown" }
        val plugged = when (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) { BatteryManager.BATTERY_PLUGGED_AC -> "Wall charger"; BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"; else -> "Not plugged in" }
        val health = when (i?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) { BatteryManager.BATTERY_HEALTH_GOOD -> "Good"; BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"; BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"; BatteryManager.BATTERY_HEALTH_COLD -> "Cold"; else -> "Unknown" }
        val currentUa = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW).takeIf { it != Int.MIN_VALUE && it != 0 }
        // Some phones report mA, most µA.
        val currentMa = currentUa?.let { if (kotlin.math.abs(it) > 20_000) it / 1000 else it }
        val charge = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER).takeIf { it > 0 }?.div(1000)
        val cycles = if (Build.VERSION.SDK_INT >= 34) i?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)?.takeIf { it >= 0 } else null
        val capacity = if (charge != null && level > 0) charge * 100 / level else null
        return Battery(level, status, plugged, health, i?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown",
            (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f, i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0, currentMa, charge, cycles, capacity)
    }

    private var lastRx = -1L; private var lastTx = -1L; private var lastNetAt = 0L
    /** Bytes per second down and up since the last call. */
    fun network(): Pair<Float, Float> {
        val rx = TrafficStats.getTotalRxBytes(); val tx = TrafficStats.getTotalTxBytes(); val now = SystemClock.elapsedRealtime()
        val out = if (lastRx >= 0 && now > lastNetAt) ((rx - lastRx) * 1000f / (now - lastNetAt)) to ((tx - lastTx) * 1000f / (now - lastNetAt)) else 0f to 0f
        lastRx = rx; lastTx = tx; lastNetAt = now
        return out.first.coerceAtLeast(0f) to out.second.coerceAtLeast(0f)
    }

    fun connection(c: Context): List<Pair<String, String>> {
        val cm = c.getSystemService(ConnectivityManager::class.java)
        val net = cm.activeNetwork ?: return listOf("Status" to "Offline")
        val caps = cm.getNetworkCapabilities(net); val link = cm.getLinkProperties(net)
        val type = when { caps == null -> "Unknown"; caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"; caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"; caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"; else -> "Other" }
        return listOfNotNull(
            "Connection" to type,
            caps?.let { "Speed (estimate)" to "↓ ${it.linkDownstreamBandwidthKbps / 1000} Mbps · ↑ ${it.linkUpstreamBandwidthKbps / 1000} Mbps" },
            caps?.let { "Metered" to if (it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) "No" else "Yes" },
            link?.interfaceName?.let { "Interface" to it },
            link?.linkAddresses?.joinToString("\n") { it.address.hostAddress ?: "" }?.takeIf { it.isNotBlank() }?.let { "IP addresses" to it },
            link?.dnsServers?.joinToString(", ") { it.hostAddress ?: "" }?.takeIf { it.isNotBlank() }?.let { "DNS" to it },
            "Data since boot" to "↓ ${dev.wonderdesk.shared.formatBytes(TrafficStats.getTotalRxBytes())} · ↑ ${dev.wonderdesk.shared.formatBytes(TrafficStats.getTotalTxBytes())}",
        )
    }

    fun storage(c: Context): List<Triple<String, Long, Long>> {
        val sm = c.getSystemService(StorageManager::class.java)
        return sm.storageVolumes.mapNotNull { v -> v.directory?.let { d -> runCatching { StatFs(d.path) }.getOrNull()?.let { Triple(if (v.isPrimary) "Internal storage" else v.getDescription(c), it.totalBytes, it.availableBytes) } } }
    }

    fun display(c: Context): List<Pair<String, String>> {
        val wm = c.getSystemService(WindowManager::class.java)
        val b = wm.maximumWindowMetrics.bounds
        val dm = c.resources.displayMetrics
        val d = c.display
        val inches = sqrt((b.width() / dm.xdpi).let { it * it } + (b.height() / dm.ydpi).let { it * it })
        return listOfNotNull(
            "Resolution" to "${b.width()} × ${b.height()} px",
            "Density" to "${dm.densityDpi} dpi (${when { dm.densityDpi >= DisplayMetrics.DENSITY_XXXHIGH -> "xxxhdpi"; dm.densityDpi >= DisplayMetrics.DENSITY_XXHIGH -> "xxhdpi"; dm.densityDpi >= DisplayMetrics.DENSITY_XHIGH -> "xhdpi"; else -> "hdpi" }})",
            "Size" to "%.1f in".format(inches),
            d?.let { "Refresh rate" to "${it.refreshRate.toInt()} Hz" + (it.supportedModes.map { m -> m.refreshRate.toInt() }.distinct().takeIf { r -> r.size > 1 }?.let { r -> " (supports ${r.joinToString("/")} Hz)" } ?: "") },
            d?.let { "HDR" to (it.hdrCapabilities?.supportedHdrTypes?.takeIf { t -> t.isNotEmpty() }?.joinToString { t -> when (t) { 1 -> "Dolby Vision"; 2 -> "HDR10"; 3 -> "HLG"; 4 -> "HDR10+"; else -> "Type $t" } } ?: "No") },
            "Font scale" to "${c.resources.configuration.fontScale}×",
        )
    }

    fun system(): List<Pair<String, String>> = listOf(
        "Model" to "${Build.MANUFACTURER.replaceFirstChar(Char::uppercase)} ${Build.MODEL}",
        "Device" to "${Build.DEVICE} (${Build.PRODUCT})",
        "Brand" to Build.BRAND,
        "Board" to Build.BOARD,
        "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        "Security patch" to Build.VERSION.SECURITY_PATCH,
        "Build" to Build.DISPLAY,
        "Kernel" to (System.getProperty("os.version") ?: "Unknown"),
        "Java VM" to (System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version")),
        "ABIs" to Build.SUPPORTED_ABIS.joinToString(", "),
        "Bootloader" to Build.BOOTLOADER,
        "Uptime" to uptime(SystemClock.elapsedRealtime()),
    )

    fun uptime(ms: Long): String { val m = ms / 60_000; return if (m >= 1440) "${m / 1440} d ${m / 60 % 24} h" else "${m / 60} h ${m % 60} min" }

    fun sensors(c: Context): List<Sensor> = c.getSystemService(SensorManager::class.java).getSensorList(Sensor.TYPE_ALL).sortedBy { it.type }

    fun cameras(c: Context): List<Pair<String, String>> = runCatching {
        val cm = c.getSystemService(CameraManager::class.java)
        cm.cameraIdList.map { id ->
            val ch = cm.getCameraCharacteristics(id)
            val facing = when (ch.get(CameraCharacteristics.LENS_FACING)) { CameraCharacteristics.LENS_FACING_FRONT -> "Front"; CameraCharacteristics.LENS_FACING_BACK -> "Back"; else -> "External" }
            val size = ch.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
            val mp = size?.let { "%.1f MP".format(it.width * it.height / 1_000_000f) } ?: "?"
            val focal = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.joinToString { "%.1f mm".format(it) } ?: ""
            val aperture = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.joinToString { "f/$it" } ?: ""
            val flash = if (ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) "flash" else "no flash"
            "Camera $id · $facing" to listOf(mp, size?.let { "${it.width}×${it.height}" }, focal, aperture, flash).filter { !it.isNullOrBlank() }.joinToString(" · ")
        }
    }.getOrDefault(emptyList())

    fun codecs(): List<Pair<String, String>> = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.map { ci ->
        (if (ci.isEncoder) "Encoder · " else "Decoder · ") + ci.name to ci.supportedTypes.joinToString(", ") + if (ci.isHardwareAccelerated) " · hardware" else " · software"
    }.sortedBy { it.first }

    fun features(c: Context): List<String> = c.packageManager.systemAvailableFeatures.mapNotNull { it.name }.sorted()

    /* ------------------------------------------------------- apps ------------------------------------------------ */

    data class AppUse(val pkg: String, val label: String, val foregroundMs: Long, val lastUsed: Long, val sizeBytes: Long?, val system: Boolean)

    fun hasUsageAccess(c: Context): Boolean {
        val ops = c.getSystemService(android.app.AppOpsManager::class.java)
        return ops.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName) == android.app.AppOpsManager.MODE_ALLOWED
    }

    fun appUsage(c: Context, sinceMs: Long): List<AppUse> {
        val pm = c.packageManager
        val usm = c.getSystemService(UsageStatsManager::class.java)
        val stats = usm.queryAndAggregateUsageStats(sinceMs, System.currentTimeMillis())
        val ssm = c.getSystemService(StorageStatsManager::class.java)
        return stats.values.filter { it.totalTimeInForeground > 0 }.mapNotNull { s ->
            val ai = runCatching { pm.getApplicationInfo(s.packageName, 0) }.getOrNull() ?: return@mapNotNull null
            val size = runCatching { ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, s.packageName, Process.myUserHandle()).let { it.appBytes + it.dataBytes + it.cacheBytes } }.getOrNull()
            AppUse(s.packageName, ai.loadLabel(pm).toString(), s.totalTimeInForeground, s.lastTimeUsed, size, (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0)
        }.sortedByDescending { it.foregroundMs }
    }

    /** Ends an app's background processes (what Android allows without root). */
    fun closeBackground(c: Context, pkg: String) = c.getSystemService(ActivityManager::class.java).killBackgroundProcesses(pkg)

    fun hasFeature(c: Context, f: String) = c.packageManager.hasSystemFeature(f)
    @Suppress("unused") val featuresOfInterest = listOf(PackageManager.FEATURE_NFC to "NFC", PackageManager.FEATURE_BLUETOOTH_LE to "Bluetooth LE", PackageManager.FEATURE_FINGERPRINT to "Fingerprint",
        PackageManager.FEATURE_WIFI_DIRECT to "Wi-Fi Direct", PackageManager.FEATURE_TELEPHONY to "Phone", PackageManager.FEATURE_CAMERA_FLASH to "Flash", "android.hardware.wifi.aware" to "Wi-Fi Aware",
        PackageManager.FEATURE_USB_HOST to "USB host (OTG)", PackageManager.FEATURE_SENSOR_STEP_COUNTER to "Step counter", PackageManager.FEATURE_VULKAN_HARDWARE_VERSION to "Vulkan")

    fun dataDir() = Environment.getDataDirectory()
}
