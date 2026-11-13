package com.hblaze.apexy.missions

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.Embedding
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder

/** On-device MobileNet embedding through MediaPipe; robust to small changes in angle and light. */
internal object ImageSimilarity {
    const val AVAILABLE = true

    @Volatile private var embedder: ImageEmbedder? = null
    private val refCache = mutableMapOf<String, Embedding>()

    private fun embedder(context: Context): ImageEmbedder = embedder ?: synchronized(this) {
        embedder ?: ImageEmbedder.createFromOptions(
            context.applicationContext,
            ImageEmbedder.ImageEmbedderOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("image_embedder.tflite").build())
                .setRunningMode(RunningMode.IMAGE)
                .setL2Normalize(true)
                .setQuantize(false)
                .build(),
        ).also { embedder = it }
    }

    private fun embed(context: Context, bitmap: Bitmap): Embedding {
        val argb = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        return embedder(context).embed(BitmapImageBuilder(argb).build()).embeddingResult().embeddings()[0]
    }

    fun similarity(context: Context, photo: Bitmap, referencePath: String): Double {
        val ref = synchronized(refCache) {
            refCache.getOrPut(referencePath) { embed(context, BitmapFactory.decodeFile(referencePath)) }
        }
        return ImageEmbedder.cosineSimilarity(embed(context, photo), ref)
    }
}
