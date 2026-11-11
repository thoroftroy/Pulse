package dev.wonderdesk.pulse

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.wonderdesk.shared.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : WonderActivity() {
    @Composable override fun Content() = PulseApp()
}

private const val ABOUT = "Pulse shows what your phone is doing right now and what it's made of: live graphs for processor, memory, battery and network, " +
    "and detailed pages for the device, display, sensors, cameras and media codecs. With usage access it also shows which apps you use most and can close their background processes."

/** Live readings, sampled every second while Pulse is open. */
class Live {
    val cpu = Series(); val mem = Series(); val batteryMa = Series(); val temp = Series(); val down = Series(); val up = Series()
    var tick by mutableIntStateOf(0)
}

@Composable
fun PulseApp() {
    val context = LocalContext.current
    val live = remember { Live() }
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var active by remember { mutableStateOf(true) }
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
    }
    LaunchedEffect(active) {
        while (active) {
            withContext(Dispatchers.IO) {
                Probe.cpuPercent()?.let { live.cpu.add(it) }
                val m = Probe.memory(context); live.mem.add(100f * (m.totalMem - m.availMem) / m.totalMem)
                val b = Probe.battery(context); b.currentMa?.let { live.batteryMa.add(it.toFloat()) }; live.temp.add(b.tempC)
                val (d, u) = Probe.network(); live.down.add(d); live.up.add(u)
            }
            live.tick++
            delay(1000)
        }
    }
    var tab by rememberSaveable { mutableStateOf("live") }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    val toastRef = remember { mutableStateOf<(String) -> Unit>({}) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) runCatching { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(report(context).toByteArray()) } }.onSuccess { toastRef.value("Report saved") }
    }
    WonderApp(appName = "Pulse", about = ABOUT,
        tabs = if (detail != null) emptyList() else listOf(WonderTab("live", "Live", WonderIcons.Speed), WonderTab("device", "Device", WonderIcons.PhoneAndroid), WonderTab("apps", "Apps", WonderIcons.Apps)),
        selectedTab = tab, onTab = { tab = it }, title = detail ?: when (tab) { "device" -> "This device"; "apps" -> "Apps"; else -> "Pulse" },
        onBack = detail?.let { { detail = null } },
        menu = listOf(WonderMenuItem("Save full report", WonderIcons.Save) { exporter.launch("Pulse report ${Build_model()}.txt") },
            WonderMenuItem("Share report", WonderIcons.Share) { context.launchSafely(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report(context)), "Share report")) {} })) {
        toastRef.value = LocalToast.current
        when {
            detail != null -> Detail(detail!!, live)
            tab == "device" -> DeviceTab { detail = it }
            tab == "apps" -> AppsTab()
            else -> LiveTab(live) { detail = it }
        }
    }
}

private fun Build_model() = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"

/* ---------------------------------------------------------- chart ------------------------------------------- */

@Composable
fun Chart(series: List<Pair<Series, Color>>, modifier: Modifier, max: Float? = null, min: Float = 0f) {
    val p = wonderPalette()
    Canvas(modifier) {
        val top = max ?: (series.maxOfOrNull { it.first.max } ?: 1f).coerceAtLeast(min + 1f) * 1.15f
        for (k in 1..3) drawLine(p.separator, Offset(0f, size.height * k / 4), Offset(size.width, size.height * k / 4), 1f)
        series.forEach { (s, color) ->
            val v = s.values.toList(); if (v.size < 2) return@forEach
            val step = size.width / (maxOf(v.size, 30) - 1)
            val start = size.width - step * (v.size - 1)
            val path = Path(); val fill = Path()
            v.forEachIndexed { i, y -> val x = start + i * step; val py = size.height - ((y - min) / (top - min)).coerceIn(0f, 1f) * size.height
                if (i == 0) { path.moveTo(x, py); fill.moveTo(x, size.height); fill.lineTo(x, py) } else { path.lineTo(x, py); fill.lineTo(x, py) } }
            fill.lineTo(size.width, size.height); fill.close()
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = .35f), color.copy(alpha = .02f))))
            drawPath(path, color, style = Stroke(4f))
        }
    }
}

