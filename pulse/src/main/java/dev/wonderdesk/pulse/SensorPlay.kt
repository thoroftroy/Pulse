package dev.wonderdesk.pulse

import android.hardware.Sensor
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.wonderdesk.shared.*
import kotlinx.coroutines.delay
import kotlin.math.*

/*
 * Things to actually do with each sensor: small games and everyday uses, shown under the plain demo.
 * Each one says in a line what real apps use the same sensor for.
 */

@Composable
fun SensorPlay(s: Sensor, values: FloatArray) {
    if (values.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (s.type) {
            Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GRAVITY -> TiltGame(values)
            Sensor.TYPE_LINEAR_ACCELERATION -> ShakeMeter(values)
            Sensor.TYPE_PROXIMITY -> WaveChallenge(values, s.maximumRange)
            Sensor.TYPE_MAGNETIC_FIELD -> MetalDetector(values)
            Sensor.TYPE_GYROSCOPE -> SpinCounter(values)
            Sensor.TYPE_PRESSURE -> StairsTracker(values)
            Sensor.TYPE_LIGHT -> ReadingLight(values)
            else -> {}
        }
    }
}

@Composable
private fun PlayHeader(title: String, use: String) {
    val p = wonderPalette()
    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = p.text)
    Text(use, style = MaterialTheme.typography.bodySmall, color = p.secondaryText)
}

/** Roll the ball into the hole by tilting the phone; every hole moves it somewhere new. */
@Composable
private fun TiltGame(values: FloatArray) {
    val p = wonderPalette()
    val latest by rememberUpdatedState(values)
    var ball by remember { mutableStateOf(Offset(.5f, .5f)) }
    var vel by remember { mutableStateOf(Offset.Zero) }
    var hole by remember { mutableStateOf(Offset(.8f, .2f)) }
    var score by remember { mutableIntStateOf(0) }
    var started by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var best by remember { mutableLongStateOf(0L) }
    var last by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(.05f)
                last = now
                val ax = -latest.getOrElse(0) { 0f } / 9.81f; val ay = latest.getOrElse(1) { 0f } / 9.81f
                var v = Offset((vel.x + ax * dt * 1.6f) * .985f, (vel.y + ay * dt * 1.6f) * .985f)
                var b = ball + v * dt
                // Bounce softly off the walls.
                if (b.x < .05f || b.x > .95f) { v = v.copy(x = -v.x * .5f); b = b.copy(x = b.x.coerceIn(.05f, .95f)) }
                if (b.y < .05f || b.y > .95f) { v = v.copy(y = -v.y * .5f); b = b.copy(y = b.y.coerceIn(.05f, .95f)) }
                ball = b; vel = v
                if ((b - hole).getDistance() < .06f) {
                    score++
                    val t = System.currentTimeMillis() - started
                    if (best == 0L || t < best) best = t
                    started = System.currentTimeMillis()
                    hole = Offset(.1f + kotlin.random.Random.nextFloat() * .8f, .1f + kotlin.random.Random.nextFloat() * .8f)
                    if ((hole - b).getDistance() < .3f) hole = Offset(1f - hole.x, 1f - hole.y)
                }
            }
        }
    }
    PlayHeader("Play: roll the ball into the hole", "Tilt the phone to roll it. Racing games and the screen's auto-rotate use this same sensor.")
    Canvas(Modifier.fillMaxWidth().aspectRatio(1.3f).clip(RoundedCornerShape(16.dp)).background(Brush.verticalGradient(listOf(Color(0xFF2E7D32), Color(0xFF1B5E20))))) {
        val w = size.width; val h = size.height; val r = min(w, h) * .045f
        drawCircle(Color(0xFF0B2E0D), r * 1.35f, Offset(hole.x * w, hole.y * h))
        drawCircle(Color.Black.copy(alpha = .25f), r, Offset(ball.x * w + 4f, ball.y * h + 6f))
        drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFB0BEC5)), Offset(ball.x * w - r * .3f, ball.y * h - r * .3f), r * 1.4f), r, Offset(ball.x * w, ball.y * h))
    }
    Text("Holes: $score" + if (best > 0) "   ·   Fastest: ${"%.1f".format(best / 1000f)} s" else "", style = MaterialTheme.typography.titleSmall, color = p.accent)
}

