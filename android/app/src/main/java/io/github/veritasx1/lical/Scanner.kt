package io.github.veritasx1.lical

import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Full-screen camera that reads one QR code (CameraX + ZXing, all on the phone – no picture is
 *  kept or sent anywhere). onResult(null) when cancelled or the camera can't open. */
@Composable
fun QrScanner(onResult: (String?) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val done = remember { AtomicBoolean(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val preview = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    fun finish(text: String?) {
        if (done.compareAndSet(false, true)) ContextCompat.getMainExecutor(context).execute { onResult(text) }
    }

    DisposableEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        val reader = QRCodeReader()
        val hints = mapOf(DecodeHintType.TRY_HARDER to true, DecodeHintType.CHARACTER_SET to "UTF-8")
        future.addListener({
            val provider = future.get()
            val show = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(executor) { image ->
                try {
                    val plane = image.planes[0]
                    val buffer = plane.buffer
                    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                    val source = PlanarYUVLuminanceSource(bytes, plane.rowStride, image.height, 0, 0, image.width, image.height, false)
                    finish(reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text)
                } catch (error: Exception) {
                    // No code in this frame.
                } finally {
                    image.close()
                }
            }
            try {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, show, analysis)
            } catch (error: Exception) {
                finish(null)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            try { future.get().unbindAll() } catch (error: Exception) { }
            executor.shutdown()
        }
    }

    BackHandler { finish(null) }
    Box(Modifier.fillMaxSize().background(Color.Black).clickable(enabled = false) {}) {
        AndroidView({ preview }, Modifier.fillMaxSize())
        Box(Modifier.align(Alignment.Center).size(240.dp).border(3.dp, Color.White, RoundedCornerShape(24.dp)))
        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(8.dp)) {
            Box(Modifier.fillMaxWidth()) {
                BasicText("Abbrechen", style = style(17f, 600, Color.White), modifier = Modifier.align(Alignment.CenterEnd).clickable { finish(null) }.padding(10.dp))
            }
            BasicText("Halte die Kamera auf den QR-Code des Termins.", style = style(15f, 400, Color.White).copy(textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth().padding(24.dp))
        }
    }
}
