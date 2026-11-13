package com.hblaze.apexy.sleep

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** On-device YAMNet sound classifier; only the "Snoring" score is used. Audio never leaves the phone. */
class SnoreDetector(context: Context) {
    private val classifier = SnoreClassifier(context)

    val available get() = classifier.available

    /** Highest "Snoring" score across the clip (16 kHz mono, -1..1). */
    fun snoreScore(pcm: FloatArray): Float = classifier.snoreScore(pcm, SAMPLE_RATE)

    fun close() = classifier.close()

    companion object {
        const val SAMPLE_RATE = 16_000

        /** False in the F-Droid build, which ships without the model. */
        const val SUPPORTED = SnoreClassifier.SUPPORTED

        fun writeWav(file: File, pcm: ShortArray) {
            file.parentFile?.mkdirs()
            val data = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            pcm.forEach { data.putShort(it) }
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(36 + data.capacity()); put("WAVE".toByteArray())
                put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
                putInt(SAMPLE_RATE); putInt(SAMPLE_RATE * 2); putShort(2); putShort(16)
                put("data".toByteArray()); putInt(data.capacity())
            }
            FileOutputStream(file).use { it.write(header.array()); it.write(data.array()) }
        }
    }
}
