package com.ledge.splitbook.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.ledge.splitbook.domain.SettlementLogic
import com.ledge.splitbook.data.entity.MemberEntity
import com.ledge.splitbook.data.entity.ExpenseEntity
import org.dhatim.fastexcel.Workbook
import org.dhatim.fastexcel.BorderStyle
import org.dhatim.fastexcel.BorderSide
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.ledge.splitbook.util.formatAmount

data class MemberSummary(
    val memberId: Long,
    val amountPaid: Double,
    val expenseShared: Double,
    val dueAmount: Double
)

object ShareExport {

    fun buildTextSummary(
        groupName: String,
        members: List<MemberEntity>,
        expenses: List<ExpenseEntity>,
        memberSummaries: List<MemberSummary>,
        currency: String
    ): String {
        val byId = members.associateBy { it.id }
        val sb = StringBuilder()
        sb.appendLine("Simple Split — $groupName")
        sb.appendLine()
        // 1) Transactions list
        sb.appendLine("Transactions:")
        sb.appendLine("Date | Paid By | Category | Description | Amount")
        var total = 0.0
        expenses.forEach { e ->
            val date = e.createdAt ?: "—"
            val payer = byId[e.paidByMemberId]?.name ?: "—"
            val notes = e.note ?: ""
            total += e.amount
            sb.appendLine("$date | $payer | ${e.category} | ${notes} | ${formatAmount(e.amount, currency)}")
        }
        sb.appendLine("Total: ${formatAmount(total, currency)}")
        sb.appendLine()
        // 2) Members details
        sb.appendLine("Members:")
        sb.appendLine("Member | Amount Paid | Expense Shared | Due Amount")
        memberSummaries.forEach { ms ->
            val name = byId[ms.memberId]?.name ?: "—"
            sb.appendLine("$name | ${formatAmount(ms.amountPaid, currency)} | ${formatAmount(ms.expenseShared, currency)} | ${formatAmount(ms.dueAmount, currency)}")
        }
        return sb.toString()
    }

    fun shareText(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share summary"))
    }