@Composable
private fun LiveCard(title: String, value: String, sub: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, series: List<Pair<Series, Color>>,
                     max: Float? = null, min: Float = 0f, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val p = wonderPalette()
    val shape = RoundedCornerShape(p.panelRadius)
    Column(modifier.clip(shape).background(p.panel).border(1.dp, p.panelBorder, shape).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, tint, 26.dp); Spacer(Modifier.width(8.dp))
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = p.secondaryText)
            if (onClick != null) Icon(WonderIcons.ChevronRight, null, tint = p.secondaryText, modifier = Modifier.size(18.dp))
        }
        Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = p.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Chart(series, Modifier.fillMaxWidth().height(70.dp), max, min)
    }
}

private fun rate(bps: Float) = if (bps < 1024) "${bps.toInt()} B/s" else formatBytes(bps.toLong()) + "/s"

/* ---------------------------------------------------------- live -------------------------------------------- */

@Composable
private fun LiveTab(live: Live, open: (String) -> Unit) {
    val context = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val t = live.tick
    val p = wonderPalette()
    val mem = remember(t) { Probe.memory(context) }
    val bat = remember(t) { Probe.battery(context) }
    val cores = remember(t) { Probe.cores() }
    val storage = remember(t / 10) { Probe.storage(context) }
    Page {
        LiveCard("Processor", live.cpu.values.lastOrNull()?.let { "${it.toInt()}%" } ?: "—", "${cores.size} cores · ${Probe.cpuName()}", WonderIcons.DeveloperBoard, Color(0xFF1E7FE8),
            listOf(live.cpu to Color(0xFF1E7FE8)), max = 100f, onClick = { open("Processor") })
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LiveCard("Memory", "${live.mem.last.toInt()}%", "${formatBytes(mem.totalMem - mem.availMem)} of ${formatBytes(mem.totalMem)}", WonderIcons.Memory, Color(0xFF8E44AD),
                listOf(live.mem to Color(0xFF8E44AD)), max = 100f, modifier = Modifier.weight(1f), onClick = { open("Memory") })
            LiveCard("Battery", "${bat.level}%", bat.currentMa?.let { "${if (it > 0) "+" else ""}$it mA · ${bat.tempC}°C" } ?: "${bat.status} · ${bat.tempC}°C",
                if (bat.status == "Charging") WonderIcons.BatteryChargingFull else WonderIcons.BatteryFull, Color(0xFF2E9E44), listOf(live.temp to Color(0xFFE67E22)), min = 15f,
                modifier = Modifier.weight(1f), onClick = { open("Battery") })
        }
        LiveCard("Network", "↓ ${rate(live.down.last)}", "↑ ${rate(live.up.last)}", WonderIcons.Wifi, Color(0xFF16A085), listOf(live.down to Color(0xFF16A085), live.up to Color(0xFFE5483C)), onClick = { open("Network") })
        Panel(title = "Storage") {
            storage.forEachIndexed { i, (name, total, free) ->
                if (i > 0) PanelDivider(0.dp)
                Column(Modifier.clickable { open("Storage") }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row { Text(name, Modifier.weight(1f)); Text("${formatBytes(total - free)} / ${formatBytes(total)}", color = p.secondaryText) }
                    AquaProgress((total - free).toFloat() / total, Modifier.fillMaxWidth(), if (free < total / 10) p.danger else Color(0xFF7A7F87))
                }
            }
        }
        Panel(title = "Cores") {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                cores.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("CPU ${c.index}", Modifier.width(56.dp), style = MaterialTheme.typography.labelMedium)
                        AquaProgress(if (c.max > 0) (c.cur - c.min).toFloat() / (c.max - c.min).coerceAtLeast(1) else 0f, Modifier.weight(1f))
                        Text(if (!c.online) "off" else if (c.cur > 0) "${c.cur / 1000} MHz" else "—", Modifier.width(84.dp).padding(start = 8.dp), style = MaterialTheme.typography.labelMedium, color = p.secondaryText)
                    }
                }
            }
        }
        Text("Uptime ${Probe.uptime(android.os.SystemClock.elapsedRealtime())}", style = MaterialTheme.typography.bodySmall, color = p.secondaryText)
    }
}

/* ---------------------------------------------------------- device ------------------------------------------ */

