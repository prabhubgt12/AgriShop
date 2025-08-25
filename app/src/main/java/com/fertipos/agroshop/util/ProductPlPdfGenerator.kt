package com.fertipos.agroshop.util

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.fertipos.agroshop.ui.reports.ReportsViewModel
import java.io.File
import java.io.FileOutputStream
import com.fertipos.agroshop.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ProductPlPdfGenerator {
    private val df = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    fun generate(
        context: Context,
        authority: String,
        from: Long,
        to: Long,
        rows: List<ReportsViewModel.ProductPLRow>
    ): Uri {
        val currency = CurrencyFormatter.inr

        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        val paint = Paint().apply { textSize = 12f }
        var y = 40f

        // Header
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Product-wise Profit & Loss", 40f, y, paint)
        paint.typeface = Typeface.DEFAULT
        y += 18f
        canvas.drawText("From: ${df.format(Date(from))}", 40f, y, paint); y += 16f
        canvas.drawText("To:   ${df.format(Date(to))}", 40f, y, paint); y += 12f
        canvas.drawLine(40f, y, 555f, y, paint); y += 16f

        // Table header
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val colX = floatArrayOf(40f, 300f, 370f, 445f, 520f) // Product | Qty | Sales | Cost | Profit
        canvas.drawText("Product", colX[0], y, paint)
        canvas.drawText("Qty", colX[1], y, paint)
        canvas.drawText("Sales", colX[2], y, paint)
        canvas.drawText("Cost", colX[3], y, paint)
        canvas.drawText("Profit", colX[4], y, paint)
        paint.typeface = Typeface.DEFAULT
        y += 14f
        canvas.drawLine(40f, y, 555f, y, paint); y += 14f

        // Rows
        var totalSales = 0.0
        var totalCost = 0.0
        var totalProfit = 0.0

        rows.forEach { r ->
            if (y > 800f) {
                // Finish current page and start new
                doc.finishPage(page)
                pageNumber += 1
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                y = 40f
                // continued header
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("Product-wise Profit & Loss (contd.)", 40f, y, paint)
                paint.typeface = Typeface.DEFAULT
                y += 18f
                canvas.drawLine(40f, y, 555f, y, paint); y += 14f
                // table header
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("Product", colX[0], y, paint)
                canvas.drawText("Qty", colX[1], y, paint)
                canvas.drawText("Sales", colX[2], y, paint)
                canvas.drawText("Cost", colX[3], y, paint)
                canvas.drawText("Profit", colX[4], y, paint)
                paint.typeface = Typeface.DEFAULT
                y += 14f
                canvas.drawLine(40f, y, 555f, y, paint); y += 14f
            }
            val qtyStr = String.format(Locale.getDefault(), "%.2f", r.quantity)
            val salesStr = currency.format(r.salesAmount)
            val costStr = currency.format(r.costAmount)
            val profitStr = currency.format(r.profit)

            canvas.drawText(r.productName, colX[0], y, paint)
            canvas.drawText(qtyStr, colX[1], y, paint)
            canvas.drawText(salesStr, colX[2], y, paint)
            canvas.drawText(costStr, colX[3], y, paint)
            canvas.drawText(profitStr, colX[4], y, paint)
            y += 16f

            totalSales += r.salesAmount
            totalCost += r.costAmount
            totalProfit += r.profit
        }

        // Totals
        y += 6f
        canvas.drawLine(40f, y, 555f, y, paint); y += 16f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Totals", colX[0], y, paint)
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("", colX[1], y, paint)
        canvas.drawText(currency.format(totalSales), colX[2], y, paint)
        canvas.drawText(currency.format(totalCost), colX[3], y, paint)
        canvas.drawText(currency.format(totalProfit), colX[4], y, paint)

        doc.finishPage(page)

        val outFile = File(context.cacheDir, "product_pl_${System.currentTimeMillis()}.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()

        return FileProvider.getUriForFile(context, authority, outFile)
    }
}
