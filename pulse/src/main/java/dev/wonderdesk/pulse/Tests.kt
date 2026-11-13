package dev.wonderdesk.pulse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.wonderdesk.shared.*
import kotlinx.coroutines.delay

/** Quick checks for the hardware: screen, touch, vibration, speaker and flashlight, each marked as it passes. */
@Composable
fun TestsTab() {
    val context = LocalContext.current
    val p = wonderPalette()
    var running by remember { mutableStateOf<String?>(null) }
    val passed = remember { mutableStateMapOf<String, Boolean>() }
    var torch by remember { mutableStateOf(false) }
    val toast = LocalToast.current
    fun mark(k: String, ok: Boolean) { passed[k] = ok }
    fun Result(k: String): (@Composable () -> Unit)? = passed[k]?.let { ok -> @Composable { Icon(if (ok) WonderIcons.CheckCircle else WonderIcons.Error, null, tint = if (ok) p.success else p.danger) } }
    LazyColumn(Modifier.fillMaxSize(), state = rememberTabTopList(), contentPadding = PaddingValues(16.dp)) {
        item {
            Text("Check that each part of your phone works, for example after a repair or before buying one second-hand. Tap a test, then say whether it worked.",
                Modifier.padding(4.dp, 0.dp, 4.dp, 12.dp), style = MaterialTheme.typography.bodyMedium, color = p.secondaryText)
        }
        item {
            Panel(title = "Screen") {
                ListRow("Colours and dead pixels", subtitle = "Fills the screen with plain colours. Tap to go to the next; look for dots that stay dark or bright", icon = WonderIcons.Palette, tint = Color(0xFFE5483C),
                    trailing = Result("display"), chevron = passed["display"] == null, onClick = { running = "display" })
                PanelDivider()
                ListRow("Touch", subtitle = "Draw over the whole screen; gaps in the line show spots that don't respond", icon = WonderIcons.TouchApp, tint = Color(0xFF1E7FE8),
                    trailing = Result("touch"), chevron = passed["touch"] == null, onClick = { running = "touch" })
                PanelDivider()
                ListRow("Multi-touch", subtitle = "Put several fingers down; it counts how many it can follow at once", icon = WonderIcons.Gesture, tint = Color(0xFF8E44AD),
                    trailing = Result("multi"), chevron = passed["multi"] == null, onClick = { running = "multi" })
            }
        }
        item {
            Panel(Modifier.padding(top = 16.dp), title = "Sound and feel") {
                ListRow("Vibration", subtitle = "Three short buzzes", icon = WonderIcons.PhoneAndroid, tint = Color(0xFF2E9E44), trailing = Result("vibrate"), onClick = {
                    runCatching {
                        val v = context.getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator ?: context.getSystemService(android.os.Vibrator::class.java)
                        v.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 180, 140, 180, 140, 180), -1))
                    }
                    running = "ask:vibrate:Did it vibrate three times?"
                })
                PanelDivider()
                ListRow("Speaker", subtitle = "Plays a short rising tone at media volume", icon = WonderIcons.VolumeUp, tint = Color(0xFFE39A12), trailing = Result("speaker"), onClick = {
                    Thread { runCatching {
                        val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 90)
                        listOf(android.media.ToneGenerator.TONE_DTMF_1, android.media.ToneGenerator.TONE_DTMF_5, android.media.ToneGenerator.TONE_DTMF_9, android.media.ToneGenerator.TONE_DTMF_D).forEach { tg.startTone(it, 260); Thread.sleep(300) }
                        tg.release()
                    } }.start()
                    running = "ask:speaker:Did you hear four tones?"
                })
                PanelDivider()
                ListRow(if (torch) "Flashlight: on" else "Flashlight", subtitle = "Turns the camera light on and off", icon = WonderIcons.Lightbulb, tint = Color(0xFFF2B33D), trailing = Result("torch"), onClick = {
                    val cm = context.getSystemService(android.hardware.camera2.CameraManager::class.java)
                    val id = runCatching { cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } }.getOrNull()
                    if (id == null) { toast("This phone has no flashlight"); mark("torch", false) }
                    else { torch = !torch; runCatching { cm.setTorchMode(id, torch) }; if (!torch) running = "ask:torch:Did the light come on?" }
                })
            }
        }
        item {
            val done = passed.size; val ok = passed.values.count { it }
            if (done > 0) Text("$ok of $done tests passed", Modifier.fillMaxWidth().padding(top = 16.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall, color = if (ok == done) p.success else p.warning)
        }
    }
    when (val r = running) {
        "display" -> FullScreen(onExit = { running = "ask:display:Did every colour look even, with no stuck dots?" }) { DisplayTest { running = "ask:display:Did every colour look even, with no stuck dots?" } }
        "touch" -> FullScreen(onExit = { running = "ask:touch:Did the line follow your finger everywhere?" }) { TouchTest() }
        "multi" -> FullScreen(onExit = { running = "ask:multi:Did it count every finger?" }) { MultiTouchTest() }
        null -> {}
        else -> if (r.startsWith("ask:")) {
            val (_, key, q) = r.split(":", limit = 3)
            WonderDialog(onDismissRequest = { running = null }, title = { Text(q) }, text = { Text("Your answer is noted on the list, so you can see at a glance what passed.") }, confirmButton = { TextButton(onClick = { mark(key, true); running = null }) { Text("Yes") } },
                dismissButton = { TextButton(onClick = { mark(key, false); running = null }) { Text("No") } })
        }
    }
}

