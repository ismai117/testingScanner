package com.example.scannersdk

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraX
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.CameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.scannersdk.ui.theme.ScannerSDKTheme
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScannerSDKTheme {
                var showCamera by remember { mutableStateOf(false) }
                var barcode by remember { mutableStateOf("") }
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Text(
                            text = barcode
                        )
                        Button(
                            onClick = {
                                showCamera = true
                            }
                        ) {
                            Text(text = "Show Scanner")
                        }
                    }
                    if (showCamera) {
                        CameraScreen(
                            onSuccess = {
                                barcode = it
                                showCamera = false
                            },
                            onCancel = {
                                showCamera = false
                            }
                        )
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    onSuccess: (String) -> Unit,
    onCancel: () -> Unit
) {
    val localContext = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember {
        ProcessCameraProvider.getInstance(localContext)
    }
    var camera: Camera? by remember { mutableStateOf(null) }
    var cameraControl: CameraControl? by remember { mutableStateOf(null) }
    var torchEnabled by remember { mutableStateOf(false) }

    var zoomLevel by remember { mutableFloatStateOf(1f) }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val previewView = PreviewView(context)
                val preview = androidx.camera.core.Preview.Builder().build()
                val selector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build()

                preview.surfaceProvider = previewView.surfaceProvider

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                imageAnalysis.setAnalyzer(
                    ContextCompat.getMainExecutor(context),
                    BarcodeAnalyzer(onSuccess = onSuccess, onCancel = onCancel, camera = camera)
                )

                runCatching {
                    cameraProviderFuture.get()?.let { cameraProvider ->
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            selector,
                            preview,
                            imageAnalysis
                        )
                        cameraControl = camera?.cameraControl
                    }
                }.onFailure {
                    Log.e("CAMERA", "Camera bind error ${it.localizedMessage}", it)
                }
                previewView
            }
        )
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = "Scan Code"
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        onCancel()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = null
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        torchEnabled = !torchEnabled
                        cameraControl?.enableTorch(torchEnabled)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null
                    )
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = Color.Transparent
            )
        )
        Box(
            modifier = Modifier.align(Alignment.Center)
        ) {
            Slider(
                value = zoomLevel,
                onValueChange = {
                    zoomLevel = it
                    cameraControl?.setZoomRatio(it)
                },
                valueRange = 1f..4f,
                steps = 30,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            )
        }

    }
}

class BarcodeAnalyzer(
    private val onSuccess: (String) -> Unit,
    private val onCancel: () -> Unit,
    private val camera: Camera?
) : ImageAnalysis.Analyzer {

    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
        .setZoomSuggestionOptions(
            ZoomSuggestionOptions.Builder { setZoom(it) }
                .setMaxSupportedZoomRatio(Float.MAX_VALUE)
                .build()
        )
        .build()

    private val scanner = BarcodeScanning.getClient(options)

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        imageProxy.image?.let { image ->
            scanner.process(
                InputImage.fromMediaImage(
                    image, imageProxy.imageInfo.rotationDegrees
                )
            )
                .addOnCanceledListener {
                    onCancel()
                }
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        when (barcode.valueType) {
                            Barcode.FORMAT_QR_CODE -> {
                                onSuccess(barcode.displayValue.orEmpty())
                            }

                            Barcode.FORMAT_CODE_128 -> {
                                onSuccess(barcode.displayValue.orEmpty())
                            }

                            Barcode.FORMAT_EAN_13 -> {
                                onSuccess(barcode.displayValue.orEmpty())
                            }

                            Barcode.FORMAT_EAN_8 -> {
                                onSuccess(barcode.displayValue.orEmpty())
                            }

                            Barcode.FORMAT_UPC_E -> {
                                onSuccess(barcode.displayValue.orEmpty())
                            }

                            Barcode.TYPE_PRODUCT -> {
                                if (barcode.displayValue == "5057967776153") {
                                    onSuccess(barcode.displayValue.orEmpty())
                                } else if (barcode.displayValue == "5054267014824") {
                                    onSuccess(barcode.displayValue.orEmpty())
                                }
                            }
                        }
                    }
                }.addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }

    private fun setZoom(zoomRatio: Float): Boolean {
        val cameraControl = camera?.cameraControl
        return if (cameraControl != null) {
            cameraControl.setZoomRatio(zoomRatio)
            true
        } else {
            false
        }
    }

}
