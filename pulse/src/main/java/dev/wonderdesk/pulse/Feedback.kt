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
    FeedbackApp("Bliss Player", "com.blissplayer.music.debug", "BlissPlayer"), FeedbackApp("Chronicle", "dev.wonderdesk.chronicle", "Chronicle"), FeedbackApp("Cinema", "dev.wonderdesk.cinema", "Cinema"),
    FeedbackApp("Control Room", "dev.wonderdesk.controlroom", "Control-Room"), FeedbackApp("Echo", "dev.wonderdesk.echo", "Echo"), FeedbackApp("Launcher", "dev.wonderdesk.launcher", "Wonderdesk-Launcher"),
    FeedbackApp("Memories", "dev.wonderdesk.memories", "Memories"), FeedbackApp("Packrat", "dev.wonderdesk.packrat", "Packrat"), FeedbackApp("Pages", "dev.wonderdesk.pages", "Pages"),
    FeedbackApp("Polaris", "dev.wonderdesk.polaris", "Polaris"), FeedbackApp("Pulse", "dev.wonderdesk.pulse", "Pulse"), FeedbackApp("Ripple", "dev.wonderdesk.ripple", "Ripple"),
    FeedbackApp("Safekeeper", "dev.wonderdesk.safekeeper", "Safekeeper"), FeedbackApp("Scribble", "dev.wonderdesk.scribble", "Scribble"), FeedbackApp("Timepiece", "dev.wonderdesk.timepiece", "Timepiece"),
    FeedbackApp("Vault", "dev.wonderdesk.vault", "Vault"), FeedbackApp("Wayfinder", "dev.wonderdesk.wayfinder", "Wayfinder"),
)

private const val OWNER = "thoroftroy"
private const val GENERAL_REPO = "App-Hub"

private fun versionOf(c: Context, pkg: String): String? = runCatching {
    val i = c.packageManager.getPackageInfo(pkg, 0)
    "${i.versionName} (${if (Build.VERSION.SDK_INT >= 28) i.longVersionCode else @Suppress("DEPRECATION") i.versionCode.toLong()})"
}.getOrNull()

/** The report text: what the person wrote, the apps it is about with their versions, and optional device details. Pure so it can be tested. */
fun feedbackBody(kind: String, message: String, apps: List<Pair<String, String?>>, allVersions: List<Pair<String, String>>, device: List<Pair<String, String>>?): String = buildString {
    appendLine("**$kind**"); appendLine()
    appendLine(message.trim().ifBlank { "(no message)" }); appendLine()
    appendLine("### About")
    if (apps.isEmpty()) appendLine("General feedback") else apps.forEach { (n, v) -> appendLine("- $n" + (v?.let { " $it" } ?: " (not installed)")) }
    if (allVersions.isNotEmpty()) { appendLine(); appendLine("### Wonderdesk apps on this phone"); allVersions.forEach { (n, v) -> appendLine("- $n $v") } }
    if (device != null) { appendLine(); appendLine("### Device"); device.forEach { (k, v) -> appendLine("- $k: $v") } }
}.trimEnd()

fun feedbackUrl(repo: String, title: String, body: String): String =
    "https://github.com/$OWNER/$repo/issues/new?title=" + Uri.encode(title.take(120)) + "&body=" + Uri.encode(body.take(6500))

private fun deviceDetails(c: Context): List<Pair<String, String>> {
    val dm = c.resources.displayMetrics
    val st = runCatching { android.os.StatFs(android.os.Environment.getDataDirectory().path).let { "${it.availableBytes / 1_000_000_000} GB free of ${it.totalBytes / 1_000_000_000} GB" } }.getOrDefault("unknown")
    return listOf("Model" to "${Build.MANUFACTURER} ${Build.MODEL}", "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", "Screen" to "${dm.widthPixels}x${dm.heightPixels} at ${dm.densityDpi} dpi",
        "Language" to java.util.Locale.getDefault().toLanguageTag(), "Storage" to st, "Processor" to (Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"))
}

@Composable
fun FeedbackTab() {
    val context = LocalContext.current
    val toast = LocalToast.current
    val p = wonderPalette()
    val installed = remember { FEEDBACK_APPS.filter { versionOf(context, it.pkg) != null } }
    var picked by rememberSaveable { mutableStateOf(setOf<String>()) }
    var kind by rememberSaveable { mutableStateOf("Problem") }
    var message by rememberSaveable { mutableStateOf("") }
    var withDevice by rememberSaveable { mutableStateOf(true) }
    var withVersions by rememberSaveable { mutableStateOf(true) }
    val chosen = FEEDBACK_APPS.filter { it.pkg in picked }
    val body = feedbackBody(kind, message, chosen.map { it.name to versionOf(context, it.pkg) },
        if (withVersions) installed.map { it.name to versionOf(context, it.pkg)!! } else emptyList(), if (withDevice) deviceDetails(context) else null)
    val title = (if (chosen.isEmpty()) "Feedback" else chosen.joinToString(", ") { it.name }.take(60)) + ": " + message.trim().lineSequence().firstOrNull().orEmpty().take(60).ifBlank { kind }
    val repo = if (chosen.size == 1) chosen[0].repo else GENERAL_REPO

    Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Panel(title = "What is it about?", footer = "Pick one app, several, or none for general feedback.") {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("General", picked.isEmpty(), { picked = emptySet() })
                    installed.forEach { a -> Chip(a.name, a.pkg in picked, { picked = if (a.pkg in picked) picked - a.pkg else picked + a.pkg }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Problem", "Idea", "Question").forEach { k -> Chip(k, kind == k, { kind = k }) } }
            }
        }
        Panel(title = "Your message") {
            OutlinedTextField(message, { message = it }, Modifier.fillMaxWidth().padding(12.dp).heightIn(min = 140.dp), placeholder = { Text("What happened, or what would you like?") })
        }
        Panel(title = "What is sent", footer = "Nothing else: no files, contacts or account details. You see it all below and can change it before sending.") {
            SwitchRow("Device details", withDevice, { withDevice = it }, subtitle = "Model, Android version, screen, language, storage", icon = WonderIcons.PhoneAndroid)
            PanelDivider()
            SwitchRow("Wonderdesk app versions", withVersions, { withVersions = it }, subtitle = "Which of the suite apps are installed and their versions", icon = WonderIcons.Apps)
            PanelDivider()
            Text(body, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp), color = p.secondaryText)
        }
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WButton("Send report", {
                if (message.isBlank()) toast("Write something first") else context.launchSafely(Intent(Intent.ACTION_VIEW, Uri.parse(feedbackUrl(repo, title, body)))) { toast("No browser found") }
            }, Modifier.fillMaxWidth(), icon = WonderIcons.OpenInNew, kind = ButtonKind.Primary)
            Text(if (chosen.size == 1) "Opens ${chosen[0].name}'s page on GitHub with the report filled in. Press “Submit new issue” there." else "Opens the Wonderdesk page on GitHub with the report filled in. Press “Submit new issue” there.", style = MaterialTheme.typography.bodySmall, color = p.secondaryText)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WButton("Copy", { (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Feedback", body)); toast("Copied") }, Modifier.weight(1f), icon = WonderIcons.ContentCopy)
                WButton("Share", { context.launchSafely(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body), "Share feedback")) {} }, Modifier.weight(1f), icon = WonderIcons.Share)
            }
        }
    }
}
