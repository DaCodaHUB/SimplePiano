package com.dangle.simplepiano.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dangle.simplepiano.audio.PianoEngine
import com.dangle.simplepiano.audio.ReleaseFader
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor

@Composable
fun PianoKeyboard(
    engine: PianoEngine,
    sustain: Boolean,
    modifier: Modifier = Modifier,
    whiteKeyWidth: Dp = 64.dp,
    keyHeight: Dp = 220.dp,
    blackKeyWidthRatio: Float = 0.62f,
    blackKeyHeightRatio: Float = 0.62f,
    separatorWidth: Dp = 1.dp,
) {
    val keys = remember { build88Keys() }
    val whiteKeys = remember(keys) { keys.filter { !it.isBlack } } // 52
    val blackKeys = remember(keys) { keys.filter { it.isBlack } }

    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    // prevent stale state inside pointer input
    val sustainNow by rememberUpdatedState(sustain)

    // audio / sustain tracking
    val fader = remember { ReleaseFader(engine.soundPool(), scope) }
    val midiToStream = remember { mutableStateMapOf<Int, Int>() }         // midi -> streamId
    val pressCount = remember { mutableStateMapOf<Int, Int>() }           // midi -> pointer count
    val sustainedOnly = remember { mutableStateMapOf<Int, Boolean>() }    // midi held by sustain only
    val pointerToMidi = remember { mutableStateMapOf<PointerId, Int>() }  // pointer -> midi

    val maxVoices = 24

    val blackH = keyHeight * blackKeyHeightRatio
    val blackW = whiteKeyWidth * blackKeyWidthRatio

    fun stopNow(streamId: Int) {
        fader.cancel(streamId)
        engine.noteOff(streamId)
    }

    fun fadeRelease(streamId: Int) {
        fader.fadeOutThenStop(streamId, durationMs = 420L, steps = 24)
    }

    fun ensurePlaying(midi: Int) {
        midiToStream[midi]?.let { existing ->
            stopNow(existing)
            midiToStream.remove(midi)
        }

        if (midiToStream.size >= maxVoices) {
            val victim = midiToStream.entries.first()
            stopNow(victim.value)
            midiToStream.remove(victim.key)
            pressCount.remove(victim.key)
            sustainedOnly.remove(victim.key)
        }

        val stream = engine.noteOn(midi)
        if (stream != 0) {
            engine.soundPool().setVolume(stream, 1f, 1f)
            midiToStream[midi] = stream
        }
    }

    fun press(midi: Int) {
        val next = (pressCount[midi] ?: 0) + 1
        pressCount[midi] = next
        sustainedOnly.remove(midi)
        if (next == 1) ensurePlaying(midi)
    }

    fun release(midi: Int) {
        val cur = pressCount[midi] ?: 0
        if (cur <= 1) pressCount.remove(midi) else pressCount[midi] = cur - 1

        if ((pressCount[midi] ?: 0) > 0) return

        if (sustainNow) {
            sustainedOnly[midi] = true
            return
        }

        midiToStream.remove(midi)?.let { stream -> fadeRelease(stream) }
    }

    // pedal up: release sustained-only notes
    LaunchedEffect(sustain) {
        if (!sustain) {
            val toRelease = sustainedOnly.keys.toList()
            sustainedOnly.clear()
            for (midi in toRelease) {
                if ((pressCount[midi] ?: 0) == 0) {
                    midiToStream.remove(midi)?.let { stream -> fadeRelease(stream) }
                }
            }
        }
    }

    data class BlackRect(val midi: Int, val left: Float, val right: Float)

    fun buildBlackRects(whiteWpx: Float, sepPx: Float, blackWpx: Float): List<BlackRect> {
        val stride = whiteWpx + sepPx
        val rects = ArrayList<BlackRect>(blackKeys.size)

        for (bk in blackKeys) {
            // black sits between white (bk-1) and next white
            val leftWhiteIndex = whiteKeys.indexOfFirst { it.midi == bk.midi - 1 }
            if (leftWhiteIndex == -1) continue

            // center at the MIDDLE of the separator between the two whites
            val separatorCenterX = leftWhiteIndex * stride + whiteWpx + (sepPx / 2f)
            val left = separatorCenterX - blackWpx / 2f
            val right = separatorCenterX + blackWpx / 2f
            rects += BlackRect(bk.midi, left, right)
        }
        return rects
    }

    fun hitTest(
        pos: Offset,
        scrollX: Float,
        whiteWpx: Float,
        whiteHpx: Float,
        sepPx: Float
    ): Int {
        val x = pos.x + scrollX
        val y = pos.y

        val stride = whiteWpx + sepPx
        val blackHpx = whiteHpx * blackKeyHeightRatio
        val blackWpx = whiteWpx * blackKeyWidthRatio

        // 1) black keys first (real rects)
        if (y <= blackHpx) {
            val rects = buildBlackRects(whiteWpx, sepPx, blackWpx)
            rects.firstOrNull { x in it.left..it.right }?.let { return it.midi }
        }

        // 2) white keys, separator-aware
        val baseIndex = floor(x / stride).toInt().coerceIn(0, whiteKeys.size - 1)
        val within = x - baseIndex * stride

        // If the touch is in the separator region, pick the nearer white
        val chosenIndex = if (within <= whiteWpx) {
            baseIndex
        } else {
            // within is in separator: choose closest side
            val distToLeft = abs(within - whiteWpx)
            val distToRight = abs(within - (whiteWpx + sepPx))
            val preferRight = distToRight < distToLeft
            (if (preferRight) baseIndex + 1 else baseIndex).coerceIn(0, whiteKeys.size - 1)
        }

        return whiteKeys[chosenIndex].midi
    }

    fun cLabel(midi: Int): String? {
        if (midi % 12 != 0) return null
        val octave = (midi / 12) - 1
        return "C$octave"
    }

    // IMPORTANT: content width must include separators too
    val contentWidth = (whiteKeyWidth + separatorWidth) * whiteKeys.size

    Column(modifier) {
        // Free slider (pixel scroll)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Low")
            Slider(
                modifier = Modifier.weight(1f),
                value = scroll.value.toFloat(),
                onValueChange = { v -> scope.launch { scroll.scrollTo(v.toInt()) } },
                valueRange = 0f..(scroll.maxValue.toFloat().coerceAtLeast(1f))
            )
            Text("High")
        }

        Spacer(Modifier.height(8.dp))

        // OUTER: touch layer (not scrolled)
        Box(
            Modifier
                .fillMaxWidth()
                .height(keyHeight)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val whiteWpx = whiteKeyWidth.toPx()
                            val whiteHpx = keyHeight.toPx()
                            val sepPx = separatorWidth.toPx()
                            val scrollX = scroll.value.toFloat()

                            for (change in event.changes) {
                                val id = change.id
                                val midi = hitTest(
                                    pos = change.position,
                                    scrollX = scrollX,
                                    whiteWpx = whiteWpx,
                                    whiteHpx = whiteHpx,
                                    sepPx = sepPx
                                )

                                if (change.pressed) {
                                    val prev = pointerToMidi[id]
                                    if (prev == null) {
                                        pointerToMidi[id] = midi
                                        press(midi)
                                    } else if (prev != midi) {
                                        release(prev)
                                        pointerToMidi[id] = midi
                                        press(midi)
                                    }
                                    change.consume()
                                } else {
                                    val prev = pointerToMidi.remove(id)
                                    if (prev != null) release(prev)
                                    change.consume()
                                }
                            }
                        }
                    }
                }
        ) {
            // INNER: drawing layer (scrolled)
            Box(
                Modifier
                    .width(contentWidth)
                    .fillMaxHeight()
                    .horizontalScroll(scroll, enabled = false)
            ) {
                // White keys + separators
                Row(Modifier.fillMaxSize()) {
                    for (wk in whiteKeys) {
                        val down = (pressCount[wk.midi] ?: 0) > 0
                        Box(
                            Modifier
                                .width(whiteKeyWidth)
                                .fillMaxHeight()
                                .background(if (down) Color(0xFFE8E8E8) else Color.White)
                        )
                        Box(
                            Modifier
                                .width(separatorWidth)
                                .fillMaxHeight()
                                .background(Color(0xFFCCCCCC))
                        )
                    }
                }

                // Black keys overlay (separator-aware positioning)
                val stride = whiteKeyWidth + separatorWidth

                for (bk in blackKeys) {
                    val leftWhiteIndex = whiteKeys.indexOfFirst { it.midi == bk.midi - 1 }
                    if (leftWhiteIndex == -1) continue

                    val down = (pressCount[bk.midi] ?: 0) > 0

                    Box(
                        Modifier
                            .offset(
                                x = stride * leftWhiteIndex +
                                        whiteKeyWidth +
                                        (separatorWidth / 2f) -
                                        (blackW / 2f)
                            )
                            .width(blackW)
                            .height(blackH)
                            .background(if (down) Color(0xFF444444) else Color.Black)
                    )
                }

                // Labels overlay (C only, with octave)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .align(Alignment.BottomStart)
                ) {
                    for (wk in whiteKeys) {
                        Box(
                            Modifier
                                .width(whiteKeyWidth)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            cLabel(wk.midi)?.let { Text(it, color = Color(0xFF444444)) }
                        }
                        Spacer(Modifier.width(separatorWidth))
                    }
                }
            }
        }
    }
}
