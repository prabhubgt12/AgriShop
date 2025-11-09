package com.fertipos.agroshop.util

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.fertipos.agroshop.data.local.entities.Invoice
import com.fertipos.agroshop.util.CurrencyFormatter
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.fertipos.agroshop.R

object SalesRangePdfGenerator {
    private val df = SimpleDateFormat("dd-MM-yy", Locale.getDefault())

    fun generate(
        context: Context,
        authority: String,
        from: Long,
        to: Long,
        invoices: List<Invoice>,
        hasRemoveAds: Boolean = false
    ): Uri {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 approx
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint().apply { textSize = 12f }
        val currency = CurrencyFormatter.inr
        var y = 40f

        // Header
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(context.getString(R.string.sales_report_title), 35f, y, paint)
        paint.typeface = Typeface.DEFAULT
        y += 18f
        val rangeLine = context.getString(R.string.from_colon, df.format(Date(from))) +
                "    " + context.getString(R.string.to_colon, df.format(Date(to)))
        canvas.drawText(rangeLine, 35f, y, paint); y += 16f
        canvas.drawLine(35f, y, 585f, y, paint); y += 16f

        // Table header
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(context.getString(R.string.inv_hash), 35f, y, paint)
        canvas.drawText(context.getString(R.string.csv_date), 65f, y, paint)
        canvas.drawText(context.getString(R.string.csv_subtotal), 135f, y, paint)
        canvas.drawText(context.getString(R.string.csv_gst), 235f, y, paint)
        canvas.drawText(context.getString(R.string.csv_total), 305f, y, paint)
        canvas.drawText(context.getString(R.string.paid_label), 405f, y, paint)
        canvas.drawText(context.getString(R.string.balance_only_label), 505f, y, paint)
        paint.typeface = Typeface.DEFAULT
        y += 12f
        canvas.drawLine(35f, y, 585f, y, paint); y += 14f

        var tSubtotal = 0.0
        var tGst = 0.0
        var tTotal = 0.0
        var tPaid = 0.0
        var tBalance = 0.0

        invoices.forEach { inv ->
            if (y > 780f) return@forEach // simple single-page; extend as needed
            val paid = inv.paid
            val balance = inv.total - paid
            canvas.drawText("#${inv.id}", 35f, y, paint)
            canvas.drawText(df.format(Date(inv.date)), 65f, y, paint)
            canvas.drawText(currency.format(inv.subtotal), 135f, y, paint)
            canvas.drawText(currency.format(inv.gstAmount), 235f, y, paint)
            canvas.drawText(currency.format(inv.total), 305f, y, paint)
            canvas.drawText(currency.format(paid), 405f, y, paint)
            canvas.drawText(currency.format(balance), 505f, y, paint)
            y += 16f
            tSubtotal += inv.subtotal
            tGst += inv.gstAmount
            tTotal += inv.total
            tPaid += paid
            tBalance += balance
        }

        y += 10f
        canvas.drawLine(35f, y, 585f, y, paint); y += 16f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(context.getString(R.string.totals_colon), 65f, y, paint)
        canvas.drawText(currency.format(tSubtotal), 135f, y, paint)
        canvas.drawText(currency.format(tGst), 235f, y, paint)
        canvas.drawText(currency.format(tTotal), 305f, y, paint)
        canvas.drawText(currency.format(tPaid), 405f, y, paint)
        canvas.drawText(currency.format(tBalance), 505f, y, paint)
        y += 22f
        canvas.drawText(
            context.getString(
                R.string.summary_sales,
                currency.format(tTotal),
                currency.format(tPaid),
                currency.format(tBalance)
            ),
            35f,
            y,
            paint
        )
        paint.typeface = Typeface.DEFAULT

        // Footer with stamp and logo (hidden if user purchased remove-ads)
        PdfFooterUtil.addFooter(context, canvas, paint, 595f, 842f, hasRemoveAds)

        doc.finishPage(page)

        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val outFile = File(context.cacheDir, "Sales_Report_${ts}.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()

        return FileProvider.getUriForFile(context, authority, outFile)
    }
}
