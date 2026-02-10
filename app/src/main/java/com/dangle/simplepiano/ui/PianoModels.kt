package com.dangle.simplepiano.ui

data class PianoKey(
    val midi: Int,
    val isBlack: Boolean,
    val label: String
)

fun isBlackKey(midi: Int): Boolean {
    // C=0, C#=1, D=2, D#=3, E=4, F=5, F#=6, G=7, G#=8, A=9, A#=10, B=11
    return when (midi % 12) {
        1, 3, 6, 8, 10 -> true
        else -> false
    }
}

fun midiToLabel(midi: Int): String {
    val names = listOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
    val octave = (midi / 12) - 1
    return names[midi % 12] + octave
}

fun build88Keys(): List<PianoKey> {
    val A0 = 21
    val C8 = 108
    return (A0..C8).map { midi ->
        PianoKey(
            midi = midi,
            isBlack = isBlackKey(midi),
            label = midiToLabel(midi)
        )
    }
}
