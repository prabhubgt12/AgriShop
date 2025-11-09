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
import com.fertipos.agroshop.R

object ProductPlPdfGenerator {
    private val df = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    fun generate(
        context: Context,
        authority: String,
        from: Long,
        to: Long,
        rows: List<ReportsViewModel.ProductPLRow>,
        hasRemoveAds: Boolean = false
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
        canvas.drawText(context.getString(R.string.product_pl_title) + " Report", 40f, y, paint)
        paint.typeface = Typeface.DEFAULT
        y += 18f
        val rangeLine = context.getString(R.string.from_colon, df.format(Date(from))) +
                "    " + context.getString(R.string.to_colon, df.format(Date(to)))
        canvas.drawText(rangeLine, 40f, y, paint); y += 16f
        canvas.drawLine(40f, y, 555f, y, paint); y += 16f

        // Table header
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val colX = floatArrayOf(40f, 300f, 370f, 445f, 520f) // Product | Qty | Sales | Cost | Profit
        canvas.drawText(context.getString(R.string.pdf_product), colX[0], y, paint)
        canvas.drawText(context.getString(R.string.pdf_qty), colX[1], y, paint)
        canvas.drawText(context.getString(R.string.pdf_sales), colX[2], y, paint)
        canvas.drawText(context.getString(R.string.pdf_cost), colX[3], y, paint)
        canvas.drawText(context.getString(R.string.pdf_profit), colX[4], y, paint)
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
                canvas.drawText(context.getString(R.string.product_pl_title_contd), 40f, y, paint)
                paint.typeface = Typeface.DEFAULT
                y += 18f
                canvas.drawLine(40f, y, 555f, y, paint); y += 14f
                // table header
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(context.getString(R.string.pdf_product), colX[0], y, paint)
                canvas.drawText(context.getString(R.string.pdf_qty), colX[1], y, paint)
                canvas.drawText(context.getString(R.string.pdf_sales), colX[2], y, paint)
                canvas.drawText(context.getString(R.string.pdf_cost), colX[3], y, paint)
                canvas.drawText(context.getString(R.string.pdf_profit), colX[4], y, paint)
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
        canvas.drawText(context.getString(R.string.pdf_totals), colX[0], y, paint)
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("", colX[1], y, paint)
        canvas.drawText(currency.format(totalSales), colX[2], y, paint)
        canvas.drawText(currency.format(totalCost), colX[3], y, paint)
        canvas.drawText(currency.format(totalProfit), colX[4], y, paint)

        // Footer with stamp and logo (hidden if user purchased remove-ads)
        PdfFooterUtil.addFooter(context, canvas, paint, pageWidth.toFloat(), pageHeight.toFloat(), hasRemoveAds)

        doc.finishPage(page)

        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val outFile = File(context.cacheDir, "Product_PL_Report_${ts}.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()

        return FileProvider.getUriForFile(context, authority, outFile)
    }
}
