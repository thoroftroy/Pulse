package dev.wonderdesk.pulse

import android.hardware.Sensor
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.wonderdesk.shared.*
import kotlin.math.*

/** What a sensor measures, in plain words. */
fun sensorExplanation(s: Sensor): String = when (s.type) {
    Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_ACCELEROMETER_UNCALIBRATED -> "Feels how the phone is being pushed or tilted, including gravity. It is why the screen rotates and how step counting and shake gestures work."
    Sensor.TYPE_GYROSCOPE, Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "Measures how fast the phone is turning, in radians per second. Games and camera stabilisation use it to follow quick movements."
    Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "Detects magnetic fields, mostly the Earth's, in microtesla. Together with the accelerometer it makes the compass work. Magnets and metal nearby disturb it."
    Sensor.TYPE_LIGHT -> "Measures how bright it is around the phone, in lux. The screen uses it to set automatic brightness."
    Sensor.TYPE_PROXIMITY -> "Tells whether something, usually your face or a pocket, is very close to the screen. It switches the screen off during calls."
    Sensor.TYPE_PRESSURE -> "Measures air pressure in hectopascals. Pressure falls as you go up, so it helps estimate height and weather changes."
    Sensor.TYPE_GRAVITY -> "The part of the accelerometer that is only gravity: which way is down, even while you move."
    Sensor.TYPE_LINEAR_ACCELERATION -> "Movement with gravity taken out: how hard you are really pushing the phone."
    Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> "A combined answer from several sensors for exactly how the phone is oriented in space. Compass and augmented reality apps use it."
    Sensor.TYPE_STEP_COUNTER -> "Counts the steps you take since the phone started. It runs in dedicated low-power hardware, so it barely uses battery."
    Sensor.TYPE_STEP_DETECTOR -> "Fires once for every step you take."
    Sensor.TYPE_AMBIENT_TEMPERATURE -> "The air temperature around the phone, in degrees Celsius."
    Sensor.TYPE_RELATIVE_HUMIDITY -> "How much water vapour is in the air, as a percentage."
    Sensor.TYPE_SIGNIFICANT_MOTION -> "Wakes the phone when you start moving, like walking or driving."
    else -> "A sensor built into this phone. The numbers below are its raw readings; the type is ${s.stringType.substringAfterLast('.')}."
}

private fun luxLabel(lux: Float) = when {
    lux < 1f -> "Pitch dark"; lux < 20f -> "Dim room"; lux < 200f -> "Living room"; lux < 1000f -> "Bright office"; lux < 10000f -> "Overcast daylight"; else -> "Direct sunlight"
}