private val SECTIONS = listOf(
    Triple("System", WonderIcons.Android, Color(0xFF3DDC84)), Triple("Processor", WonderIcons.DeveloperBoard, Color(0xFF1E7FE8)), Triple("Memory", WonderIcons.Memory, Color(0xFF8E44AD)),
    Triple("Battery", WonderIcons.BatteryFull, Color(0xFF2E9E44)), Triple("Display", WonderIcons.Brightness6, Color(0xFFF2A516)), Triple("Storage", WonderIcons.Storage, Color(0xFF7A7F87)),
    Triple("Network", WonderIcons.Wifi, Color(0xFF16A085)), Triple("Sensors", WonderIcons.Gesture, Color(0xFFE67E22)), Triple("Cameras", WonderIcons.PhotoCamera, Color(0xFF34495E)),
    Triple("Media codecs", WonderIcons.Movie, Color(0xFFE5483C)), Triple("Features", WonderIcons.Verified, Color(0xFF6C5CE7)),
)

@Composable
private fun DeviceTab(open: (String) -> Unit) {
    val p = wonderPalette()
    Page {
        Panel {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(WonderIcons.PhoneAndroid, Color(0xFF1E7FE8), 56.dp); Spacer(Modifier.width(14.dp))
                Column {
                    Text(Build_model(), style = MaterialTheme.typography.titleLarge)
                    Text("Android ${android.os.Build.VERSION.RELEASE} · ${Probe.cpuName()}", style = MaterialTheme.typography.bodySmall, color = p.secondaryText)
                }
            }
        }
        Panel { SECTIONS.forEachIndexed { i, (name, icon, tint) -> if (i > 0) PanelDivider(); ListRow(name, icon = icon, tint = tint, chevron = true, onClick = { open(name) }) } }
    }
}

