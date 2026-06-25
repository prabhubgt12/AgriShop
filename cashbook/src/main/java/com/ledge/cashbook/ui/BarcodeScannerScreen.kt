package com.ledge.cashbook.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.ledge.cashbook.data.api.ProductInfo
import com.ledge.cashbook.data.api.ProductLookupService
import com.ledge.cashbook.data.local.dao.ProductBarcodeDao
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScannerScreen(
    onProductDetected: (ProductInfo) -> Unit,
    onBack: () -> Unit,
    viewModel: BarcodeScannerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val productBarcodeDao = viewModel.dao

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var isScanning by remember { mutableStateOf(true) }
    var showNotFoundMessage by remember { mutableStateOf(false) }
    var lastScannedBarcode by remember { mutableStateOf<String?>(null) }
    var productNameInput by remember { mutableStateOf("") }
    var productPriceInput by remember { mutableStateOf("") }

    // Animation for scanning laser line
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val laserOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser"
    )

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    DisposableEffect(hasCameraPermission) {
        if (!hasCameraPermission) return@DisposableEffect onDispose {}

        val executor = ContextCompat.getMainExecutor(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView?.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(executor) { imageProxy ->
                        if (isScanning) {
                            processImageProxy(imageProxy) { barcode ->
                                if (barcode != lastScannedBarcode) {
                                    lastScannedBarcode = barcode
                                    scope.launch {
                                        val product = ProductLookupService.lookupByBarcode(barcode, localDao = productBarcodeDao)
                                        if (product != null) {
                                            isScanning = false
                                            onProductDetected(product)
                                        } else {
                                            showNotFoundMessage = true
                                        }
                                    }
                                }
                            }
                        } else {
                            imageProxy.close()
                        }
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, executor)

        onDispose {
            if (hasCameraPermission) {
                cameraProviderFuture.get().unbindAll()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Barcode") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (hasCameraPermission) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { previewView = it }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Scanning overlay with laser line
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val scanBoxSize = 250.dp
                    Box(
                        modifier = Modifier
                            .size(scanBoxSize)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        // Laser scanning line
                        Canvas(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            val lineY = size.height * laserOffset
                            drawLine(
                                color = Color(0xFF00FF00),
                                start = Offset(0f, lineY),
                                end = Offset(size.width, lineY),
                                strokeWidth = 3f
                            )
                        }
                    }

                    // Corner accents
                    Box(modifier = Modifier.size(scanBoxSize)) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(Color.Transparent)
                                .border(
                                    width = 3.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(topStart = 4.dp)
                                )
                        )
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .align(Alignment.TopEnd)
                                .background(Color.Transparent)
                                .border(
                                    width = 3.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(topEnd = 4.dp)
                                )
                        )
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .align(Alignment.BottomStart)
                                .background(Color.Transparent)
                                .border(
                                    width = 3.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(bottomStart = 4.dp)
                                )
                        )
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .align(Alignment.BottomEnd)
                                .background(Color.Transparent)
                                .border(
                                    width = 3.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(bottomEnd = 4.dp)
                                )
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Point camera at barcode",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            modifier = Modifier.padding(12.dp, 8.dp)
                        )
                    }
                }

                if (showNotFoundMessage) {
                    AlertDialog(
                        onDismissRequest = { 
                            showNotFoundMessage = false
                            productNameInput = ""
                            productPriceInput = ""
                            isScanning = true
                        },
                        title = { Text("Product not found") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("This barcode is not in our database. Enter product details to save for future scans:")
                                OutlinedTextField(
                                    value = productNameInput,
                                    onValueChange = { productNameInput = it },
                                    label = { Text("Product name") },
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = productPriceInput,
                                    onValueChange = { input -> productPriceInput = input.filter { it.isDigit() || it == '.' } },
                                    label = { Text("Price (optional)") },
                                    singleLine = true
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = { 
                                    if (productNameInput.isNotBlank()) {
                                        scope.launch {
                                            val price = productPriceInput.toDoubleOrNull()
                                            ProductLookupService.saveToLocal(
                                                barcode = lastScannedBarcode ?: "",
                                                name = productNameInput,
                                                price = price,
                                                category = "",
                                                localDao = productBarcodeDao
                                            )
                                            onProductDetected(
                                                ProductInfo(
                                                    name = productNameInput,
                                                    category = "",
                                                    brand = "",
                                                    price = price
                                                )
                                            )
                                        }
                                    }
                                },
                                enabled = productNameInput.isNotBlank()
                            ) {
                                Text("Save & Use")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { 
                                showNotFoundMessage = false
                                productNameInput = ""
                                productPriceInput = ""
                                onBack()
                            }) {
                                Text("Enter manually")
                            }
                        }
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Camera permission required",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }) {
                            Text("Grant Permission")
                        }
                    }
                }
            }
        }
    }
}

private fun processImageProxy(
    imageProxy: ImageProxy,
    onBarcodeDetected: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        val scanner = BarcodeScanning.getClient()

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    val rawValue = barcode.rawValue
                    if (rawValue != null) {
                        onBarcodeDetected(rawValue)
                        break
                    }
                }
            }
            .addOnFailureListener {
                // Handle failure
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}
