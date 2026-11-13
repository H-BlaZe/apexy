package com.hblaze.apexy.sleep

import android.content.Context
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.audio.core.RunningMode
import com.google.mediapipe.tasks.components.containers.AudioData
import com.google.mediapipe.tasks.core.BaseOptions

/** YAMNet through MediaPipe; only the "Snoring" score is used. */
internal class SnoreClassifier(context: Context) {
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

    fun snoreScore(pcm: FloatArray, sampleRate: Int): Float {
        val c = classifier ?: return 0f
        return runCatching {
            val data = AudioData.create(
                AudioData.AudioDataFormat.builder().setNumOfChannels(1).setSampleRate(sampleRate.toFloat()).build(),
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
        const val SUPPORTED = true
    }
}