@Composable
private fun Detail(name: String, live: Live) {
    val context = LocalContext.current
    val p = wonderPalette()
    @Suppress("UNUSED_VARIABLE") val t = live.tick
    val rows: List<Pair<String, String>> = when (name) {
        "System" -> Probe.system()
        "Processor" -> listOf("Chip" to Probe.cpuName(), "Cores" to "${Runtime.getRuntime().availableProcessors()}", "Architecture" to (System.getProperty("os.arch") ?: ""),
            "ABIs" to android.os.Build.SUPPORTED_ABIS.joinToString(", "), "Usage" to (live.cpu.values.lastOrNull()?.let { "${it.toInt()}%" } ?: "Not available")) +
            Probe.cores().map { c -> "Core ${c.index}" to if (!c.online) "Offline" else "${c.cur / 1000} MHz (range ${c.min / 1000}–${c.max / 1000} MHz)${if (c.governor.isNotBlank()) " · ${c.governor}" else ""}" }
        "Memory" -> Probe.memory(context).let { m -> listOf("Total" to formatBytes(m.totalMem), "In use" to formatBytes(m.totalMem - m.availMem), "Available" to formatBytes(m.availMem),
            "Low-memory threshold" to formatBytes(m.threshold), "Low on memory now" to if (m.lowMemory) "Yes" else "No",
            "Pulse itself" to formatBytes(android.os.Debug.getPss() * 1024)) }
        "Battery" -> Probe.battery(context).let { b -> listOfNotNull("Level" to "${b.level}%", "Status" to b.status, "Power source" to b.plugged, "Health" to b.health, "Technology" to b.tech,
            "Temperature" to "${b.tempC} °C", "Voltage" to "${b.voltageMv} mV", b.currentMa?.let { "Current" to "$it mA" }, b.chargeMah?.let { "Charge left" to "$it mAh" },
            b.capacityMah?.let { "Estimated capacity" to "$it mAh" }, b.cycles?.let { "Charge cycles" to "$it" }) }
        "Display" -> Probe.display(context)
        "Storage" -> Probe.storage(context).flatMap { (n, total, free) -> listOf(n to "${formatBytes(total - free)} used of ${formatBytes(total)} · ${formatBytes(free)} free") }
        "Network" -> Probe.connection(context)
        "Cameras" -> Probe.cameras(context).ifEmpty { listOf("Cameras" to "None found") }
        "Media codecs" -> Probe.codecs()
        "Features" -> Probe.features(context).map { it.removePrefix("android.hardware.").removePrefix("android.software.") to "" }
        else -> emptyList()
    }
    if (name == "Sensors") { SensorsDetail(); return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        when (name) {
            "Processor" -> item { LiveCard("Usage", live.cpu.values.lastOrNull()?.let { "${it.toInt()}%" } ?: "—", "Last 90 seconds", WonderIcons.DeveloperBoard, Color(0xFF1E7FE8), listOf(live.cpu to Color(0xFF1E7FE8)), 100f); Spacer(Modifier.height(16.dp)) }
            "Memory" -> item { LiveCard("Memory in use", "${live.mem.last.toInt()}%", "Last 90 seconds", WonderIcons.Memory, Color(0xFF8E44AD), listOf(live.mem to Color(0xFF8E44AD)), 100f); Spacer(Modifier.height(16.dp)) }
            "Battery" -> item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LiveCard("Current", "${live.batteryMa.last.toInt()} mA", "Negative means draining", WonderIcons.Bolt, Color(0xFFF2A516), listOf(live.batteryMa to Color(0xFFF2A516)), min = live.batteryMa.values.minOrNull()?.coerceAtMost(0f) ?: 0f, modifier = Modifier.weight(1f))
                    LiveCard("Temperature", "${live.temp.last} °C", "Battery", WonderIcons.Thermostat, Color(0xFFE67E22), listOf(live.temp to Color(0xFFE67E22)), min = 15f, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
            }
            "Network" -> item { LiveCard("Traffic", "↓ ${rate(live.down.last)}", "↑ ${rate(live.up.last)}", WonderIcons.Wifi, Color(0xFF16A085), listOf(live.down to Color(0xFF16A085), live.up to Color(0xFFE5483C))); Spacer(Modifier.height(16.dp)) }
        }
        itemsIndexed(rows) { i, (k, v) ->
            Row(Modifier.panelItem(i, rows.size).padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(k, Modifier.weight(if (v.isEmpty()) 1f else .42f), style = MaterialTheme.typography.bodyMedium, color = if (v.isEmpty()) p.text else p.secondaryText)
                if (v.isNotEmpty()) Text(v, Modifier.weight(.58f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SensorsDetail() {
    val context = LocalContext.current
    val p = wonderPalette()
    val sensors = remember { Probe.sensors(context) }
    var watching by remember { mutableStateOf<Sensor?>(null) }
    var values by remember { mutableStateOf(floatArrayOf()) }
    val series = remember(watching) { Series(120) }
    DisposableEffect(watching) {
        val sm = context.getSystemService(SensorManager::class.java)
        val l = object : SensorEventListener { override fun onSensorChanged(e: SensorEvent) { values = e.values.clone(); series.add(kotlin.math.sqrt(e.values.take(3).sumOf { (it * it).toDouble() }).toFloat()) }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {} }
        watching?.let { sm.registerListener(l, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose { sm.unregisterListener(l) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        watching?.let { s ->
            item {
                Panel(title = "Live: ${s.name}") {
                    Column(Modifier.padding(14.dp)) {
                        Text(values.joinToString("   ") { "%.3f".format(it) }.ifBlank { "Waiting for a reading…" }, style = MaterialTheme.typography.titleMedium)
                        Chart(listOf(series to p.accent), Modifier.fillMaxWidth().height(90.dp).padding(top = 8.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        item { SectionLabel("${sensors.size} sensors · tap one for live readings", Modifier.padding(start = 6.dp, bottom = 6.dp)) }
        itemsIndexed(sensors) { i, s ->
            ListRow(s.name, Modifier.panelItem(i, sensors.size), subtitle = "${s.vendor} · range ${s.maximumRange} · ${s.power} mA", selected = s == watching, onClick = { watching = if (watching == s) null else s })
        }
    }
}

/* ----------------------------------------------------------- apps ------------------------------------------- */

@Composable
private fun AppsTab() {
    val context = LocalContext.current
    val p = wonderPalette()
    val toast = LocalToast.current
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var access by remember { mutableStateOf(Probe.hasUsageAccess(context)) }
    var range by rememberSaveable { mutableIntStateOf(0) }
    var apps by remember { mutableStateOf<List<Probe.AppUse>?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) { access = Probe.hasUsageAccess(context); tick++ } }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
    }
    LaunchedEffect(access, range, tick) {
        if (!access) return@LaunchedEffect
        val since = when (range) { 0 -> java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(); 1 -> System.currentTimeMillis() - 7 * 86_400_000L; else -> System.currentTimeMillis() - 30 * 86_400_000L }
        apps = withContext(Dispatchers.IO) { Probe.appUsage(context, since) }
    }
    if (!access) {
        var guide by remember { mutableStateOf(false) }
        EmptyState("See how you use your phone", "Pulse needs Android's usage access to show screen time per app and app sizes. It never leaves your phone.",
            icon = WonderIcons.QueryStats, action = { WButton("Turn on usage access", { guide = true }, kind = ButtonKind.Primary) })
        if (guide) AccessGuide(SpecialAccess.Kind.USAGE, "Pulse reads how long each app was used today, this week and this month, and how much space each app takes. It stays on your phone.",
            onDismiss = { guide = false }, onGranted = { access = true; tick++ })
        return
    }
    val list = apps
    Column(Modifier.fillMaxSize()) {
        Segmented(listOf("Today", "7 days", "30 days"), range, Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp)) { range = it }
        if (list == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = p.accent) }; return@Column }
        val total = list.sumOf { it.foregroundMs }
        val top = list.maxOfOrNull { it.foregroundMs } ?: 1
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            item { Text("Screen time ${dur(total)}", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(start = 6.dp, bottom = 12.dp)) }
            itemsIndexed(list, key = { _, a -> a.pkg }) { i, a ->
                var menu by remember { mutableStateOf(false) }
                val icon = remember(a.pkg) { context.suiteArt(a.pkg, 96) }
                Box {
                    Column(Modifier.panelItem(i, list.size).clickable { menu = true }.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            icon?.let { Image(it, null, Modifier.size(36.dp)) }; Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${dur(a.foregroundMs)} · used ${relativeTime(a.lastUsed)}" + (a.sizeBytes?.let { " · ${formatBytes(it)}" } ?: ""), style = MaterialTheme.typography.bodySmall, color = p.secondaryText)
                            }
                        }
                        AquaProgress(a.foregroundMs.toFloat() / top, Modifier.fillMaxWidth().padding(start = 48.dp, top = 6.dp))
                    }
                    DropdownMenu(menu, { menu = false }, Modifier.background(p.panel)) {
                        DropdownMenuItem({ Text("Close background processes") }, { menu = false; Probe.closeBackground(context, a.pkg); toast("${a.label}'s background processes were closed") }, leadingIcon = { Icon(WonderIcons.Close, null) })
                        DropdownMenuItem({ Text("Force stop or uninstall…") }, { menu = false; context.launchSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}"))) {} }, leadingIcon = { Icon(WonderIcons.Info, null) })
                        context.packageManager.getLaunchIntentForPackage(a.pkg)?.let { li -> DropdownMenuItem({ Text("Open") }, { menu = false; context.launchSafely(li) {} }, leadingIcon = { Icon(WonderIcons.OpenInNew, null) }) }
                    }
                }
            }
        }
    }
}

private fun dur(ms: Long): String { val m = ms / 60_000; return if (m >= 60) "${m / 60} h ${m % 60} min" else if (m > 0) "$m min" else "${ms / 1000} s" }

fun report(c: Context): String = buildString {
    appendLine("Pulse report · ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date())}\n")
    fun sec(t: String, rows: List<Pair<String, String>>) { appendLine("== $t =="); rows.forEach { (k, v) -> appendLine("$k: $v") }; appendLine() }
    sec("System", Probe.system()); sec("Display", Probe.display(c)); sec("Network", Probe.connection(c))
    Probe.battery(c).let { b -> sec("Battery", listOf("Level" to "${b.level}%", "Status" to b.status, "Health" to b.health, "Temperature" to "${b.tempC} °C", "Voltage" to "${b.voltageMv} mV")) }
    Probe.memory(c).let { m -> sec("Memory", listOf("Total" to formatBytes(m.totalMem), "Available" to formatBytes(m.availMem))) }
    sec("Storage", Probe.storage(c).map { (n, t, f) -> n to "${formatBytes(t - f)} of ${formatBytes(t)}" })
    sec("Cameras", Probe.cameras(c)); sec("Sensors", Probe.sensors(c).map { it.name to it.vendor })
}
