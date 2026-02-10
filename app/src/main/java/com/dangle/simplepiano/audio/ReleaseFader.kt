package com.dangle.simplepiano.audio

import android.media.SoundPool
import kotlinx.coroutines.*

class ReleaseFader(
    private val soundPool: SoundPool,
    private val scope: CoroutineScope
) {
    private val jobs = mutableMapOf<Int, Job>() // streamId -> job

    fun cancel(streamId: Int) {
        jobs.remove(streamId)?.cancel()
    }

    fun fadeOutThenStop(streamId: Int, durationMs: Long = 120L, steps: Int = 8) {
        cancel(streamId)

        val job = scope.launch(Dispatchers.Default) {
            try {
                val safeSteps = steps.coerceAtLeast(1)
                val stepDelay = (durationMs / safeSteps).coerceAtLeast(10L)

                for (i in safeSteps downTo 1) {
                    val v = i.toFloat() / safeSteps.toFloat()
                    soundPool.setVolume(streamId, v, v)
                    delay(stepDelay)
                }
                soundPool.stop(streamId)
            } finally {
                jobs.remove(streamId)
            }
        }

        jobs[streamId] = job
    }

    fun releaseAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }
}
