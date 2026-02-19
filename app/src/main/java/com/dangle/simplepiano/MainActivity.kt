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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.dangle.simplepiano.audio.PianoEngine
import com.dangle.simplepiano.audio.Recorder
import com.dangle.simplepiano.ui.PianoKeyboard
import kotlinx.coroutines.launch

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
            .background(Color.Black)
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

                // Record / Replay controls state
                var recording by remember { mutableStateOf(false) }
                var playing by remember { mutableStateOf(false) }
                var replayJob by remember { mutableStateOf<Job?>(null) }

                // Row with sustain, mini scrollbar, record, play
                val keys = remember { com.dangle.simplepiano.ui.build88Keys() }
                val whiteKeys = remember(keys) { keys.filter { !it.isBlack } }
                val blackKeys = remember(keys) { keys.filter { it.isBlack } }

                val mainScroll = rememberScrollState()
                val density = LocalDensity.current
                var mainViewportWidthPx by remember { mutableStateOf(0) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF181C21))
                        .border(2.dp, Color(0xFF3B4048), RoundedCornerShape(24.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFF2E3238),
                                            Color(0xFF1A1E24),
                                            Color(0xFF111419)
                                        )
                                    )
                                )
                                .drawBehind {
                                    // soft top highlight
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.16f),
                                                Color.Transparent
                                            )
                                        ),
                                        topLeft = Offset(0f, 0f),
                                        size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.26f)
                                    )
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SustainIcon(size = 40.dp, on = sustain, onClick = {
                                    sustain = !sustain
                                    if (recorder.isRecording) recorder.recordPedal(sustain)
                                })

                                // mini piano strip (shorter)
                                val miniWhiteKeyWidth = 10.dp
                                val miniKeyHeight = 40.dp
                                val miniContentWidthDp = (miniWhiteKeyWidth + 1.dp) * whiteKeys.size

                                Box(Modifier.weight(1f).height(miniKeyHeight).padding(horizontal = 8.dp).onSizeChanged { /* measured below */ }) {
                                    // measure available mini box width and scale the mini keys to fit
                                    var miniBoxWidthPxLocal by remember { mutableStateOf(0) }
                                    Box(Modifier.matchParentSize().onSizeChanged { miniBoxWidthPxLocal = it.width })

                                    val miniTotalPx = with(density) { miniContentWidthDp.toPx() }
                                    val availablePx = miniBoxWidthPxLocal.toFloat().coerceAtLeast(1f)
                                    val scaledWhiteWPx = (with(density) { miniWhiteKeyWidth.toPx() } * (availablePx / miniTotalPx)).toInt()
                                    val scaledSepPx = (with(density) { 1.dp.toPx() } * (availablePx / miniTotalPx)).toInt()
                                    val stridePx = scaledWhiteWPx + scaledSepPx
                                    val miniRowWidthPx = stridePx * whiteKeys.size

                                    Row(Modifier.width(with(density) { miniRowWidthPx.toDp() }).fillMaxHeight()) {
                                        for (wk in whiteKeys) {
                                            Box(Modifier.width(with(density) { scaledWhiteWPx.toDp() }).fillMaxHeight().background(Color(0xFFF2F2F2)))
                                            Box(Modifier.width(with(density) { scaledSepPx.toDp() }).fillMaxHeight().background(Color(0xFFBBBBBB)))
                                        }
                                    }

                                    val miniBlackWPx = (scaledWhiteWPx * 0.62f).toInt()
                                    for (bk in blackKeys) {
                                        val leftWhiteIndex = whiteKeys.indexOfFirst { it.midi == bk.midi - 1 }
                                        if (leftWhiteIndex == -1) continue
                                        val offsetPx = stridePx * leftWhiteIndex + scaledWhiteWPx + (scaledSepPx / 2f) - (miniBlackWPx / 2f)
                                        Box(Modifier.offset { IntOffset(offsetPx.toInt(), 0) }.width(with(density) { miniBlackWPx.toDp() }).height(miniKeyHeight * 0.62f).background(Color(0xFF003233)))
                                    }

                                    // highlight overlay and drag (use actual mini row width mapping)
                                    val mainWhiteKeyWidth = 64.dp
                                    val mainSeparatorWidth = 1.dp
                                    val contentWidthPx = with(density) { (mainWhiteKeyWidth + mainSeparatorWidth).toPx() * whiteKeys.size }
                                    val miniContentWidthPx = miniRowWidthPx.toFloat()
                                    val vpMainW = mainViewportWidthPx.toFloat()
                                    val visibleMiniWidthPxRaw = if (contentWidthPx <= 0f) 0f else (vpMainW / contentWidthPx) * miniContentWidthPx
                                    val visibleMiniWidthPx = visibleMiniWidthPxRaw
                                    val maxMain = (contentWidthPx - vpMainW).coerceAtLeast(0f)
                                    val maxMini = (miniContentWidthPx - visibleMiniWidthPx).coerceAtLeast(0f)
                                    val highlightOffsetPx = if (maxMain <= 0f) 0f else (mainScroll.value.toFloat() / maxMain) * maxMini

                                    Box(Modifier.offset { IntOffset(highlightOffsetPx.toInt(), 0) }.width(with(density) { visibleMiniWidthPx.toDp() }).fillMaxHeight().background(Color(0x55208583)).pointerInput(Unit) {
                                        var dragTotal = 0f
                                        var initialMain = 0
                                        detectDragGestures(onDragStart = { _ -> dragTotal = 0f; initialMain = mainScroll.value }, onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragTotal += dragAmount.x
                                            if (maxMini <= 0f || maxMain <= 0f) return@detectDragGestures
                                            val ratio = (dragTotal / maxMini)
                                            val target = (initialMain + ratio * maxMain).coerceIn(0f, maxMain)
                                            scope.launch { mainScroll.scrollTo(target.toInt()) }
                                        })
                                    })
                                }

                                Spacer(Modifier.width(12.dp))

                                RecordIcon(size = miniKeyHeight, recording = recording, enabled = !playing, onToggle = {
                                    if (!recording) {
                                        recorder.start()
                                        recording = true
                                    } else {
                                        recorder.stop()
                                        recording = false
                                    }
                                })

                                PlayIcon(size = miniKeyHeight, playing = playing, enabled = !recording, onClick = {
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
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Piano
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF060707))
                                .padding(bottom = 10.dp)
                        ) {
                            PianoKeyboard(
                                engine = engine,
                                sustain = sustain,
                                modifier = Modifier.fillMaxWidth(),
                                externalScroll = mainScroll,
                                onViewportWidthChanged = { mainViewportWidthPx = it },
                                onNoteEvent = { midi, down -> if (recording) recorder.recordNote(midi, down) }
                            )
                        }
                    }
                }
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
