package com.hblaze.apexy.sleep

import android.content.Context

/** The F-Droid build ships without MediaPipe, so snoring isn't detected. */
internal class SnoreClassifier(@Suppress("UNUSED_PARAMETER") context: Context) {
    val available get() = false

    fun snoreScore(pcm: FloatArray, sampleRate: Int): Float = 0f

    fun close() = Unit

    companion object {
        const val SUPPORTED = false
    }
}
