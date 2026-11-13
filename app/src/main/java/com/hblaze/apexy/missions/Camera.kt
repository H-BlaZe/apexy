package com.hblaze.apexy.missions

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.Executors

/** Back-camera preview; [analyzer] receives frames when set. [onReady] hands out the view for snapshots. */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    analyzer: ImageAnalysis.Analyzer? = null,
    onReady: (PreviewView) -> Unit = {},
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val view = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
            val useCases = buildList {
                add(preview)
                if (analyzer != null) {
                    add(
                        ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { it.setAnalyzer(executor, analyzer) },
                    )
                }
            }
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, *useCases.toTypedArray())
            }
            onReady(view)
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { future.get().unbindAll() }
            executor.shutdown()
        }
    }
    AndroidView({ view }, modifier)
}

enum class CodeKind { QR, BARCODE }

private val LINEAR_FORMATS = listOf(
    BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E,
    BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.CODE_93, BarcodeFormat.ITF, BarcodeFormat.CODABAR,
)

/** Frame analyzer that reports the raw value of every code it reads (ZXing, on the luminance plane). */
class CodeAnalyzer(private val kind: CodeKind, private val onCode: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to if (kind == CodeKind.QR) listOf(BarcodeFormat.QR_CODE) else LINEAR_FORMATS,
                DecodeHintType.TRY_HARDER to true,
            ),
        )
    }

    override fun analyze(proxy: ImageProxy) {
        try {
            val plane = proxy.planes[0]
            val w = proxy.width
            val h = proxy.height
            val buf = plane.buffer
            val y = ByteArray(w * h)
            for (row in 0 until h) {
                buf.position(row * plane.rowStride)
                if (plane.pixelStride == 1) buf.get(y, row * w, w)
                else for (col in 0 until w) y[row * w + col] = buf.get(row * plane.rowStride + col * plane.pixelStride)
            }
            // Linear codes are only read across their bars, so also try the frame turned a quarter.
            val value = decode(y, w, h) ?: if (kind == CodeKind.BARCODE) decode(rotate(y, w, h), h, w) else null
            value?.let(onCode)
        } finally {
            proxy.close()
        }
    }

    private fun decode(y: ByteArray, w: Int, h: Int): String? = try {
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(PlanarYUVLuminanceSource(y, w, h, 0, 0, w, h, false)))).text
    } catch (_: ReaderException) {
        null
    } finally {
        reader.reset()
    }

    private fun rotate(y: ByteArray, w: Int, h: Int): ByteArray {
        val out = ByteArray(y.size)
        for (row in 0 until h) for (col in 0 until w) out[col * h + (h - 1 - row)] = y[row * w + col]
        return out
    }
}

/** Compares photos with an on-device MobileNet embedding; robust to small changes in angle and light. */
object PhotoMatcher {
    /** Minimum cosine similarity for each difficulty level. */
    val THRESHOLDS = doubleArrayOf(0.55, 0.62, 0.70, 0.78, 0.85)

    /** False in the F-Droid build, which ships without the embedding model. */
    const val AVAILABLE = ImageSimilarity.AVAILABLE

    /** Call off the main thread. */
    fun similarity(context: Context, photo: Bitmap, referencePath: String): Double =
        ImageSimilarity.similarity(context, photo, referencePath)

    fun saveReference(context: Context, bitmap: Bitmap): String {
        val dir = File(context.filesDir, "mission_photos").apply { mkdirs() }
        val scale = 720f / maxOf(bitmap.width, bitmap.height)
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return file.absolutePath
    }
}