/** How hard can you shake it? Current force and your best. */
@Composable
private fun ShakeMeter(values: FloatArray) {
    val p = wonderPalette()
    val g = sqrt(values.take(3).sumOf { (it * it).toDouble() }).toFloat() / 9.81f
    var best by remember { mutableFloatStateOf(0f) }
    if (g > best) best = g
    PlayHeader("Play: shake-o-meter", "Shake the phone as hard as you safely can. Step counters and \"shake to undo\" read this movement.")
    Box(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(11.dp)).background(p.accent.copy(alpha = .15f))) {
        Box(Modifier.fillMaxWidth((g / 4f).coerceIn(.01f, 1f)).fillMaxHeight().clip(RoundedCornerShape(11.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF2E9E44), Color(0xFFF5A623), Color(0xFFE5483C)))))
    }
    Text("Now ${"%.1f".format(g)} g   ·   Best ${"%.1f".format(best)} g", style = MaterialTheme.typography.titleSmall, color = p.text)
    Text(when { best < .5f -> "Barely a wiggle"; best < 1.5f -> "A good shake"; best < 3f -> "Like a roller coaster drop"; else -> "Fighter-pilot territory!" }, style = MaterialTheme.typography.bodySmall, color = p.accent)
}

/** Wave over the sensor as many times as you can in ten seconds. */
@Composable
private fun WaveChallenge(values: FloatArray, maxRange: Float) {
    val p = wonderPalette()
    val near = values[0] < maxRange.coerceAtMost(5f)
    var waves by remember { mutableIntStateOf(0) }
    var wasNear by remember { mutableStateOf(near) }
    var endsAt by remember { mutableLongStateOf(0L) }
    var best by remember { mutableIntStateOf(0) }
    var left by remember { mutableIntStateOf(0) }
    if (near && !wasNear) {
        if (endsAt == 0L) { endsAt = System.currentTimeMillis() + 10_000; waves = 0 }
        if (System.currentTimeMillis() < endsAt) waves++
    }
    wasNear = near
    LaunchedEffect(endsAt) {
        if (endsAt == 0L) return@LaunchedEffect
        while (System.currentTimeMillis() < endsAt) { left = ((endsAt - System.currentTimeMillis()) / 1000 + 1).toInt(); delay(200) }
        left = 0; if (waves > best) best = waves; endsAt = 0L
    }
    PlayHeader("Play: wave challenge", "Wave over the top of the screen as fast as you can; the first wave starts a 10 second clock. Phones use this sensor to turn the screen off against your ear.")
    Text(if (endsAt != 0L) "$waves waves · $left s left" else if (best > 0) "Last round: $waves · Best: $best" else "Wave to start", style = MaterialTheme.typography.headlineSmall, color = p.text)
}

/** Beeps faster the closer you get to metal or a magnet, like a real metal detector. */
@Composable
private fun MetalDetector(values: FloatArray) {
    val p = wonderPalette()
    val b = sqrt(values.take(3).sumOf { (it * it).toDouble() }).toFloat()
    var baseline by remember { mutableFloatStateOf(b) }
    var sound by remember { mutableStateOf(false) }
    val extra = (b - baseline).coerceAtLeast(0f)
    val strength = (extra / 150f).coerceIn(0f, 1f)
    val latestStrength by rememberUpdatedState(strength)
    DisposableEffect(sound) {
        val tone = if (sound) runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 60) }.getOrNull() else null
        val stop = java.util.concurrent.atomic.AtomicBoolean(false)
        val t = Thread {
            while (!stop.get() && tone != null) {
                val s = latestStrength
                if (s > .03f) tone.startTone(ToneGenerator.TONE_PROP_BEEP, 40)
                Thread.sleep((900 - s * 820).toLong().coerceAtLeast(70))
            }
        }.also { if (tone != null) it.start() }
        onDispose { stop.set(true); runCatching { tone?.release() } }
    }
    PlayHeader("Use: metal detector", "Sweep the top of the phone over a wall or a desk: hidden screws, pipes and magnets push the reading up. Stud finder apps work exactly like this.")
    Canvas(Modifier.size(140.dp)) {
        val c = center; val r = size.minDimension / 2
        drawCircle(p.accent.copy(alpha = .1f), r, c)
        for (i in 1..3) drawCircle(p.accent.copy(alpha = .15f + strength * .5f), r * (.3f + .23f * i) * (.85f + strength * .15f), c, style = Stroke(4f + strength * 8f))
        drawCircle(lerpColor(Color(0xFF2E9E44), Color(0xFFE5483C), strength), r * (.18f + strength * .2f), c)
    }
    Text(when { strength < .05f -> "Nothing here"; strength < .3f -> "Something metal nearby"; strength < .7f -> "Getting close"; else -> "Right on top of it!" }, style = MaterialTheme.typography.titleSmall, color = p.text)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WButton(if (sound) "Sound off" else "Beep", { sound = !sound }, icon = if (sound) WonderIcons.VolumeOff else WonderIcons.VolumeUp)
        WButton("Reset here", { baseline = b }, icon = WonderIcons.Refresh)
    }
}