    fun exportPdf(
        context: Context,
        groupName: String,
        members: List<MemberEntity>,
        expenses: List<ExpenseEntity>,
        memberSummaries: List<MemberSummary>,
        currency: String
    ): Uri {
        val df = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
        val fileName = "simple_split_${groupName}_${LocalDateTime.now().format(df)}.pdf"
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, fileName)

        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4-ish in points
        val page = doc.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        val paint = Paint().apply {
            color = 0xFF000000.toInt()
            textSize = 14f
            typeface = Typeface.MONOSPACE
        }
        val margin = 40f
        var y = 40f
        val byId = members.associateBy { it.id }
        fun newPageWithTitle(title: String) {
            doc.finishPage(page)
        }
        fun ensureSpace(rowHeight: Float, headerHeight: Float, headerDrawer: (() -> Unit)? = null) {
            val bottom = pageInfo.pageHeight - margin
            if (y + rowHeight > bottom) {
                doc.finishPage(page)
                val newPage = doc.startPage(pageInfo)
                val newCanvas = newPage.canvas
                // switch references
                // We cannot reassign 'canvas' val; so keep rows short to avoid overflow in MVP.
            }
        }
        fun drawTitle(text: String) {
            paint.isFakeBoldText = true
            canvas.drawText(text, margin, y, paint)
            paint.isFakeBoldText = false
            y += 24f
        }
        fun drawTable(headers: List<String>, rows: List<List<String>>, widths: List<Float>) {
            val left = margin
            val right = pageInfo.pageWidth - margin
            val totalWidth = right - left
            val colXs = mutableListOf<Float>()
            var acc = left
            widths.forEach { w ->
                colXs.add(acc)
                acc += totalWidth * w
            }
            colXs.add(right)
            val headerBg = Paint().apply { color = 0xFF6B46C1.toInt() }
            val headerText = Paint(paint).apply { color = 0xFFFFFFFF.toInt(); isFakeBoldText = true }
            val cellStroke = Paint().apply { color = 0x33000000; strokeWidth = 1f; style = Paint.Style.STROKE }
            val cellText = Paint(paint)
            val rowH = 22f
            // Header row
            val topH = y
            canvas.drawRect(left, topH - 16f, right, topH + 8f, headerBg)
            headers.forEachIndexed { i, h ->
                val tx = colXs[i] + 6f
                canvas.drawText(h, tx, topH, headerText)
                // vertical lines
                canvas.drawLine(colXs[i], topH - 16f, colXs[i], topH + rowH, cellStroke)
            }
            canvas.drawLine(right, topH - 16f, right, topH + rowH, cellStroke)
            canvas.drawLine(left, topH + rowH, right, topH + rowH, cellStroke)
            y += rowH
            // Data rows
            rows.forEach { r ->
                val top = y
                r.forEachIndexed { i, v ->
                    val tx = colXs[i] + 6f
                    canvas.drawText(v, tx, top, cellText)
                    canvas.drawLine(colXs[i], top - 16f, colXs[i], top + rowH, cellStroke)
                }
                canvas.drawLine(right, top - 16f, right, top + rowH, cellStroke)
                canvas.drawLine(left, top + rowH, right, top + rowH, cellStroke)
                y += rowH
            }
        }
        // Title
        drawTitle("Simple Split — $groupName")
        // Transactions table
        val txHeaders = listOf("Date", "Paid By", "Category", "Description", "Amount")
        var total = 0.0
        val txRows = expenses.map { e ->
            total += e.amount
            listOf(
                e.createdAt ?: "—",
                byId[e.paidByMemberId]?.name ?: "—",
                e.category,
                e.note ?: "",
                formatAmount(e.amount, currency)
            )
        }
        drawTable(txHeaders, txRows, listOf(0.18f, 0.22f, 0.18f, 0.28f, 0.14f))
        // Total row
        val totalBg = Paint().apply { color = 0xFF6B46C1.toInt() }
        val totalText = Paint(paint).apply { color = 0xFFFFFFFF.toInt(); isFakeBoldText = true }
        val left = margin
        val right = pageInfo.pageWidth - margin
        val totalTop = y
        val rowH = 22f
        canvas.drawRect(left, totalTop - 16f, right, totalTop + 8f, totalBg)
        canvas.drawText("Total:", left + 6f, totalTop, totalText)
        canvas.drawText(formatAmount(total, currency), right - 120f, totalTop, totalText)
        y += rowH + 10f
        // Members table
        val msHeaders = listOf("Member", "Amount Paid", "Deposit", "Expense Shared", "Due Amount")
        val msRows = memberSummaries.map { ms ->
            val deposit = byId[ms.memberId]?.deposit ?: 0.0
            listOf(
                byId[ms.memberId]?.name ?: "—",
                formatAmount(ms.amountPaid, currency),
                formatAmount(deposit, currency),
                formatAmount(ms.expenseShared, currency),
                formatAmount(ms.dueAmount, currency)
            )
        }
        // Adjust widths to accommodate the extra Deposit column
        drawTable(msHeaders, msRows, listOf(0.26f, 0.18f, 0.14f, 0.22f, 0.20f))
        doc.finishPage(page)
        FileOutputStream(file).use { out -> doc.writeTo(out) }
        doc.close()