@Composable
private fun FullScreen(onExit: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Dialog(onDismissRequest = onExit, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler { onExit() }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            content()
            Text("Back to finish", Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp), color = Color.Gray, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun DisplayTest(onDone: () -> Unit) {
    val colours = listOf(Color.White, Color.Red, Color(0xFF00FF00), Color.Blue, Color.Black, Color(0xFF808080), Color.Cyan, Color.Magenta, Color.Yellow)
    var i by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().background(colours[i]).clickable { if (i < colours.lastIndex) i++ else onDone() }, contentAlignment = Alignment.Center) {
        var hint by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) { delay(1800); hint = false }
        if (hint) Text("Tap for the next colour (${colours.size} in all)", color = Color.Gray, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun TouchTest() {
    val lines = remember { mutableStateListOf<List<Offset>>() }
    var live by remember { mutableStateOf<List<Offset>>(emptyList()) }
    Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
        awaitEachGesture {
            val d = awaitFirstDown(); live = listOf(d.position)
            do { val e = awaitPointerEvent(); e.changes.firstOrNull()?.let { if (it.pressed) { live = live + it.position; it.consume() } } } while (e.changes.any { it.pressed })
            lines += live; live = emptyList()
        }
    }) {
        val grid = 12
        for (k in 1 until grid) { drawLine(Color(0xFF222831), Offset(size.width * k / grid, 0f), Offset(size.width * k / grid, size.height)); drawLine(Color(0xFF222831), Offset(0f, size.height * k / grid), Offset(size.width, size.height * k / grid)) }
        (lines + listOf(live)).forEach { pts -> if (pts.size > 1) drawPath(Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }, Color(0xFF5AB0FF), style = Stroke(10f, cap = StrokeCap.Round)) }
    }
}

@Composable
private fun MultiTouchTest() {
    var points by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var most by remember { mutableIntStateOf(0) }
    val colours = listOf(Color(0xFFE5483C), Color(0xFF2E9E44), Color(0xFF1E7FE8), Color(0xFFE39A12), Color(0xFF8E44AD), Color(0xFF16A085), Color(0xFFFF7AC8), Color(0xFFF2B33D), Color.White, Color.Cyan)
    Box(Modifier.fillMaxSize().pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown()
            do { val e = awaitPointerEvent(); points = e.changes.filter { it.pressed }.map { it.position }; most = maxOf(most, points.size); e.changes.forEach { it.consume() } } while (e.changes.any { it.pressed })
            points = emptyList()
        }
    }) {
        Canvas(Modifier.fillMaxSize()) { points.forEachIndexed { i, o -> drawCircle(colours[i % colours.size], 70f, o); drawCircle(colours[i % colours.size].copy(alpha = .25f), 120f, o) } }
        Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${points.size}", color = Color.White, style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold))
            Text(if (most > 0) "Most at once: $most" else "Put several fingers on the screen", color = Color.Gray, style = MaterialTheme.typography.titleMedium)
        }
    }
}
