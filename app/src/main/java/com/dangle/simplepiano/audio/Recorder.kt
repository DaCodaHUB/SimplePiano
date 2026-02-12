package com.dangle.simplepiano.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.dangle.simplepiano.audio.ReleaseFader

enum class EventKind { NOTE, PEDAL }

data class RecordingEvent(val timeMs: Long, val kind: EventKind, val midi: Int = -1, val down: Boolean)

class Recorder {
    private val events = ArrayList<RecordingEvent>()
    private var startTime: Long = 0L
    var isRecording: Boolean = false
        private set

    fun start() {
        events.clear()
        startTime = System.currentTimeMillis()
        isRecording = true
    }

    fun stop() {
        isRecording = false
    }

    fun recordNote(midi: Int, down: Boolean) {
        if (!isRecording) return
        val t = System.currentTimeMillis() - startTime
        events.add(RecordingEvent(t, EventKind.NOTE, midi, down))
    }

    fun recordPedal(down: Boolean) {
        if (!isRecording) return
        val t = System.currentTimeMillis() - startTime
        events.add(RecordingEvent(t, EventKind.PEDAL, -1, down))
    }

    fun clear() = events.clear()

    fun hasEvents(): Boolean = events.isNotEmpty()

    /**
     * Replay recorded events against the given [engine]. Returns a Job so caller can cancel.
     * If [initialSustain] is true, replay starts with pedal engaged.
     */
    fun replay(engine: PianoEngine, scope: CoroutineScope, initialSustain: Boolean = false): Job = scope.launch {
        val snapshot = events.toList()
        if (snapshot.isEmpty()) return@launch

        var lastMs = 0L
        val midiToStream = mutableMapOf<Int, Int>()
        val sustainedOnly = mutableSetOf<Int>()
        var sustainNow = initialSustain
        val fader = ReleaseFader(engine.soundPool(), scope)

        try {
            for (e in snapshot) {
                val wait = e.timeMs - lastMs
                if (wait > 0) delay(wait)

                when (e.kind) {
                    EventKind.NOTE -> {
                        if (e.down) {
                            val stream = engine.noteOn(e.midi)
                            if (stream != 0) midiToStream[e.midi] = stream
                        } else {
                            if (sustainNow) {
                                sustainedOnly.add(e.midi)
                            } else {
                                val stream = midiToStream.remove(e.midi)
                                if (stream != null) fader.fadeOutThenStop(stream, durationMs = 420L, steps = 24)
                            }
                        }
                    }
                    EventKind.PEDAL -> {
                        if (e.down) {
                            sustainNow = true
                        } else {
                            sustainNow = false
                            val toRelease = sustainedOnly.toList()
                            sustainedOnly.clear()
                            for (m in toRelease) {
                                val stream = midiToStream.remove(m)
                                if (stream != null) fader.fadeOutThenStop(stream, durationMs = 420L, steps = 24)
                            }
                        }
                    }
                }

                lastMs = e.timeMs
            }

            // Fade out any remaining held notes (if pedal not held)
            if (!sustainNow) {
                for (stream in midiToStream.values) {
                    fader.fadeOutThenStop(stream, durationMs = 420L, steps = 24)
                }
            }
        } finally {
            // caller can cancel the Job to stop early
        }
    }
}
