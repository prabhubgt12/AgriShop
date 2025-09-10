package com.fertipos.agroshop.util

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.graphics.Rect
import androidx.core.content.FileProvider
import com.fertipos.agroshop.data.local.entities.Invoice
import com.fertipos.agroshop.data.local.entities.InvoiceItem
import com.fertipos.agroshop.data.local.entities.Product
import com.fertipos.agroshop.data.local.entities.CompanyProfile
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.fertipos.agroshop.R

object InvoicePdfGenerator {
    private val df = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    data class ItemWithProduct(val item: InvoiceItem, val product: Product?)

    fun generate(
        context: Context,
        authority: String,
        invoice: Invoice,
        customerName: String,
        company: CompanyProfile,
        items: List<ItemWithProduct>,
        paid: Double = 0.0,
        balance: Double = invoice.total,
        hasRemoveAds: Boolean = false
    ): Uri {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 in points (approx)
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint().apply { textSize = 12f }
        var y = 40f

        // Header
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(company.name.ifBlank { context.getString(R.string.invoice_fallback_title) }, 40f, y, paint)
        paint.typeface = Typeface.DEFAULT
        // Optional Logo at top-right
        if (company.logoUri.isNotBlank()) {
            try {
                val uri = Uri.parse(company.logoUri)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bmp = BitmapFactory.decodeStream(input)
                    if (bmp != null) {
                        val targetWidth = 80
                        val aspect = bmp.height.toFloat() / bmp.width.toFloat()
                        val targetHeight = (targetWidth * aspect).toInt().coerceAtLeast(1)
                        val left = 555 - targetWidth
                        val top = 20
                        val dest = Rect(left, top, left + targetWidth, top + targetHeight)
                        canvas.drawBitmap(bmp, null, dest, null)
                    }
                }
            } catch (_: Exception) { /* ignore logo errors */ }
        }
        y += 18f
        val addr1 = listOf(company.addressLine1, company.addressLine2).filter { it.isNotBlank() }.joinToString(", ")
        if (addr1.isNotBlank()) { canvas.drawText(addr1, 40f, y, paint); y += 16f }
        val addr2 = listOf(company.city, company.state, company.pincode).filter { it.isNotBlank() }.joinToString(", ")
        if (addr2.isNotBlank()) { canvas.drawText(addr2, 40f, y, paint); y += 16f }
        if (company.gstin.isNotBlank()) { canvas.drawText(context.getString(R.string.gstin_colon, company.gstin), 40f, y, paint); y += 16f }
        if (company.phone.isNotBlank()) { canvas.drawText(context.getString(R.string.phone_colon_pdf, company.phone), 40f, y, paint); y += 16f }
        if (company.email.isNotBlank()) { canvas.drawText(context.getString(R.string.email_colon_pdf, company.email), 40f, y, paint); y += 16f }

        y += 8f
        canvas.drawLine(40f, y, 555f, y, paint); y += 18f

        // Invoice meta
        canvas.drawText(context.getString(R.string.invoice_id_colon, invoice.id.toString()), 40f, y, paint); y += 16f
        canvas.drawText(context.getString(R.string.date_colon, df.format(Date(invoice.date))), 40f, y, paint); y += 16f
        canvas.drawText(context.getString(R.string.customer_colon, customerName), 40f, y, paint); y += 20f

        // Table header
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(context.getString(R.string.pdf_item), 40f, y, paint)
        canvas.drawText(context.getString(R.string.pdf_qty), 230f, y, paint)
        canvas.drawText(context.getString(R.string.pdf_price), 290f, y, paint)
        canvas.drawText(context.getString(R.string.pdf_gst_percent), 360f, y, paint)
        canvas.drawText(context.getString(R.string.pdf_gst_amount), 420f, y, paint)
        canvas.drawText(context.getString(R.string.pdf_line_total), 500f, y, paint)
        paint.typeface = Typeface.DEFAULT
        y += 14f
        canvas.drawLine(40f, y, 555f, y, paint); y += 14f

        // Items
        items.forEach { row ->
            if (y > 760f) { /* pagination skipped for brevity */ }
            val name = row.product?.name ?: ("Item " + row.item.productId)
            val base = row.item.quantity * row.item.unitPrice
            val gstAmt = base * (row.item.gstPercent / 100.0)
            canvas.drawText(name.take(30), 40f, y, paint)
            canvas.drawText(String.format(Locale.getDefault(), "%.2f", row.item.quantity), 230f, y, paint)
            canvas.drawText(String.format(Locale.getDefault(), "%.2f", row.item.unitPrice), 290f, y, paint)
            canvas.drawText(String.format(Locale.getDefault(), "%.1f", row.item.gstPercent), 360f, y, paint)
            canvas.drawText(String.format(Locale.getDefault(), "%.2f", gstAmt), 420f, y, paint)
            canvas.drawText(String.format(Locale.getDefault(), "%.2f", row.item.lineTotal), 500f, y, paint)
            y += 16f
        }

        y += 10f
        canvas.drawLine(40f, y, 555f, y, paint); y += 18f
        // Totals
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(context.getString(R.string.pdf_subtotal_with_amount, String.format(Locale.getDefault(), "%.2f", invoice.subtotal)), 400f, y, paint); y += 16f
        canvas.drawText(context.getString(R.string.pdf_gst_with_amount, String.format(Locale.getDefault(), "%.2f", invoice.gstAmount)), 400f, y, paint); y += 16f
        canvas.drawText(context.getString(R.string.pdf_total_with_amount, String.format(Locale.getDefault(), "%.2f", invoice.total)), 400f, y, paint); y += 16f
        canvas.drawText(context.getString(R.string.pdf_paid_with_amount, String.format(Locale.getDefault(), "%.2f", paid)), 400f, y, paint); y += 16f
        canvas.drawText(context.getString(R.string.pdf_balance_with_amount, String.format(Locale.getDefault(), "%.2f", balance)), 400f, y, paint); y += 16f
        paint.typeface = Typeface.DEFAULT

        // Add footer with company stamp
        PdfFooterUtil.addFooter(context, canvas, paint, 595f, 842f, hasRemoveAds)

        doc.finishPage(page)

        val outFile = File(context.cacheDir, "invoice_${invoice.id}.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()

        return FileProvider.getUriForFile(context, authority, outFile)
    }
}