/** A small interactive demo for the sensor being watched: try the sensor, not just read its numbers. */
@Composable
fun SensorDemo(s: Sensor, values: FloatArray) {
    val p = wonderPalette()
    if (values.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (s.type) {
            Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GRAVITY -> {
                // Spirit level: tilt the phone and the bubble slides to the high side.
                val ax = (-values[0] / 9.81f).coerceIn(-1f, 1f); val ay = (values[1] / 9.81f).coerceIn(-1f, 1f)
                Canvas(Modifier.size(150.dp)) {
                    val c = center; val r = size.minDimension / 2
                    drawCircle(p.accent.copy(alpha = .12f), r, c); drawCircle(p.accent.copy(alpha = .5f), r, c, style = Stroke(3f)); drawCircle(p.accent.copy(alpha = .3f), r * .18f, c, style = Stroke(2f))
                    drawCircle(if (abs(ax) < .04f && abs(ay) < .04f) Color(0xFF2E9E44) else p.accent, r * .16f, Offset(c.x - ax * r * .8f, c.y + ay * r * .8f))
                }
                Text(if (abs(ax) < .04f && abs(ay) < .04f) "Level" else "Tilt: ${"%.1f".format(Math.toDegrees(asin(hypot(ax, ay).coerceIn(0f, 1f)).toDouble()))}°", style = MaterialTheme.typography.titleSmall, color = p.text)
                Text("Lay the phone flat and tilt it: the bubble moves to the high side.", style = MaterialTheme.typography.bodySmall, color = p.secondaryText)
            }
            Sensor.TYPE_LIGHT -> {
                val lux = values[0]
                Text("%.0f lux".format(lux), style = MaterialTheme.typography.headlineMedium, color = p.text)
                Text(luxLabel(lux), style = MaterialTheme.typography.titleSmall, color = p.accent)
                Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(p.accent.copy(alpha = .15f))) {
                    Box(Modifier.fillMaxWidth((ln(1f + lux) / ln(40001f)).coerceIn(.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(p.accent))
                }
                Text("Cover the sensor with your hand, then point it at a window. The bar is logarithmic, like your eyes.", style = MaterialTheme.typography.bodySmall, color = p.secondaryText)
            }
            Sensor.TYPE_PROXIMITY -> {
                val near = values[0] < s.maximumRange.coerceAtMost(5f)
                Box(Modifier.size(110.dp).clip(RoundedCornerShape(55.dp)).background(if (near) Color(0xFFE5483C) else Color(0xFF2E9E44).copy(alpha = .6f)), contentAlignment = Alignment.Center) { Text(if (near) "Near" else "Far", color = Color.White, style = MaterialTheme.typography.titleLarge) }
                Text("Wave your hand over the top of the phone near the earpiece.", style = MaterialTheme.typography.bodySmall, color = p.secondaryText)
            }
            Sensor.TYPE_GYROSCOPE -> {
                var angle by remember { mutableFloatStateOf(0f) }
                var last by remember { mutableLongStateOf(0L) }
                val now = System.nanoTime(); if (last != 0L) angle += values[2] * ((now - last) / 1e9f) else Unit; last = now
                Canvas(Modifier.size(130.dp)) {
                    val c = center; val r = size.minDimension / 2
                    drawCircle(p.accent.copy(alpha = .12f), r, c); drawCircle(p.accent.copy(alpha = .5f), r, c, style = Stroke(3f))
                    drawLine(p.accent, c, Offset(c.x + sin(-angle) * r * .85f, c.y - cos(-angle) * r * .85f), 8f, StrokeCap.Round)
                }
                Text("Spin the phone flat on a table: the arrow turns with it.", style = MaterialTheme.typography.bodySmall, color = p.secondaryText)
            }
            Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                val rot = FloatArray(9); val ori = FloatArray(3)
                android.hardware.SensorManager.getRotationMatrixFromVector(rot, values); android.hardware.SensorManager.getOrientation(rot, ori)
                val az = ori[0]
                Canvas(Modifier.size(150.dp)) {
                    val c = center; val r = size.minDimension / 2
                    drawCircle(p.accent.copy(alpha = .12f), r, c); drawCircle(p.accent.copy(alpha = .5f), r, c, style = Stroke(3f))
                    drawLine(Color(0xFFE5483C), c, Offset(c.x + sin(-az) * r * .85f, c.y - cos(-az) * r * .85f), 8f, StrokeCap.Round)
                    drawLine(p.secondaryText, c, Offset(c.x - sin(-az) * r * .5f, c.y + cos(-az) * r * .5f), 6f, StrokeCap.Round)
                }
                Text("Red points north: ${((Math.toDegrees(az.toDouble()) + 360) % 360).roundToInt()}°", style = MaterialTheme.typography.titleSmall, color = p.text)
                Text("A compass. Hold the phone flat and turn around.", style = MaterialTheme.typography.bodySmall, color = p.secondaryText)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                val b = sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2])
                Text("%.0f µT".format(b), style = MaterialTheme.typography.headlineMedium, color = p.text)
                Text(if (b in 25f..65f) "About the Earth's own field" else if (b > 65f) "Stronger than the Earth: a magnet or metal is close" else "Weaker than usual", style = MaterialTheme.typography.titleSmall, color = p.accent)
                Text("Bring a fridge magnet or something metal near the phone's top edge and watch it jump.", style = MaterialTheme.typography.bodySmall, color = p.secondaryText)
            }
            Sensor.TYPE_PRESSURE -> {
                val alt = android.hardware.SensorManager.getAltitude(android.hardware.SensorManager.PRESSURE_STANDARD_ATMOSPHERE, values[0])
                Text("%.1f hPa".format(values[0]), style = MaterialTheme.typography.headlineMedium, color = p.text)
                Text("about ${alt.roundToInt()} m up (a rough guess, weather changes it)", style = MaterialTheme.typography.titleSmall, color = p.accent)
                Text("Lift the phone from the floor to above your head: the pressure drops a tiny bit.", style = MaterialTheme.typography.bodySmall, color = p.secondaryText)
            }
            Sensor.TYPE_STEP_COUNTER -> {
                val start = remember { values[0] }
                Text("${(values[0] - start).roundToInt()} steps", style = MaterialTheme.typography.headlineMedium, color = p.text)
                Text("since you opened this screen (${values[0].roundToInt()} since the phone started)", style = MaterialTheme.typography.bodySmall, color = p.secondaryText)
                Text("Walk around with the phone in your pocket and come back.", style = MaterialTheme.typography.bodySmall, color = p.secondaryText)
            }
            Sensor.TYPE_AMBIENT_TEMPERATURE -> Text("%.1f °C".format(values[0]), style = MaterialTheme.typography.headlineMedium, color = p.text)
            Sensor.TYPE_RELATIVE_HUMIDITY -> Text("%.0f %% humidity".format(values[0]), style = MaterialTheme.typography.headlineMedium, color = p.text)
        }
    }
}