        return FileProvider.getUriForFile(context, "com.ledge.splitbook.fileprovider", file)
    }

    fun exportExcel(
        context: Context,
        groupName: String,
        members: List<MemberEntity>,
        expenses: List<ExpenseEntity>,
        memberSummaries: List<MemberSummary>,
        currency: String
    ): Uri {
        val df = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
        val fileName = "simple_split_${groupName}_${LocalDateTime.now().format(df)}.xlsx"
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, fileName)

        FileOutputStream(file).use { fos ->
            val wb = Workbook(fos, "Simple Split", "1.0")
            // Transactions sheet
            val tx = wb.newWorksheet("Transactions")
            tx.value(1, 1, "Date")
            tx.value(1, 2, "Paid By")
            tx.value(1, 3, "Category")
            tx.value(1, 4, "Description")
            tx.value(1, 5, "Amount")
            val byId = members.associateBy { it.id }
            var total = 0.0
            expenses.forEachIndexed { idx, e ->
                val row = idx + 2
                tx.value(row, 1, e.createdAt ?: "")
                tx.value(row, 2, byId[e.paidByMemberId]?.name ?: "")
                tx.value(row, 3, e.category)
                tx.value(row, 4, e.note ?: "")
                tx.value(row, 5, e.amount)
                total += e.amount
            }
            val totalRow = expenses.size + 3
            tx.value(totalRow, 4, "Total")
            tx.value(totalRow, 5, total)
            // Style as table: borders, header fill/bold, total row highlight
            val lastDataRow = totalRow - 1
            tx.range(1, 1, lastDataRow, 5).style()
                .borderStyle(BorderSide.TOP, BorderStyle.THIN)
                .borderStyle(BorderSide.BOTTOM, BorderStyle.THIN)
                .borderStyle(BorderSide.LEFT, BorderStyle.THIN)
                .borderStyle(BorderSide.RIGHT, BorderStyle.THIN)
                .set()
            tx.range(1, 1, 1, 5).style()
                .bold()
                .fillColor("#6B46C1")
                .fontColor("#FFFFFF")
                .set()
            tx.range(totalRow, 4, totalRow, 5).style()
                .bold()
                .fillColor("#6B46C1")
                .fontColor("#FFFFFF")
                .borderStyle(BorderSide.TOP, BorderStyle.THIN)
                .borderStyle(BorderSide.BOTTOM, BorderStyle.THIN)
                .borderStyle(BorderSide.LEFT, BorderStyle.THIN)
                .borderStyle(BorderSide.RIGHT, BorderStyle.THIN)
                .set()
            // Optional: set reasonable column widths
            tx.width(1, 14.0)
            tx.width(2, 18.0)
            tx.width(3, 16.0)
            tx.width(4, 28.0)
            tx.width(5, 12.0)

            // Members sheet
            val ms = wb.newWorksheet("Members")
            ms.value(1, 1, "Member")
            ms.value(1, 2, "Amount Paid")
            ms.value(1, 3, "Deposit")
            ms.value(1, 4, "Expense Shared")
            ms.value(1, 5, "Due Amount")
            memberSummaries.forEachIndexed { idx, s ->
                val row = idx + 2
                ms.value(row, 1, byId[s.memberId]?.name ?: "")
                ms.value(row, 2, s.amountPaid)
                ms.value(row, 3, byId[s.memberId]?.deposit ?: 0.0)
                ms.value(row, 4, s.expenseShared)
                ms.value(row, 5, s.dueAmount)
            }
            val msLastRow = memberSummaries.size + 1
            ms.range(1, 1, msLastRow, 5).style()
                .borderStyle(BorderSide.TOP, BorderStyle.THIN)
                .borderStyle(BorderSide.BOTTOM, BorderStyle.THIN)
                .borderStyle(BorderSide.LEFT, BorderStyle.THIN)
                .borderStyle(BorderSide.RIGHT, BorderStyle.THIN)
                .set()
            ms.range(1, 1, 1, 5).style()
                .bold()
                .fillColor("#6B46C1")
                .fontColor("#FFFFFF")
                .set()
            ms.width(1, 22.0)
            ms.width(2, 16.0)
            ms.width(3, 12.0)
            ms.width(4, 18.0)
            ms.width(5, 16.0)
            wb.finish()
        }

        return FileProvider.getUriForFile(context, "com.ledge.splitbook.fileprovider", file)
    }

    fun shareFile(context: Context, uri: Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share export"))
    }
}
