package com.ledge.ledgerbook.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.ledge.ledgerbook.util.LedgerInterest
import com.ledge.ledgerbook.ui.LedgerViewModel
import com.ledge.ledgerbook.util.InterestRateFormatter
import com.ledge.ledgerbook.util.CurrencyFormatter
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
        val title = Paint().apply { textSize = 18f; isFakeBoldText = true; color = Color.BLACK }
        val header = Paint().apply { textSize = 12f; isFakeBoldText = true; color = Color.BLACK }
        val text = Paint().apply { textSize = 12f; color = Color.BLACK }
        val border = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1.2f; color = Color.parseColor("#E0E0E0") }
        val rule = Paint().apply { strokeWidth = 1f; color = Color.parseColor("#E0E0E0") }
        var y = MARGIN.toFloat()

        fun newPage() {
            doc.finishPage(page)
            pageNumber += 1
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = MARGIN.toFloat()
        }

        fun lineL(t: String, p: Paint = text, gap: Float = 18f) { canvas.drawText(t, MARGIN.toFloat(), y, p); y += gap }

        // Title
        lineL(context.getString(R.string.pdf_title_receipt), title, 24f)
        lineL(context.getString(R.string.label_date_with_value, dateFmt.format(Date())), text)
        y += 6f
        lineL(context.getString(R.string.label_customer_with_value, item.name), header)
        y += 6f

        // Table columns for single entry
        data class Col(val title: String, val width: Float)
        val cols = listOf(
            Col(context.getString(R.string.entry_details), 120f),
            Col(context.getString(R.string.amount), 120f),
        )

        // Draw table without header row
        val startX = MARGIN.toFloat()
        val tableWidth = cols.sumOf { it.width.toDouble() }.toFloat() + COL_GAP * (cols.size - 1)
        y += 12f

        // Details data rows
        val (years, months, days) = LedgerInterest.durationBetween(item.fromDateMillis, System.currentTimeMillis())
        val duration = buildString {
                if (years > 0) append("${years} ${context.getString(R.string.year_singular)} ")
                if (months > 0) append("${months} ${context.getString(R.string.month_singular)} ")
                append("${days} ${context.getString(R.string.day_singular)}")
            }.trim()
        val details = listOf(
            context.getString(R.string.label_type) to when (item.type?.uppercase()) {
                "LEND" -> context.getString(R.string.lend)
                "BORROW" -> context.getString(R.string.borrow)
                else -> item.type?.let { toCamel(it) } ?: ""
            },
            context.getString(R.string.label_principal) to CurrencyFormatter.format(item.principal),
            context.getString(R.string.label_rate) to InterestRateFormatter.format(item.rate, item.rateBasis),
            context.getString(R.string.label_from) to item.dateStr,
            context.getString(R.string.duration) to duration,
            context.getString(R.string.label_interest) to CurrencyFormatter.format(item.accrued),
            context.getString(R.string.label_total) to CurrencyFormatter.format(item.total)
        )

        // Draw data rows with alternating colors
        details.forEachIndexed { index, (key, value) ->
            var x = startX
            val rowHeight = 25f
            val isTotalRow = key == context.getString(R.string.label_total)
            val isTypeRow = key == context.getString(R.string.label_type)
            val isLend = item.type == "LEND"
            
            val rowBgColor = when {
                isTotalRow -> if (isLend) Color.parseColor("#E8F5E8") else Color.parseColor("#FFEBEE") // Green for lend, red for borrow (same as type row)
                isTypeRow -> if (isLend) Color.parseColor("#E8F5E8") else Color.parseColor("#FFEBEE") // Green for lend, red for borrow
                index % 2 == 0 -> Color.parseColor("#FAFAFA")
                else -> Color.WHITE
            }
            val rowBg = Paint().apply { color = rowBgColor }
            
            // Draw row background and border
            canvas.drawRect(startX, y, startX + tableWidth, y + rowHeight, rowBg)
            canvas.drawRect(startX, y, startX + tableWidth, y + rowHeight, border)
            
            // Choose text style and color based on row type
            val textStyle = if (isTotalRow) header else text
            val textColor = when {
                isTotalRow -> if (isLend) Color.parseColor("#2E7D32") else Color.parseColor("#C62828") // Dark green for lend, dark red for borrow (same as type row)
                isTypeRow -> if (isLend) Color.parseColor("#2E7D32") else Color.parseColor("#C62828") // Dark green for lend, dark red for borrow
                else -> Color.BLACK
            }
            textStyle.color = textColor
            
            // Draw field name
            canvas.drawText(key, x + 4f, y + rowHeight / 2 + 4f, textStyle)
            x += cols[0].width + COL_GAP
            
            // Draw value (right-aligned)
            val drawX = x + cols[1].width - 4f - textStyle.measureText(value)
            canvas.drawText(value, drawX, y + rowHeight / 2 + 4f, textStyle)
            
            y += rowHeight
        }

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
        val title = Paint().apply { textSize = 18f; isFakeBoldText = true; color = Color.BLACK }
        val header = Paint().apply { textSize = 12f; isFakeBoldText = true; color = Color.BLACK }
        val text = Paint().apply { textSize = 12f; color = Color.BLACK }
        val border = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1.2f; color = Color.parseColor("#E0E0E0") }
        val rule = Paint().apply { strokeWidth = 1f; color = Color.parseColor("#E0E0E0") }
        val lendHeaderBg = Paint().apply { color = Color.parseColor("#4CAF50") } // Green for lend
        val borrowHeaderBg = Paint().apply { color = Color.parseColor("#F44336") } // Red for borrow
        val headerText = Paint().apply { textSize = 12f; isFakeBoldText = true; color = Color.WHITE }
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

        fun drawTable(sectionTitle: String, rows: List<LedgerViewModel.LedgerItemVM>, isLendTable: Boolean) {
            if (rows.isEmpty()) return
            ensureSpace(80f)
            
            val startX = MARGIN.toFloat()
            val tableWidth = cols.sumOf { it.width.toDouble() }.toFloat() + COL_GAP * (cols.size - 1)
            
            // Section title with colorful header background
            val headerHeight = 35f
            val headerBg = if (isLendTable) lendHeaderBg else borrowHeaderBg
            canvas.drawRect(startX, y, startX + tableWidth, y + headerHeight, headerBg)
            
            // Draw section title in white
            val titleWidth = headerText.measureText(sectionTitle)
            canvas.drawText(sectionTitle, startX + (tableWidth - titleWidth) / 2, y + headerHeight / 2 + 6f, headerText)
            y += headerHeight + 15f
            
            // Header row with background and borders
            var x = startX
            val headerRowHeight = 30f
            
            // Draw header background
            val headerRowBg = Paint().apply { color = Color.parseColor("#F5F5F5") }
            canvas.drawRect(startX, y, startX + tableWidth, y + headerRowHeight, headerRowBg)
            canvas.drawRect(startX, y, startX + tableWidth, y + headerRowHeight, border)
            
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
                canvas.drawText(titleText, drawX, y + headerRowHeight / 2 + 4f, header)
                // Draw vertical lines
                if (i < cols.size - 1) {
                    canvas.drawRect(x + cellWidth + COL_GAP / 2 - 0.5f, y, x + cellWidth + COL_GAP / 2 + 0.5f, y + headerRowHeight, border)
                }
                x += cellWidth + COL_GAP
            }
            
            y += headerRowHeight
            canvas.drawLine(startX, y, startX + tableWidth, y, rule)
            y += 12f

            // Body rows with alternating colors and borders
            rows.forEachIndexed { rowIndex, r ->
                ensureSpace(30f)
                x = startX
                val rowHeight = 25f
                val rowBgColor = if (rowIndex % 2 == 0) Color.parseColor("#FAFAFA") else Color.WHITE
                val rowBg = Paint().apply { color = rowBgColor }
                
                // Draw row background and border
                canvas.drawRect(startX, y, startX + tableWidth, y + rowHeight, rowBg)
                canvas.drawRect(startX, y, startX + tableWidth, y + rowHeight, border)
                
                val values = listOf(
                    r.dateStr,
                    CurrencyFormatter.format(r.principal),
                    InterestRateFormatter.format(r.rate, r.rateBasis),
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
                    canvas.drawText(value, drawX, y + rowHeight / 2 + 4f, text)
                    // Draw vertical lines
                    if (i < cols.size - 1) {
                        canvas.drawRect(x + cellWidth + COL_GAP / 2 - 0.5f, y, x + cellWidth + COL_GAP / 2 + 0.5f, y + rowHeight, border)
                    }
                    x += cellWidth + COL_GAP
                }
                y += rowHeight
            }

            // Subtotal row with special styling
            val principalValue = rows.sumOf { it.principal }
            val interestValue = rows.sumOf { it.accrued }
            val totalValue = rows.sumOf { it.total }
            ensureSpace(40f)
            y += 12f
            
            // Draw subtotal background
            val subtotalBg = Paint().apply { color = Color.parseColor("#E8F5E8") }
            val subtotalHeight = 25f
            canvas.drawRect(startX, y, startX + tableWidth, y + subtotalHeight, subtotalBg)
            canvas.drawRect(startX, y, startX + tableWidth, y + subtotalHeight, border)
            
            // Draw subtotal values
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
                canvas.drawText(value, drawX, y + subtotalHeight / 2 + 4f, header)
                if (i < cols.size - 1) {
                    canvas.drawRect(x + cellWidth + COL_GAP / 2 - 0.5f, y, x + cellWidth + COL_GAP / 2 + 0.5f, y + subtotalHeight, border)
                }
                x += cellWidth + COL_GAP
            }
            y += subtotalHeight
            canvas.drawLine(startX, y, startX + tableWidth, y, rule)
            y += 8f
        }

        drawTable(context.getString(R.string.lend), lend, true)
        // extra separation between sections
        y += 24f
        drawTable(context.getString(R.string.borrow), borrow, false)

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
