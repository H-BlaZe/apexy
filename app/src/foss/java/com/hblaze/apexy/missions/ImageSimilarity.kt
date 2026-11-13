package com.hblaze.apexy.missions

import android.content.Context
import android.graphics.Bitmap

/** The F-Droid build ships without MediaPipe, so the Photo mission is unavailable. */
internal object ImageSimilarity {
    const val AVAILABLE = false

    fun similarity(context: Context, photo: Bitmap, referencePath: String): Double =
        throw UnsupportedOperationException("Photo match needs the full build")
}
