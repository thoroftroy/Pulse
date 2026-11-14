package dev.wonderdesk.pulse

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.wonderdesk.shared.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Opt-in history: every half hour (batched by Android, so it costs next to nothing) Pulse notes the battery level,
 * temperature, whether it's charging, and how long each app was on screen since last time. Kept on the phone for two
 * weeks. A second, separate opt-in sends a weekly anonymous summary to the Wonderdesk developer.
 */
object Insights {
    data class Sample(val at: Long, val level: Int, val charging: Boolean, val tempC: Float, val apps: Map<String, Long>)

    private fun prefs(c: Context) = c.getSharedPreferences("insights", Context.MODE_PRIVATE)
    private fun file(c: Context) = File(c.filesDir, "insights.jsonl")
    fun collecting(c: Context) = prefs(c).getBoolean("collect", false)
    fun sharing(c: Context) = prefs(c).getBoolean("share", false)

    fun setCollecting(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("collect", on).apply()
        val js = c.getSystemService(JobScheduler::class.java)
        if (on) { js.schedule(JobInfo.Builder(0x1A5, ComponentName(c, InsightsJob::class.java)).setPeriodic(30 * 60_000L).setPersisted(true).build()); Thread { record(c) }.start() }
        else js.cancel(0x1A5)
    }
    fun setSharing(c: Context, on: Boolean) = prefs(c).edit().putBoolean("share", on).apply()

    fun record(c: Context) {
        val b = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / b.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val charging = b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val temp = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
        val last = prefs(c).getLong("last", System.currentTimeMillis() - 30 * 60_000L)
        val now = System.currentTimeMillis()
        // Screen time per app since the last sample: Android reports running totals for the day, so keep the
        // previous totals and record only the difference (a new day starts from zero).
        val dayStart = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val totals = runCatching {
            c.getSystemService(UsageStatsManager::class.java).queryAndAggregateUsageStats(dayStart, now)
                .filterValues { it.totalTimeInForeground > 0 }.mapValues { it.value.totalTimeInForeground }
        }.getOrDefault(emptyMap())
        val prev = runCatching { JSONObject(prefs(c).getString("totals", "{}")!!) }.getOrDefault(JSONObject())
        val sameDay = prefs(c).getLong("totalsDay", 0L) == dayStart
        val apps = totals.mapValues { (k, v) -> if (sameDay) (v - prev.optLong(k, 0L)).coerceAtLeast(0L) else v }.filterValues { it > 0 }
            .toList().sortedByDescending { it.second }.take(15).toMap()
        prefs(c).edit().putString("totals", JSONObject(totals).toString()).putLong("totalsDay", dayStart).apply()
        val o = JSONObject().put("at", now).put("level", level).put("charging", charging).put("temp", temp.toDouble()).put("apps", JSONObject(apps))
        val f = file(c)
        f.appendText(o.toString() + "\n")
        // Keep two weeks.
        if (f.length() > 400_000) { val keep = f.readLines().takeLast(700); f.writeText(keep.joinToString("\n") + "\n") }
        prefs(c).edit().putLong("last", now).apply()
        if (sharing(c) && now - prefs(c).getLong("sent", 0L) > 7 * 86_400_000L) runCatching { send(c) }.onSuccess { prefs(c).edit().putLong("sent", now).apply() }
    }

    fun samples(c: Context): List<Sample> = runCatching {
        file(c).readLines().mapNotNull { l -> runCatching { val o = JSONObject(l); val a = o.optJSONObject("apps") ?: JSONObject()
            Sample(o.getLong("at"), o.getInt("level"), o.getBoolean("charging"), o.optDouble("temp").toFloat(), a.keys().asSequence().associateWith { a.getLong(it) }) }.getOrNull() }
    }.getOrDefault(emptyList())

    /** Average % lost per hour while not charging, over the samples given. */
    fun drainPerHour(s: List<Sample>): Float? {
        var lost = 0f; var hours = 0f
        s.zipWithNext().forEach { (a, b) -> if (!a.charging && !b.charging && b.level <= a.level) { lost += a.level - b.level; hours += (b.at - a.at) / 3_600_000f } }
        return if (hours >= 1f) lost / hours else null
    }

    /** Screen time per app during battery (not charging) periods, the likeliest drain. */
    fun appsOnBattery(s: List<Sample>): List<Pair<String, Long>> =
        s.filter { !it.charging }.flatMap { it.apps.toList() }.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }.toList().sortedByDescending { it.second }

    /** What the weekly anonymous report contains: no names, no file contents, no account details. */
    fun report(c: Context): String {
        val s = samples(c).filter { it.at > System.currentTimeMillis() - 7 * 86_400_000L }
        val d = drainPerHour(s)
        return buildString {
            appendLine("Pulse weekly insights")
            appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE}")
            appendLine("Battery drain: ${d?.let { "%.1f%%/h".format(it) } ?: "not enough data"} · max temp ${s.maxOfOrNull { it.tempC } ?: 0f} °C")
            appendLine("Top apps on battery (min): " + appsOnBattery(s).take(8).joinToString(", ") { "${it.first} ${it.second / 60_000}" })
            appendLine("Wonderdesk apps: " + appsOnBattery(s).filter { it.first.startsWith("dev.wonderdesk.") || it.first.startsWith("com.blissplayer") }.joinToString(", ") { "${it.first.substringAfterLast('.')} ${it.second / 60_000}m" })
        }
    }

    private fun send(c: Context) {
        val conn = java.net.URL("https://ntfy.sh/wonderdesk-crash-062c8350c3af4072").openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"; conn.doOutput = true; conn.connectTimeout = 15_000; conn.readTimeout = 15_000
        conn.setRequestProperty("Title", "Pulse insights")
        conn.outputStream.use { it.write(report(c).toByteArray()) }
        check(conn.responseCode in 200..299); conn.disconnect()
    }
}

