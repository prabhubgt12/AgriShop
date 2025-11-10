package com.fertipos.agroshop.ui.print

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fertipos.agroshop.R
import com.fertipos.agroshop.ui.screens.AppNavViewModel
import java.io.FileOutputStream
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale

@Composable
fun PrintPreviewScreen(navVm: AppNavViewModel = hiltViewModel()) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        val context = LocalContext.current
        val uri by navVm.pendingPrintPreviewUri.collectAsState()
        val jobName by navVm.pendingPrintJobName.collectAsState()

        // If nothing to preview, return to Home
        if (uri == null || jobName == null) {
            LaunchedEffect(Unit) {
                navVm.navigateTo(0)
            }
            return@Surface
        }

        val previewBitmap by remember(uri) { mutableStateOf(renderFirstPageBitmap(context, uri!!)) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
                Text(text = "Preview", style = MaterialTheme.typography.titleMedium, color = Color.Black)
            }

            // Zoom/Pan state
            var scale by remember { mutableStateOf(1f) }
            var offsetX by remember { mutableStateOf(0f) }
            var offsetY by remember { mutableStateOf(0f) }

            // Preview area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFFEEEEEE)), // light gray to contrast page
                contentAlignment = Alignment.TopCenter
            ) {
                if (previewBitmap != null) {
                    val aspect = previewBitmap!!.width.toFloat() / previewBitmap!!.height.toFloat()
                    Card(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                            .aspectRatio(aspect)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offsetX
                                translationY = offsetY
                            }
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 4f)
                                    // Only pan when zoomed in to avoid drifting at 1x
                                    if (scale > 1f) {
                                        offsetX += pan.x
                                        offsetY += pan.y
                                    } else {
                                        offsetX = 0f; offsetY = 0f
                                    }
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Image(
                            bitmap = previewBitmap!!.asImageBitmap(),
                            contentDescription = "PDF Preview",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Text(text = "(Preview unavailable)", color = Color.Black)
                }
            }

            // Bottom bar with themed background
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        navVm.clearPrintPreview()
                        navVm.navigateTo(0)
                    }) {
                        Text(stringResource(R.string.close))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = {
                            val targetUri = uri ?: return@IconButton
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(android.content.Intent.EXTRA_STREAM, targetUri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.share_pdf)))
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.share_pdf))
                        }
                        IconButton(onClick = {
                            val activity = context.asActivity()
                            if (activity == null) {
                                android.widget.Toast.makeText(context, context.getString(R.string.print_failed_after_save_try_view_bills), android.widget.Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }
                            val name = jobName ?: return@IconButton
                            val targetUri = uri ?: return@IconButton
                            val adapter = buildUriPrintAdapter(context, targetUri, name)
                            val printManager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
                            printManager.print(name, adapter, PrintAttributes.Builder().build())
                        }) {
                            Icon(Icons.Filled.Print, contentDescription = stringResource(R.string.print))
                        }
                    }
                }
            }
        }
    }
}

private fun renderFirstPageBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        pfd.use { fd ->
            PdfRenderer(fd).use { renderer ->
                if (renderer.pageCount <= 0) return null
                renderer.openPage(0).use { page ->
                    val w = page.width
                    val h = page.height
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        }
    } catch (_: Exception) {
        null
    }
}

private fun buildUriPrintAdapter(context: Context, uri: Uri, jobName: String): PrintDocumentAdapter {
    return object : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes?,
            cancellationSignal: android.os.CancellationSignal?,
            callback: LayoutResultCallback?,
            extras: android.os.Bundle?
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback?.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder("${jobName.replace(' ', '_')}.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .build()
            callback?.onLayoutFinished(info, true)
        }
        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: android.os.ParcelFileDescriptor?,
            cancellationSignal: android.os.CancellationSignal?,
            callback: WriteResultCallback?
        ) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destination?.fileDescriptor?.let { fd ->
                        FileOutputStream(fd).use { out ->
                            val buf = ByteArray(8192)
                            while (true) {
                                val r = input.read(buf)
                                if (r <= 0) break
                                out.write(buf, 0, r)
                            }
                        }
                    }
                }
                callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback?.onWriteFailed(e.message)
            }
        }
    }
}

private tailrec fun Context.asActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.asActivity()
    else -> null
}
