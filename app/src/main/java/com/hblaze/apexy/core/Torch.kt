package com.hblaze.apexy.core

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Strobes the camera flash. Needs no permission; silently does nothing on phones without one. */
class Torch(context: Context) {
    private val cameras = context.getSystemService(CameraManager::class.java)
    private val id: String? = runCatching {
        cameras.cameraIdList.firstOrNull { cameras.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    }.getOrNull()
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        val camera = id ?: return
        if (job?.isActive == true) return
        job = scope.launch {
            var on = false
            while (isActive) {
                on = !on
                runCatching { cameras.setTorchMode(camera, on) }
                delay(if (on) 120 else 480)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        id?.let { runCatching { cameras.setTorchMode(it, false) } }
    }
}
