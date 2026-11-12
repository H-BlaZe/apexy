package com.apexy.alarm.sleep

import android.content.Context
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.audio.core.RunningMode
import com.google.mediapipe.tasks.components.containers.AudioData
import com.google.mediapipe.tasks.core.BaseOptions
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** On-device YAMNet sound classifier; only the "Snoring" score is used. Audio never leaves the phone. */
class SnoreDetector(context: Context) {
    private val classifier: AudioClassifier? = runCatching {
        AudioClassifier.createFromOptions(
            context.applicationContext,
            AudioClassifier.AudioClassifierOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("yamnet.tflite").build())
                .setRunningMode(RunningMode.AUDIO_CLIPS)
                .setMaxResults(8)
                .build(),
        )
    }.getOrNull()

    val available get() = classifier != null

    /** Highest "Snoring" score across the clip (16 kHz mono, -1..1). */
    fun snoreScore(pcm: FloatArray): Float {
        val c = classifier ?: return 0f
        return runCatching {
            val data = AudioData.create(
                AudioData.AudioDataFormat.builder().setNumOfChannels(1).setSampleRate(SAMPLE_RATE.toFloat()).build(),
                pcm.size,
            )
            data.load(pcm)
            c.classify(data).classificationResults().maxOfOrNull { result ->
                result.classifications().flatMap { it.categories() }
                    .filter { it.categoryName().equals("Snoring", ignoreCase = true) }
                    .maxOfOrNull { it.score() } ?: 0f
            } ?: 0f
        }.getOrDefault(0f)
    }

    fun close() = classifier?.close()

    companion object {
        const val SAMPLE_RATE = 16_000

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
