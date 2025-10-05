package com.ledge.ledgerbook.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.ledge.ledgerbook.ui.LedgerViewModel
import com.ledge.ledgerbook.R
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date

// Type alias in UI module: typealias LedgerItemVM = LedgerViewModel.LedgerItemVM
// We refer to it directly here via full name

object PdfShareUtils {
    private fun toCamel(label: String?): String {
        if (label.isNullOrBlank()) return ""
        val lower = label.lowercase()
        return lower.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
    private val dateFmt = SimpleDateFormat("dd/MM/yyyy")
    private const val PAGE_WIDTH = 595 // A4 72dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36 // half inch
    private const val COL_GAP = 16f

    fun shareEntry(context: Context, item: LedgerViewModel.LedgerItemVM, includePromo: Boolean) {
        val doc = PdfDocument()
        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas: Canvas = page.canvas
        val title = Paint().apply { textSize = 18f; isFakeBoldText = true }
        val label = Paint().apply { textSize = 12f; isFakeBoldText = true }
        val text = Paint().apply { textSize = 12f }
        var y = MARGIN.toFloat()

        fun newPage() {
            doc.finishPage(page)
            pageNumber += 1
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = MARGIN.toFloat()
        }

        fun lineL(t: String, p: Paint = text, gap: Float = 18f) { canvas.drawText(t, MARGIN.toFloat(), y, p); y += gap }

        // Header
        lineL(context.getString(R.string.pdf_title_receipt), title, 24f)
        lineL(context.getString(R.string.label_date_with_value, dateFmt.format(Date())), text)
        y += 6f
        lineL(context.getString(R.string.label_customer_with_value, item.name), label)
        y += 6f

        // Summary (no box)
        fun summaryLine(lbl: String, value: String) { lineL("$lbl: $value", text) }
        // Localized type label (LEND/BORROW)
        val typeValue = when (item.type?.uppercase()) {
            "LEND" -> context.getString(R.string.lend)
            "BORROW" -> context.getString(R.string.borrow)
            else -> item.type?.let { toCamel(it) } ?: ""
        }
        summaryLine(context.getString(R.string.label_type), typeValue)
        summaryLine(context.getString(R.string.label_principal), CurrencyFormatter.format(item.principal))
        // Localized rate basis (MONTHLY/YEARLY)
        val basisValue = when (item.rateBasis?.uppercase()) {
            "MONTHLY" -> context.getString(R.string.monthly)
            "YEARLY" -> context.getString(R.string.yearly)
            else -> item.rateBasis?.let { toCamel(it) } ?: ""
        }
        summaryLine(context.getString(R.string.label_rate), "${item.rate}% $basisValue")
        summaryLine(context.getString(R.string.label_from), item.dateStr)
        summaryLine(context.getString(R.string.label_interest), CurrencyFormatter.format(item.accrued))
        summaryLine(context.getString(R.string.label_total), CurrencyFormatter.format(item.total))
        y += 8f
        if (includePromo) {
            // Footer note with app link for free users
            lineL("", text, 6f)
            lineL(context.getString(R.string.generated_by_footer_with_link, "https://play.google.com/store/apps/details?id=com.ledge.ledgerbook"), text, 16f)
        }

        // Save
        doc.finishPage(page)
        val outFile = File(context.cacheDir, "receipt_${item.name}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()

        shareFile(context, outFile, "Receipt - ${item.name}")
    }

    fun shareGroup(context: Context, name: String, items: List<LedgerViewModel.LedgerItemVM>, includePromo: Boolean) {
        val doc = PdfDocument()
        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas: Canvas = page.canvas
        val title = Paint().apply { textSize = 18f; isFakeBoldText = true }
        val header = Paint().apply { textSize = 12f; isFakeBoldText = true }
        val text = Paint().apply { textSize = 12f }
        val border = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1.2f }
        val rule = Paint().apply { strokeWidth = 1f }
        var y = MARGIN.toFloat()

        fun newPage() {
            doc.finishPage(page)
            pageNumber += 1
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = MARGIN.toFloat()
        }

        fun lineL(t: String, p: Paint = text, gap: Float = 18f) {
            canvas.drawText(t, MARGIN.toFloat(), y, p); y += gap
        }

        // Summary header
        val lend = items.filter { it.type == "LEND" }
        val borrow = items.filter { it.type == "BORROW" }
        val lendTotal = lend.sumOf { it.total }
        val borrowTotal = borrow.sumOf { it.total }
        val net = lendTotal - borrowTotal

        lineL(context.getString(R.string.pdf_title_statement), title, 24f)
        lineL(context.getString(R.string.label_customer_with_value, name), header)
        lineL(context.getString(R.string.label_date_with_value, dateFmt.format(Date())))
        y += 6f
        lineL(context.getString(R.string.label_total_lend_with_value, CurrencyFormatter.format(lendTotal)))
        lineL(context.getString(R.string.label_total_borrow_with_value, CurrencyFormatter.format(borrowTotal)))
        lineL(context.getString(R.string.label_net_with_value, CurrencyFormatter.format(net)), header)
        // Extra spacing before table sections
        y += 12f

        // Table columns
        data class Col(val title: String, val width: Float)
        val cols = listOf(
            Col(context.getString(R.string.col_date), 90f),
            Col(context.getString(R.string.col_principal), 100f),
            Col(context.getString(R.string.col_rate), 100f),
            Col(context.getString(R.string.col_interest), 100f),
            Col(context.getString(R.string.col_total), 100f),
        )

        fun ensureSpace(rowHeight: Float) { if (y + rowHeight > PAGE_HEIGHT - MARGIN) newPage() }

        fun drawTable(titleText: String, rows: List<LedgerViewModel.LedgerItemVM>) {
            if (rows.isEmpty()) return
            ensureSpace(60f)
            // Section title
            lineL(titleText, header, 22f)
            // Header row (align like body: right-align numeric columns, left-align others)
            val startX = MARGIN.toFloat()
            var x = startX
            for (i in cols.indices) {
                val col = cols[i]
                val titleText = col.title
                val cellWidth = col.width
                val drawX = when (i) {
                    1, 3, 4 -> { // numeric columns right-aligned
                        val w = header.measureText(titleText)
                        x + cellWidth - 4f - w
                    }
                    else -> x + 4f
                }
                canvas.drawText(titleText, drawX, y, header)
                x += cellWidth + COL_GAP
            }
            val tableWidth = cols.sumOf { it.width.toDouble() }.toFloat() + COL_GAP * (cols.size - 1)
            // Draw header underline sufficiently below text baseline
            y += 14f
            canvas.drawLine(startX, y, startX + tableWidth, y, rule)
            y += 12f

            // Body rows
            rows.forEach { r ->
                ensureSpace(22f)
                x = startX
                val rowBasis = when (r.rateBasis.uppercase()) {
                    "MONTHLY" -> context.getString(R.string.monthly)
                    "YEARLY" -> context.getString(R.string.yearly)
                    else -> toCamel(r.rateBasis)
                }
                val values = listOf(
                    r.dateStr,
                    CurrencyFormatter.format(r.principal),
                    "${r.rate}% $rowBasis",
                    CurrencyFormatter.format(r.accrued),
                    CurrencyFormatter.format(r.total),
                )
                for (i in cols.indices) {
                    val cellWidth = cols[i].width
                    val value = values[i]
                    val drawX = when (i) {
                        1, 3, 4 -> { // right-align numeric columns
                            val w = text.measureText(value)
                            x + cellWidth - 4f - w
                        }
                        else -> x + 4f
                    }
                    canvas.drawText(value, drawX, y, text)
                    x += cellWidth + COL_GAP
                }
                // Leave ample space after text, then draw row separator line
                y += 12f
                canvas.drawLine(startX, y, startX + tableWidth, y, rule)
                y += 12f
            }

            // Subtotal single row aligned with columns
            val principalValue = rows.sumOf { it.principal }
            val interestValue = rows.sumOf { it.accrued }
            val totalValue = rows.sumOf { it.total }
            ensureSpace(30f)
            // Do not draw another line here to avoid double-line; last row separator already drawn
            // Keep spacing consistent with a normal row gap (12f)
            y += 12f
            // Draw values per column
            x = startX
            val subtotalValues = listOf(
                context.getString(R.string.label_subtotal), // under Date column as label
                CurrencyFormatter.format(principalValue),
                "", // Rate column left blank
                CurrencyFormatter.format(interestValue),
                CurrencyFormatter.format(totalValue)
            )
            for (i in cols.indices) {
                val cellWidth = cols[i].width
                val value = subtotalValues[i]
                val drawX = when (i) {
                    1, 3, 4 -> {
                        val w = header.measureText(value)
                        x + cellWidth - 4f - w
                    }
                    else -> x + 4f
                }
                canvas.drawText(value, drawX, y, header)
                x += cellWidth + COL_GAP
            }
            // Bottom line after subtotal row
            y += 12f
            canvas.drawLine(startX, y, startX + tableWidth, y, rule)
            y += 8f
        }

        drawTable(context.getString(R.string.lend), lend)
        // extra separation between sections
        y += 24f
        drawTable(context.getString(R.string.borrow), borrow)

        if (includePromo) {
            // Footer note with app link for free users
            ensureSpace(24f)
            y += 6f
            lineL(context.getString(R.string.generated_by_footer_with_link, "https://play.google.com/store/apps/details?id=com.ledge.ledgerbook"), text, 16f)
        }

        doc.finishPage(page)
        val outFile = File(context.cacheDir, "receipt_group_${name}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()

        shareFile(context, outFile, "Receipt - $name")
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
