package com.dangle.simplepiano

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import kotlinx.coroutines.Job
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dangle.simplepiano.audio.PianoEngine
import com.dangle.simplepiano.audio.Recorder
import com.dangle.simplepiano.ui.PianoKeyboard

@Composable
fun RecordIcon(size: Dp, recording: Boolean, enabled: Boolean = true, onToggle: () -> Unit) {
    val bg = when {
        !enabled -> Color(0xFFF0F0F0)
        recording -> Color(0xFFD92B2B)
        else -> Color(0xFFFFFFFF)
    }
    val innerColor = if (enabled) Color(0xFFD92B2B) else Color(0xFFAAAAAA)

    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .border(2.dp, if (enabled) Color(0xFF444444) else Color.LightGray, CircleShape)
            .clickable(enabled = enabled) { onToggle() },
        contentAlignment = Alignment.Center
    ) {
        // inner circle when not recording to resemble icon
        if (!recording) {
            Box(Modifier.size(size * 0.5f).clip(CircleShape).background(innerColor))
        }
    }
}

@Composable
fun PlayIcon(size: Dp, playing: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = if (enabled) Color.White else Color(0xFFF0F0F0)
    val iconColor = if (enabled) Color.Black else Color(0xFFAAAAAA)

    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .border(2.dp, if (enabled) Color(0xFF444444) else Color.LightGray, CircleShape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.5f)) {
            val w = minOf(this.size.width, this.size.height)
            val h = w
            if (!playing) {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w, h / 2f)
                    lineTo(0f, h)
                    close()
                }
                drawPath(path, iconColor)
            } else {
                // draw pause: two rects
                val barW = w * 0.22f
                val gap = w * 0.15f
                val left = (w - (2 * barW + gap)) / 2f
                drawRoundRect(iconColor, topLeft = Offset(left, 0f), size = androidx.compose.ui.geometry.Size(barW, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f,2f))
                drawRoundRect(iconColor, topLeft = Offset(left + barW + gap, 0f), size = androidx.compose.ui.geometry.Size(barW, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f,2f))
            }
        }
    }
}

@Composable
fun SustainIcon(size: Dp, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(if (on) Color(0xFF0F9D94) else Color(0xFF222222))
            .clickable { onClick() }
            .border(1.dp, Color(0xFF444444), RoundedCornerShape(8.dp)),
        contentAlignment = if (on) Alignment.TopCenter else Alignment.BottomCenter
    ) {
        // simple pedal rectangle: position near top when on, near bottom when off
        Box(
            Modifier
                .padding(vertical = 6.dp)
                .width(size * 0.8f)
                .height(size * 0.45f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFEFF6F6))
        )
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PianoScreen() }
    }
}

@Composable
fun PianoScreen() {
    val context = LocalContext.current
    val engine = remember { PianoEngine(context) }

    var loaded by remember { mutableStateOf(false) }
    var sustain by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        loaded = false
        engine.loadAll { loaded = true }
        onDispose { engine.release() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp)
    ) {
        if (loaded) {
            val scope = rememberCoroutineScope()

            Column(
                modifier = Modifier
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top controls
                val recorder = remember { Recorder() }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Sustain icon button
                    SustainIcon(size = 40.dp, on = sustain, onClick = {
                        sustain = !sustain
                        if (recorder.isRecording) recorder.recordPedal(sustain)
                    })
                }

                // Record / Replay controls
                var recording by remember { mutableStateOf(false) }
                var playing by remember { mutableStateOf(false) }
                var replayJob by remember { mutableStateOf<Job?>(null) }

                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Record icon
                    RecordIcon(size = 48.dp, recording = recording, enabled = !playing, onToggle = {
                        if (!recording) {
                            recorder.start()
                            recording = true
                        } else {
                            recorder.stop()
                            recording = false
                        }
                    })

                    // Play / Pause icon
                    PlayIcon(size = 48.dp, playing = playing, enabled = !recording, onClick = {
                        if (!playing) {
                            if (recorder.hasEvents() && !recording) {
                                playing = true
                                val job = recorder.replay(engine, scope, initialSustain = sustain)
                                replayJob = job
                                job.invokeOnCompletion { playing = false; replayJob = null }
                            }
                        } else {
                            // stop playback
                            replayJob?.cancel()
                            replayJob = null
                            playing = false
                        }
                    })

                    // simple status (icons only)
                }

                Spacer(Modifier.height(20.dp))

                // Piano
                PianoKeyboard(
                    engine = engine,
                    sustain = sustain,
                    modifier = Modifier.fillMaxWidth(),
                    onNoteEvent = { midi, down -> if (recording) recorder.recordNote(midi, down) }
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Loading piano…")
            }
        }
    }
}
