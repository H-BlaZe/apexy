package com.apexy.alarm.missions

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
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
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.Embedding
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
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

private val LINEAR_FORMATS = intArrayOf(
    Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
    Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39, Barcode.FORMAT_CODE_93, Barcode.FORMAT_ITF, Barcode.FORMAT_CODABAR,
)

/** Frame analyzer that reports the raw value of every code it reads. */
class CodeAnalyzer(kind: CodeKind, private val onCode: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val scanner = BarcodeScanning.getClient(
        when (kind) {
            CodeKind.QR -> BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            CodeKind.BARCODE -> BarcodeScannerOptions.Builder().setBarcodeFormats(LINEAR_FORMATS[0], *LINEAR_FORMATS.copyOfRange(1, LINEAR_FORMATS.size))
        }.build(),
    )

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image ?: return proxy.close()
        scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
            .addOnSuccessListener { codes -> codes.firstNotNullOfOrNull { it.rawValue }?.let(onCode) }
            .addOnCompleteListener { proxy.close() }
    }
}

/** Compares photos with an on-device MobileNet embedding; robust to small changes in angle and light. */
object PhotoMatcher {
    /** Minimum cosine similarity for each difficulty level. */
    val THRESHOLDS = doubleArrayOf(0.55, 0.62, 0.70, 0.78, 0.85)

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

    /** Call off the main thread. */
    fun similarity(context: Context, photo: Bitmap, referencePath: String): Double {
        val ref = synchronized(refCache) {
            refCache.getOrPut(referencePath) {
                embed(context, android.graphics.BitmapFactory.decodeFile(referencePath))
            }
        }
        return ImageEmbedder.cosineSimilarity(embed(context, photo), ref)
    }

    fun saveReference(context: Context, bitmap: Bitmap): String {
        val dir = File(context.filesDir, "mission_photos").apply { mkdirs() }
        val scale = 720f / maxOf(bitmap.width, bitmap.height)
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return file.absolutePath
    }
}
