package com.dangle.simplepiano.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import kotlin.math.pow

class PianoEngine(private val context: Context) {

    private val soundPool: SoundPool
    private val soundIdByMidi = mutableMapOf<Int, Int>()
    private var discoveredSampledMidis: List<Int> = emptyList()
    private val sampledMidis: List<Int>
    internal fun soundPool(): SoundPool = soundPool

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(16)
            .setAudioAttributes(attrs)
            .build()

        val mids = mutableListOf<Int>()
        fun addRange(note: String, startOct: Int, endOct: Int) {
            for (oct in startOct..endOct) mids += midiOf(note, oct)
        }

        addRange("A", 0, 7)
        addRange("C", 1, 8)
        addRange("D#", 1, 7)
        addRange("F#", 1, 7)

        sampledMidis = mids.distinct().sorted()
    }

    fun loadAll(onLoaded: (() -> Unit)? = null) {
        // Discover all files under assets/piano/
        val files = context.assets.list("piano")?.toList().orEmpty()

        // Parse files like A0.ogg, C4.ogg, Ds3.ogg, Fs7.ogg (case-insensitive)
        val midiToFile = mutableMapOf<Int, String>()

        val pattern = Regex("""^([A-Ga-g])(?:([sS#]))?(\d)\.ogg$""")
        for (f in files) {
            val m = pattern.matchEntire(f) ?: continue
            val letter = m.groupValues[1].uppercase()
            val accidental = m.groupValues[2]
            val octave = m.groupValues[3].toInt()

            val note = when {
                accidental.equals("#", ignoreCase = true) -> "$letter#"
                accidental.equals("s", ignoreCase = true) -> "$letter#"
                else -> letter
            }

            val midi = midiOf(note, octave)
            midiToFile[midi] = "piano/$f"
        }

        // Nothing found? fail gracefully.
        if (midiToFile.isEmpty()) {
            onLoaded?.invoke()
            return
        }

        // Update sampled list based on what you actually have
        soundIdByMidi.clear()
        val discoveredMidis = midiToFile.keys.sorted()

        var remaining = discoveredMidis.size
        soundPool.setOnLoadCompleteListener { _, _, _ ->
            remaining -= 1
            if (remaining == 0) onLoaded?.invoke()
        }

        for (midi in discoveredMidis) {
            val file = midiToFile.getValue(midi)
            val afd = context.assets.openFd(file)
            soundIdByMidi[midi] = soundPool.load(afd, 1)
        }

        // IMPORTANT: also replace sampledMidis usage:
        // We'll store it in a new field (see step 2).
        this.discoveredSampledMidis = discoveredMidis
    }


    fun noteOn(targetMidi: Int, velocity: Float = 1f): Int {
        val baseMidi = nearestSampledMidi(targetMidi)
        val soundId = soundIdByMidi[baseMidi] ?: return 0

        val semitoneDiff = targetMidi - baseMidi
        val rate = (2.0).pow(semitoneDiff / 12.0).toFloat().coerceIn(0.5f, 2.0f)

        return soundPool.play(soundId, velocity, velocity, 1, 0, rate)
    }

    fun noteOff(streamId: Int) {
        if (streamId != 0) {
            soundPool.stop(streamId)
        }
    }

    fun release() = soundPool.release()

    private fun nearestSampledMidi(target: Int): Int {
        val list = discoveredSampledMidis
        if (list.isEmpty()) return target

        var best = list.first()
        var bestDist = kotlin.math.abs(best - target)
        for (m in list) {
            val d = kotlin.math.abs(m - target)
            if (d < bestDist) {
                best = m
                bestDist = d
            }
        }
        return best
    }

    private fun fileStem(note: String): String =
        when (note) {
            "D#" -> "Ds"
            "F#" -> "Fs"
            else -> note
        }

    companion object {
        private val NOTE_TO_SEMITONE = mapOf(
            "C" to 0, "C#" to 1, "D" to 2, "D#" to 3, "E" to 4, "F" to 5,
            "F#" to 6, "G" to 7, "G#" to 8, "A" to 9, "A#" to 10, "B" to 11
        )
        private val SEMITONE_TO_NOTE =
            listOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")

        fun midiOf(note: String, octave: Int): Int {
            val s = NOTE_TO_SEMITONE.getValue(note)
            return (octave + 1) * 12 + s
        }

        fun nameAndOctFromMidi(midi: Int): Pair<String, Int> {
            val octave = (midi / 12) - 1
            val note = SEMITONE_TO_NOTE[midi % 12]
            return note to octave
        }
    }
}
