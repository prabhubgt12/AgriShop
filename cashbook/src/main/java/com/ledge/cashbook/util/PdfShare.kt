package com.ledge.cashbook.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.ledge.cashbook.data.local.entities.CashTxn
import com.ledge.cashbook.R
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfShare {
    private val dateFmt = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
    private const val PAGE_WIDTH = 595 // A4 72dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36 // half inch
    private const val COL_GAP = 12f

    fun exportAccount(context: Context, accountName: String, txns: List<CashTxn>) {
        val doc = PdfDocument()
        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas: Canvas = page.canvas

        val title = Paint().apply { textSize = 18f; isFakeBoldText = true }
        val header = Paint().apply { textSize = 12f; isFakeBoldText = true }
        val text = Paint().apply { textSize = 12f }
        val rule = Paint().apply { strokeWidth = 1f }
        var y = MARGIN.toFloat()

        fun newPage() {
            doc.finishPage(page)
            pageNumber += 1
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = MARGIN.toFloat()
        }

        fun ensureSpace(rowHeight: Float) { if (y + rowHeight > PAGE_HEIGHT - MARGIN) newPage() }
        fun lineL(t: String, p: Paint = text, gap: Float = 18f) { canvas.drawText(t, MARGIN.toFloat(), y, p); y += gap }

        // Header
        lineL(context.getString(R.string.title_cash_book), title, 24f)
        lineL(context.getString(R.string.label_customer_with_value, accountName), header)
        lineL(context.getString(R.string.label_date_with_value, SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())), text)

        // Calculate totals here and print Total Balance
        val totalCredit = txns.filter { it.isCredit }.sumOf { it.amount }
        val totalDebit = txns.filter { !it.isCredit }.sumOf { it.amount }
        val closingBalance = totalCredit - totalDebit
        lineL(context.getString(R.string.label_total_balance_with_value, Currency.inr(closingBalance)), header)
        y += 6f

        // Table columns - align similar to screen: Date, Particular, Credit, Debit, Balance
        data class Col(val title: String, val width: Float)
        // Ensure widths + gaps (4 * 12f = 48f) fit within (PAGE_WIDTH - 2*MARGIN) = 523f
        val cols = listOf(
            Col(context.getString(R.string.col_date), 70f),
            Col(context.getString(R.string.col_particular), 140f),
            Col(context.getString(R.string.col_credit), 80f),
            Col(context.getString(R.string.col_debit), 80f),
            Col(context.getString(R.string.col_balance), 100f),
        )
        val startX = MARGIN.toFloat()

        // Header row
        ensureSpace(60f)
        var x = startX
        cols.forEachIndexed { i, col ->
            val drawX = when (i) {
                2, 3, 4 -> { // numeric right-aligned
                    val w = header.measureText(col.title)
                    x + col.width - 4f - w
                }
                else -> x + 4f
            }
            canvas.drawText(col.title, drawX, y, header)
            x += col.width + COL_GAP
        }
        val tableWidth = cols.sumOf { it.width.toDouble() }.toFloat() + COL_GAP * (cols.size - 1)
        y += 12f
        canvas.drawLine(startX, y, startX + tableWidth, y, rule)
        // Extra breathing room so first row isn't stuck to header line
        y += 14f

        // Body rows with running balance
        var running = 0.0
        txns.forEach { t ->
            ensureSpace(24f)
            running += if (t.isCredit) t.amount else -t.amount
            val values = listOf(
                dateFmt.format(Date(t.date)),
                t.note ?: "-",
                if (t.isCredit) Currency.inr(t.amount) else "-",
                if (!t.isCredit) Currency.inr(t.amount) else "-",
                Currency.inr(running)
            )
            x = startX
            values.forEachIndexed { i, v ->
                val drawX = when (i) {
                    2, 3, 4 -> {
                        val w = text.measureText(v)
                        x + cols[i].width - 4f - w
                    }
                    else -> x + 4f
                }
                canvas.drawText(v, drawX, y, text)
                x += cols[i].width + COL_GAP
            }
            y += 12f
            canvas.drawLine(startX, y, startX + tableWidth, y, rule)
            y += 12f
        }

        // Summary row at end: totals and closing balance
        ensureSpace(28f)
        // Leave a small gap before totals
        y += 6f
        var xSum = startX
        val totalLabel = context.getString(R.string.total)
        // Draw "Total" under Particular column start
        // Iterate columns to place values aligned by column
        for (i in cols.indices) {
            val cellWidth = cols[i].width
            val value = when (i) {
                1 -> totalLabel
                2 -> Currency.inr(totalCredit)
                3 -> Currency.inr(totalDebit)
                4 -> Currency.inr(running)
                else -> ""
            }
            if (value.isNotEmpty()) {
                val drawX = if (i in listOf(2,3,4)) {
                    val w = header.measureText(value)
                    xSum + cellWidth - 4f - w
                } else {
                    xSum + 4f
                }
                canvas.drawText(value, drawX, y, header)
            }
            xSum += cellWidth + COL_GAP
        }
        y += 10f
        canvas.drawLine(startX, y, startX + tableWidth, y, rule)

        // Footer advertisement line on current page
        ensureSpace(20f)
        val footerPaint = Paint().apply { textSize = 10f }
        canvas.drawText(context.getString(R.string.pdf_generated_footer), MARGIN.toFloat(), PAGE_HEIGHT - MARGIN.toFloat(), footerPaint)

        // Save
        doc.finishPage(page)
        val safeName = accountName.replace("[^A-Za-z0-9_]".toRegex(), "_")
        val outFile = File(context.cacheDir, "cashbook_${safeName}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()

        shareFile(context, outFile, context.getString(R.string.title_cash_book))
    }

    private fun shareFile(context: Context, file: File, title: String) {
        val authority = context.packageName + ".fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }
}
