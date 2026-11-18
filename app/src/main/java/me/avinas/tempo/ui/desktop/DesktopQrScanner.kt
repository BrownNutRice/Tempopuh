package me.avinas.tempo.ui.desktop

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

/** The caller must have CAMERA permission before adding this view to composition. */
@Composable
fun QrScannerView(
    modifier: Modifier = Modifier,
    onCameraError: () -> Unit,
    onQrDetected: (rawText: String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val onDetected = rememberUpdatedState(onQrDetected)
    val onError = rememberUpdatedState(onCameraError)
    val view = remember(context) {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    key(view) { AndroidView(factory = { view }, modifier = modifier) }

    DisposableEffect(view, lifecycleOwner) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val executor = Executors.newSingleThreadExecutor()
        val preview = Preview.Builder().build()
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        var provider: ProcessCameraProvider? = null
        // Both disposal and provider/detection callbacks run on the main thread.
        var disposed = false
        var delivered = false

        fun release() {
            disposed = true
            analysis.clearAnalyzer()
            provider?.unbind(preview, analysis)
            preview.setSurfaceProvider(null)
            executor.shutdown()
        }

        fun reportFailure(error: Exception) {
            Log.e("QrScannerView", "Could not start the QR camera", error)
            release()
            onError.value()
        }

        try {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (disposed) return@addListener
                try {
                    val camera = future.get()
                    provider = camera
                    preview.setSurfaceProvider(view.surfaceProvider)
                    analysis.setAnalyzer(executor) { image ->
                        try {
                            val plane = image.planes[0]
                            val buffer = plane.buffer
                            val start = buffer.position()
                            val bytes = ByteArray(image.width * image.height)
                            for (row in 0 until image.height) {
                                buffer.position(start + row * plane.rowStride)
                                buffer.get(bytes, row * image.width, image.width)
                            }
                            val source = PlanarYUVLuminanceSource(
                                bytes, image.width, image.height,
                                0, 0, image.width, image.height, false,
                            )
                            val result = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)))
                            mainExecutor.execute {
                                if (!disposed && !delivered) {
                                    delivered = true
                                    onDetected.value(result.text)
                                }
                            }
                        } catch (_: NotFoundException) {
                            // No QR code in this frame.
                        } catch (error: Exception) {
                            Log.w("QrScannerView", "Could not decode QR frame", error)
                        } finally {
                            image.close()
                        }
                    }
                    camera.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } catch (error: Exception) {
                    reportFailure(error)
                }
            }, mainExecutor)
        } catch (error: Exception) {
            reportFailure(error)
        }
        onDispose { if (!disposed) release() }
    }
}
