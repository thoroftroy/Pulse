package dev.wonderdesk.pulse

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wonderdesk.shared.*

/** An app that feedback can be about, and the project page its reports go to. */
data class FeedbackApp(val name: String, val pkg: String, val repo: String)

val FEEDBACK_APPS = listOf(
    FeedbackApp("Abacus", "dev.wonderdesk.abacus", "Abacus"), FeedbackApp("App Hub", "dev.wonderdesk.apphub", "App-Hub"), FeedbackApp("Beacon", "dev.wonderdesk.beacon", "Beacon"),
    FeedbackApp("Bliss Player", "com.blissplayer.music.debug", "BlissPlayer"), FeedbackApp("Chronicle", "dev.wonderdesk.chronicle", "Chronicle"), FeedbackApp("Control Room", "dev.wonderdesk.controlroom", "Control-Room"), FeedbackApp("Echo", "dev.wonderdesk.echo", "Echo"), FeedbackApp("Launcher", "dev.wonderdesk.launcher", "Wonderdesk-Launcher"),
    FeedbackApp("Memories", "dev.wonderdesk.memories", "Memories"), FeedbackApp("Packrat", "dev.wonderdesk.packrat", "Packrat"), FeedbackApp("Pages", "dev.wonderdesk.pages", "Pages"),
    FeedbackApp("Polaris", "dev.wonderdesk.polaris", "Polaris"), FeedbackApp("Pulse", "dev.wonderdesk.pulse", "Pulse"), FeedbackApp("Ripple", "dev.wonderdesk.ripple", "Ripple"),
    FeedbackApp("Safekeeper", "dev.wonderdesk.safekeeper", "Safekeeper"), FeedbackApp("Scribble", "dev.wonderdesk.scribble", "Scribble"), FeedbackApp("Timepiece", "dev.wonderdesk.timepiece", "Timepiece"),
    FeedbackApp("Vault", "dev.wonderdesk.vault", "Vault"), FeedbackApp("Wayfinder", "dev.wonderdesk.wayfinder", "Wayfinder"),
)


private fun versionOf(c: Context, pkg: String): String? = runCatching {
    val i = c.packageManager.getPackageInfo(pkg, 0)
    "${i.versionName} (${if (Build.VERSION.SDK_INT >= 28) i.longVersionCode else @Suppress("DEPRECATION") i.versionCode.toLong()})"
}.getOrNull()

/** The report text: what the person wrote, the apps it is about with their versions, and optional device details. Pure so it can be tested. */
fun feedbackBody(kind: String, message: String, apps: List<Pair<String, String?>>, allVersions: List<Pair<String, String>>, device: List<Pair<String, String>>?, crashes: List<String> = emptyList()): String = buildString {
    appendLine("**$kind**"); appendLine()
    appendLine(message.trim().ifBlank { "(no message)" }); appendLine()
    appendLine("### About")
    if (apps.isEmpty()) appendLine("General feedback") else apps.forEach { (n, v) -> appendLine("- $n" + (v?.let { " $it" } ?: " (not installed)")) }
    if (allVersions.isNotEmpty()) { appendLine(); appendLine("### Wonderdesk apps on this phone"); allVersions.forEach { (n, v) -> appendLine("- $n $v") } }
    if (device != null) { appendLine(); appendLine("### Device"); device.forEach { (k, v) -> appendLine("- $k: $v") } }
    if (crashes.isNotEmpty()) { appendLine(); appendLine("### Recent crashes"); crashes.forEach { appendLine(it) } }
}.trimEnd()

/** The latest crashes Control Room kept for these apps, shortened and with file paths, links and emails removed. */
fun recentCrashes(c: Context, pkgs: Set<String>): List<String> = runCatching {
    val out = ArrayList<String>()
    c.contentResolver.query(WonderCrash.REPORT_URI, null, null, null, null)?.use { cur ->
        val rows = ArrayList<List<String>>()
        while (cur.moveToNext()) rows += listOf(cur.getString(0), cur.getString(1), cur.getString(2), cur.getString(3), cur.getString(4), cur.getString(5))
        rows.filter { it[0] in pkgs }.sortedByDescending { it[2].toLongOrNull() ?: 0L }.take(4).forEach { r ->
            val name = FEEDBACK_APPS.firstOrNull { it.pkg == r[0] }?.name ?: r[0]
            val top = WonderReport.redact(r[5]).lineSequence().filter { it.isNotBlank() }.take(8).joinToString("\n    ")
            out += "- $name ${r[3]} (${r[1]}): ${WonderReport.redact(r[4]).take(160)}\n    $top"
        }
    }
    out
}.getOrDefault(emptyList())