private fun lerpColor(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)

/** Counts full turns while spinning the phone flat, and the fastest spin. */
@Composable
private fun SpinCounter(values: FloatArray) {
    val p = wonderPalette()
    var turned by remember { mutableFloatStateOf(0f) }
    var last by remember { mutableLongStateOf(0L) }
    var fastest by remember { mutableFloatStateOf(0f) }
    val now = System.nanoTime()
    if (last != 0L) turned += abs(values[2]) * ((now - last) / 1e9f)
    last = now
    val rps = abs(values[2]) / (2 * PI.toFloat())
    if (rps > fastest) fastest = rps
    PlayHeader("Play: spin counter", "Lay the phone on a table and spin it. Camera stabilisation and VR headsets follow your turns with this sensor.")
    Text("${"%.1f".format(turned / (2 * PI.toFloat()))} turns", style = MaterialTheme.typography.headlineSmall, color = p.text)
    Text("Fastest: ${"%.1f".format(fastest * 60)} turns a minute", style = MaterialTheme.typography.bodySmall, color = p.accent)
    WButton("Start again", { turned = 0f; fastest = 0f }, icon = WonderIcons.Refresh)
}

/** How much higher or lower you are than when you opened it: count stairs with air pressure. */
@Composable
private fun StairsTracker(values: FloatArray) {
    val p = wonderPalette()
    // Smooth the reading: pressure jitters by a few hundredths.
    var smooth by remember { mutableFloatStateOf(values[0]) }
    smooth += (values[0] - smooth) * .1f
    var start by remember { mutableFloatStateOf(values[0]) }
    val metres = (start - smooth) * 8.3f   // about 8.3 m per hPa near sea level
    PlayHeader("Use: stairs and height", "Climb a flight of stairs or ride a lift. Fitness watches count floors climbed this way.")
    Text("${if (metres >= 0) "+" else ""}${"%.1f".format(metres)} m", style = MaterialTheme.typography.headlineSmall, color = p.text)
    Text("about ${(metres / 3f).roundToInt()} floor${if (abs((metres / 3f).roundToInt()) == 1) "" else "s"} ${if (metres >= 0) "up" else "down"} since you started", style = MaterialTheme.typography.bodySmall, color = p.accent)
    WButton("Start from here", { start = smooth }, icon = WonderIcons.Refresh)
}

/** Is there enough light to read comfortably here? */
@Composable
private fun ReadingLight(values: FloatArray) {
    val p = wonderPalette()
    val lux = values[0]
    var brightest by remember { mutableFloatStateOf(lux) }
    if (lux > brightest) brightest = lux
    PlayHeader("Use: is it bright enough to read?", "Hold the phone where your book or desk is. Lighting designers aim for about 300 to 500 lux for reading.")
    val (verdict, col) = when {
        lux < 50 -> "Too dark to read comfortably" to Color(0xFFE5483C)
        lux < 300 -> "Fine for a screen, dim for paper" to Color(0xFFF5A623)
        lux < 1500 -> "Good reading light" to Color(0xFF2E9E44)
        else -> "Very bright: mind the glare" to Color(0xFFF5A623)
    }
    Text(verdict, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = col)
    Text("Brightest spot so far: ${"%.0f".format(brightest)} lux", style = MaterialTheme.typography.bodySmall, color = p.secondaryText)
}
