package com.fertipos.agroshop.util

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import com.fertipos.agroshop.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.fertipos.agroshop.R

object PLPdfGenerator {
    private val df = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    fun generate(
        context: Context,
        authority: String,
        from: Long,
        to: Long,
        salesSubtotal: Double,
        salesGst: Double,
        salesTotal: Double,
        purchasesSubtotal: Double,
        purchasesGst: Double,
        purchasesTotal: Double,
        grossProfit: Double,
        netAmount: Double,
        hasRemoveAds: Boolean = false
    ): Uri {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 approx
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint().apply { textSize = 14f }
        val currency = CurrencyFormatter.inr
        var y = 40f

        // Header
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(context.getString(R.string.pl_report_title), 40f, y, paint)
        paint.typeface = Typeface.DEFAULT
        y += 20f
        canvas.drawText(context.getString(R.string.from_colon, df.format(Date(from))), 40f, y, paint); y += 16f
        canvas.drawText(context.getString(R.string.to_colon, df.format(Date(to))), 40f, y, paint); y += 20f
        canvas.drawLine(40f, y, 555f, y, paint); y += 18f

        // Sections
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(context.getString(R.string.section_sales), 40f, y, paint)
        paint.typeface = Typeface.DEFAULT
        y += 16f
        canvas.drawText(context.getString(R.string.subtotal_with_amount, currency.format(salesSubtotal)), 60f, y, paint); y += 16f
        canvas.drawText(context.getString(R.string.gst_with_amount, currency.format(salesGst)), 60f, y, paint); y += 16f
        canvas.drawText(context.getString(R.string.total_with_amount, currency.format(salesTotal)), 60f, y, paint); y += 20f

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(context.getString(R.string.section_purchases), 40f, y, paint)
        paint.typeface = Typeface.DEFAULT
        y += 16f
        canvas.drawText(context.getString(R.string.subtotal_with_amount, currency.format(purchasesSubtotal)), 60f, y, paint); y += 16f
        canvas.drawText(context.getString(R.string.gst_with_amount, currency.format(purchasesGst)), 60f, y, paint); y += 16f
        canvas.drawText(context.getString(R.string.total_with_amount, currency.format(purchasesTotal)), 60f, y, paint); y += 20f

        canvas.drawLine(40f, y, 555f, y, paint); y += 18f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(context.getString(R.string.gross_profit_colon, currency.format(grossProfit)), 40f, y, paint); y += 18f
        canvas.drawText(context.getString(R.string.net_amount_colon, currency.format(netAmount)), 40f, y, paint); y += 18f
        paint.typeface = Typeface.DEFAULT

        // Footer with stamp and logo (hidden if user purchased remove-ads)
        PdfFooterUtil.addFooter(context, canvas, paint, 595f, 842f, hasRemoveAds)

        doc.finishPage(page)

        val outFile = File(context.cacheDir, "pl_${System.currentTimeMillis()}.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()

        return FileProvider.getUriForFile(context, authority, outFile)
    }
}