private fun deviceDetails(c: Context): List<Pair<String, String>> {
    val dm = c.resources.displayMetrics
    val st = runCatching { android.os.StatFs(android.os.Environment.getDataDirectory().path).let { "${it.availableBytes / 1_000_000_000} GB free of ${it.totalBytes / 1_000_000_000} GB" } }.getOrDefault("unknown")
    return listOf("Model" to "${Build.MANUFACTURER} ${Build.MODEL}", "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", "Screen" to "${dm.widthPixels}x${dm.heightPixels} at ${dm.densityDpi} dpi",
        "Language" to java.util.Locale.getDefault().toLanguageTag(), "Storage" to st, "Processor" to (Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"))
}

private fun chosenFirst(all: List<FeedbackApp>, picked: Set<String>) = all.filter { it.pkg in picked } + all.filter { it.pkg !in picked }

@Composable
fun FeedbackTab() {
    val context = LocalContext.current
    val toast = LocalToast.current
    val p = wonderPalette()
    val installed = remember { FEEDBACK_APPS.filter { versionOf(context, it.pkg) != null } }
    var picked by rememberSaveable { mutableStateOf(setOf<String>()) }
    var search by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("Problem") }
    var message by rememberSaveable { mutableStateOf("") }
    var withDevice by rememberSaveable { mutableStateOf(true) }
    var withVersions by rememberSaveable { mutableStateOf(true) }
    var withCrashes by rememberSaveable { mutableStateOf(true) }
    val chosen = FEEDBACK_APPS.filter { it.pkg in picked }
    val body = feedbackBody(kind, message, chosen.map { it.name to versionOf(context, it.pkg) },
        if (withVersions) installed.map { it.name to versionOf(context, it.pkg)!! } else emptyList(), if (withDevice) deviceDetails(context) else null,
        if (withCrashes && chosen.isNotEmpty()) remember(picked) { recentCrashes(context, picked) } else emptyList())
    val title = (if (chosen.isEmpty()) "Feedback" else chosen.joinToString(", ") { it.name }.take(60)) + ": " + message.trim().lineSequence().firstOrNull().orEmpty().take(60).ifBlank { kind }
    var reportId by rememberSaveable { mutableStateOf(WonderReport.newId()) }
    var sending by remember { mutableStateOf(false) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Panel(title = "What is it about?", footer = "Pick one app, several, or none for general feedback.") {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search apps") },
                    leadingIcon = { androidx.compose.material3.Icon(WonderIcons.Search, null) })
                val shown = installed.filter { search.isBlank() || it.name.contains(search.trim(), ignoreCase = true) }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (search.isBlank()) Chip("General", picked.isEmpty(), { picked = emptySet() })
                    // Chosen apps stay in view while searching.
                    (chosenFirst(installed, picked).filter { it in shown || it.pkg in picked }).forEach { a -> Chip(a.name, a.pkg in picked, { picked = if (a.pkg in picked) picked - a.pkg else picked + a.pkg }) }
                }
                if (search.isNotBlank() && shown.isEmpty()) Text("No app called “${search.trim()}”", style = MaterialTheme.typography.bodySmall, color = p.secondaryText)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Problem", "Idea", "Question").forEach { k -> Chip(k, kind == k, { kind = k }) } }
            }
        }
        Panel(title = "Your message") {
            OutlinedTextField(message, { message = it }, Modifier.fillMaxWidth().padding(12.dp).heightIn(min = 140.dp), placeholder = { Text("What happened, or what would you like?") })
        }
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WButton(if (sending) "Sending…" else "Send report", {
                if (message.isBlank()) toast("Write something first") else if (!sending) {
                    sending = true
                    Thread {
                        val ok = WonderReport.post("Feedback: " + title, body + "\n\nReport ID: $reportId", if (chosen.isEmpty()) "speech_balloon" else "memo")
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            sending = false
                            if (ok) { message = ""; search = ""; picked = emptySet(); kind = "Problem"; reportId = WonderReport.newId(); toast("Sent. Thank you!") } else toast("Couldn't send. Check your connection, or use Copy or Share")
                        }
                    }.start()
                }
            }, Modifier.fillMaxWidth(), icon = WonderIcons.Share, kind = ButtonKind.Primary, enabled = !sending)
        }
        Panel(title = "What is sent", footer = "Nothing else: no files, contacts or account details. You see it all below and can change it before sending.") {
            SwitchRow("Device details", withDevice, { withDevice = it }, subtitle = "Model, Android version, screen, language, storage", icon = WonderIcons.PhoneAndroid)
            PanelDivider()
            if (chosen.isNotEmpty()) { SwitchRow("Recent crashes", withCrashes, { withCrashes = it }, subtitle = "Any crash of the apps you picked, with paths and links removed", icon = WonderIcons.Warning); PanelDivider() }
            SwitchRow("Wonderdesk app versions", withVersions, { withVersions = it }, subtitle = "Which of the suite apps are installed and their versions", icon = WonderIcons.Apps)
            PanelDivider()
            Text(body, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp), color = p.secondaryText)
        }
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Goes straight to the Wonderdesk developer. No account needed. “Public text” copies a version without your device details, for posting on GitHub.", style = MaterialTheme.typography.bodySmall, color = p.secondaryText)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WButton("Copy", { (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Feedback", body)); toast("Copied") }, Modifier.weight(1f), icon = WonderIcons.ContentCopy)
                WButton("Public text", { (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Feedback", WonderReport.publicText(reportId, chosen.joinToString { it.name }.ifBlank { "General" }, chosen.joinToString { versionOf(context, it.pkg) ?: "?" }.ifBlank { "-" }, message))); toast("Copied without device details") }, Modifier.weight(1f), icon = WonderIcons.OpenInNew)
                WButton("Share", { context.launchSafely(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body), "Share feedback")) {} }, Modifier.weight(1f), icon = WonderIcons.Share)
            }
        }
    }
}
