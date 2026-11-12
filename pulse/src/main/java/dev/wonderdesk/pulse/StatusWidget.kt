package dev.wonderdesk.pulse

import android.app.ActivityManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.view.View
import android.widget.RemoteViews
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import dev.wonderdesk.shared.*

/** Battery, memory and storage at a glance. Refreshes every 30 minutes, when the battery changes a lot, and when you tap the title. */
class StatusWidget : AppWidgetProvider() {
    companion object {
        fun refresh(c: Context) { val mgr = AppWidgetManager.getInstance(c); mgr.getAppWidgetIds(ComponentName(c, StatusWidget::class.java)).forEach { runCatching { mgr.updateAppWidget(it, build(c, it)) } } }

        fun build(c: Context, id: Int): RemoteViews = dev.wonderdesk.shared.responsiveWidget(R.layout.widget_status_small, null, R.layout.widget_status) { fill(c, id, it) }
        private fun fill(c: Context, id: Int, layout: Int): RemoteViews {
            val s = WidgetPrefs(c, id)
            val v = RemoteViews(c.packageName, layout)
            val look = runCatching { WidgetLook.valueOf(s.str("look", "GLASS")) }.getOrDefault(WidgetLook.GLASS)
            v.applyLook(look, R.id.w_root, Triple(R.drawable.wd_widget_bg, R.drawable.wd_widget_bg_dark, R.drawable.wd_widget_bg_clear),
                listOf(R.id.w_head, R.id.w_bat_l, R.id.w_mem_l, R.id.w_sto_l), listOf(R.id.w_bat_t, R.id.w_mem_t, R.id.w_sto_t))
            v.setTextViewText(R.id.w_head, s.str("title", "Pulse").ifBlank { "Pulse" })
            v.setViewVisibility(R.id.w_head, if (s.bool("head", true)) View.VISIBLE else View.GONE)
            val b = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val pct = b?.let { it.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / it.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1) } ?: 0
            val charging = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1).let { it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL }
            v.setProgressBar(R.id.w_bat, 100, pct, false); v.setTextViewText(R.id.w_bat_t, "$pct%" + if (charging) " · charging" else "")
            val m = ActivityManager.MemoryInfo().also { c.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
            val usedMem = m.totalMem - m.availMem
            v.setProgressBar(R.id.w_mem, 100, (usedMem * 100 / m.totalMem.coerceAtLeast(1)).toInt(), false); v.setTextViewText(R.id.w_mem_t, "${formatBytes(usedMem)} of ${formatBytes(m.totalMem)}")
            val st = StatFs(Environment.getDataDirectory().path)
            val used = st.totalBytes - st.availableBytes
            v.setProgressBar(R.id.w_sto, 100, (used * 100 / st.totalBytes.coerceAtLeast(1)).toInt(), false); v.setTextViewText(R.id.w_sto_t, "${formatBytes(st.availableBytes)} free")
            listOf("bat" to R.id.w_bat_row, "mem" to R.id.w_mem_row, "sto" to R.id.w_sto_row).forEach { (k, row) -> v.setViewVisibility(row, if (s.bool(k, true)) View.VISIBLE else View.GONE) }
            v.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(c, 41, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE))
            v.setOnClickPendingIntent(R.id.w_head, PendingIntent.getBroadcast(c, 4200 + id % 1000, Intent(c, StatusWidget::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(id)), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            if (layout != R.layout.widget_status) {
                v.setViewVisibility(R.id.w_head, View.GONE)
                // Short values so nothing collides in a 1×1 square.
                v.setTextViewText(R.id.w_bat_t, "$pct%")
                v.setTextViewText(R.id.w_mem_t, "${usedMem * 100 / m.totalMem.coerceAtLeast(1)}%")
                v.setTextViewText(R.id.w_sto_t, "${used * 100 / st.totalBytes.coerceAtLeast(1)}%")
            }
            return v
        }
    }
    override fun onUpdate(c: Context, mgr: AppWidgetManager, ids: IntArray) { ids.forEach { runCatching { mgr.updateAppWidget(it, build(c, it)) } } }
    override fun onDeleted(c: Context, ids: IntArray) { ids.forEach { WidgetPrefs(c, it).clear() } }
}

class StatusWidgetSetup : WidgetSetupActivity() {
    override fun redraw() { AppWidgetManager.getInstance(this).updateAppWidget(widgetId, StatusWidget.build(this, widgetId)) }
    @Composable override fun Content() {
        val c = LocalContext.current
        val s = remember { WidgetPrefs(c, widgetId) }
        var look by remember { mutableStateOf(runCatching { WidgetLook.valueOf(s.str("look", "GLASS")) }.getOrDefault(WidgetLook.GLASS)) }
        var head by remember { mutableStateOf(s.bool("head", true)) }
        var bat by remember { mutableStateOf(s.bool("bat", true)) }
        var mem by remember { mutableStateOf(s.bool("mem", true)) }
        var sto by remember { mutableStateOf(s.bool("sto", true)) }
        WidgetSetup("Pulse", "Status widget", onCancel = { finish() }, onSave = { s.put("look" to look.name, "head" to head, "bat" to bat, "mem" to mem, "sto" to sto); save() }) {
            Panel(title = "Shows", footer = "Tap the widget's title to refresh it right away.") {
                SwitchRow("Title", head, { head = it }); PanelDivider()
                SwitchRow("Battery", bat, { bat = it }, icon = WonderIcons.BatteryFull, tint = androidx.compose.ui.graphics.Color(0xFF2E9E44)); PanelDivider()
                SwitchRow("Memory", mem, { mem = it }, icon = WonderIcons.Memory, tint = androidx.compose.ui.graphics.Color(0xFF8E44AD)); PanelDivider()
                SwitchRow("Storage", sto, { sto = it }, icon = WonderIcons.Storage, tint = androidx.compose.ui.graphics.Color(0xFF1E7FE8))
            }
            WidgetLookPicker(look) { look = it }
        }
    }
}