class InsightsJob : JobService() {
    override fun onStartJob(p: JobParameters): Boolean { Thread { runCatching { Insights.record(applicationContext) }; jobFinished(p, false) }.start(); return true }
    override fun onStopJob(p: JobParameters) = true
}

@Composable
fun HistoryTab() {
    val context = LocalContext.current
    val p = wonderPalette()
    var collect by remember { mutableStateOf(Insights.collecting(context)) }
    var share by remember { mutableStateOf(Insights.sharing(context)) }
    var samples by remember { mutableStateOf<List<Insights.Sample>>(emptyList()) }
    var showReport by remember { mutableStateOf(false) }
    val usage = remember { SpecialAccess.granted(context, SpecialAccess.Kind.USAGE) }
    LaunchedEffect(collect) { samples = withContext(Dispatchers.IO) { Insights.samples(context) } }
    val recent = samples.filter { it.at > System.currentTimeMillis() - 48 * 3_600_000L }
    val drain = Insights.drainPerHour(samples.filter { it.at > System.currentTimeMillis() - 7 * 86_400_000L })
    LazyColumn(Modifier.fillMaxSize(), state = rememberTabTopList(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Panel(title = "History", footer = "Every half hour Pulse notes the battery level, temperature, charging and which apps were on screen. It stays on this phone for two weeks, and costs almost no battery: Android runs it alongside its own work.") {
                SwitchRow("Keep a battery and app history", collect, { collect = it; Insights.setCollecting(context, it) }, icon = WonderIcons.Insights, tint = Color(0xFF2E9E44))
                PanelDivider()
                SwitchRow("Send a weekly anonymous summary to the Wonderdesk developer", share, { share = it; Insights.setSharing(context, it) }, subtitle = "Helps make the apps lighter and faster. Tap “What's sent” to read it first",
                    icon = WonderIcons.Share, tint = Color(0xFF1E7FE8))
                PanelDivider()
                ListRow("What's sent", icon = WonderIcons.Info, tint = p.secondaryText, chevron = true, onClick = { showReport = true })
            }
        }
        if (!collect) return@LazyColumn
        if (!usage) item { Panel { ListRow("Per-app details need usage access", subtitle = "Turn it on under Pulse › Apps to see which apps drain the battery", icon = WonderIcons.Warning, tint = p.warning) } }
        item {
            Panel(title = "Battery, last 48 hours") {
                Column(Modifier.padding(12.dp)) {
                    if (recent.size < 2) Text("Collecting… check back in an hour.", color = p.secondaryText)
                    else {
                        val start = recent.first().at; val span = (recent.last().at - start).coerceAtLeast(1)
                        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                            val path = Path()
                            recent.forEachIndexed { i, s -> val x = (s.at - start).toFloat() / span * size.width; val y = size.height * (1 - s.level / 100f); if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
                            for (k in 1..3) drawLine(p.separator, Offset(0f, size.height * k / 4), Offset(size.width, size.height * k / 4), 1f)
                            drawPath(path, p.accent, style = Stroke(4f))
                        }
                        Text("${recent.last().level}% now · " + (drain?.let { "loses %.1f%% an hour on battery".format(it) } ?: "not enough unplugged time yet"), style = MaterialTheme.typography.bodyMedium)
                        drain?.takeIf { it > 0.1f && !recent.last().charging }?.let { Text("About %.0f hours left at that rate".format(recent.last().level / it), style = MaterialTheme.typography.bodySmall, color = p.secondaryText) }
                    }
                }
            }
        }
        val apps = Insights.appsOnBattery(samples.filter { it.at > System.currentTimeMillis() - 7 * 86_400_000L }).take(10)
        if (apps.isNotEmpty()) item {
            Panel(title = "On screen while on battery, past week", footer = "Screen time is the biggest battery cost, so these are the likeliest drains.") {
                apps.forEachIndexed { i, (pkg, ms) -> if (i > 0) PanelDivider()
                    val label = remember(pkg) { runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg) }
                    ListRow(label, subtitle = "${ms / 3_600_000}h ${(ms / 60_000) % 60}m", icon = WonderIcons.Apps, tint = Color(0xFF8E44AD)) }
            }
        }
    }
    if (showReport) WonderDialog(onDismissRequest = { showReport = false }, title = { Text("The weekly summary") }, text = {
        Text(Insights.report(context) + "\nNothing else: no names, messages, files, locations or accounts.", style = MaterialTheme.typography.bodySmall)
    }, confirmButton = { TextButton(onClick = { showReport = false }) { Text("Close") } })
}
