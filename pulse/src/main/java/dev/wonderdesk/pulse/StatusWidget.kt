package dev.wonderdesk.pulse

import android.app.ActivityManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.widget.RemoteViews
import dev.wonderdesk.shared.formatBytes

/** Battery, memory and storage at a glance. Refreshes every 30 minutes and when tapped. */
class StatusWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, mgr: AppWidgetManager, ids: IntArray) {
        val v = RemoteViews(c.packageName, R.layout.widget_status)
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
        v.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(c, 41, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE))
        v.setOnClickPendingIntent(R.id.w_head, PendingIntent.getBroadcast(c, 42, Intent(c, StatusWidget::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        ids.forEach { mgr.updateAppWidget(it, v) }
    }
}
