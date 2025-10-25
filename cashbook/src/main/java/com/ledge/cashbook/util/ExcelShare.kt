package com.ledge.cashbook.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.ledge.cashbook.R
import com.ledge.cashbook.data.local.entities.CashTxn
import org.dhatim.fastexcel.Workbook
import org.dhatim.fastexcel.Worksheet
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.dhatim.fastexcel.BorderSide

object ExcelShare {
    private val dateFmt = SimpleDateFormat("dd/MM/yy", Locale.getDefault())

    fun exportAccountXlsx(
        context: Context,
        accountName: String,
        txns: List<CashTxn>,
        startMillis: Long? = null,
        endMillis: Long? = null,
        showCategory: Boolean = false
    ) {
        val safeName = accountName.replace("[^A-Za-z0-9_]".toRegex(), "_")
        val outFile = File(context.cacheDir, "cashbook_${safeName}_${System.currentTimeMillis()}.xlsx")
        FileOutputStream(outFile).use { fos ->
            val wb = Workbook(fos, "SimpleCashBook", "1.0")
            val sheet: Worksheet = wb.newWorksheet(context.getString(R.string.title_cash_book))

            var row = 0
            fun write(r: Int, c: Int, value: String) { sheet.value(r, c, value) }
            fun writeNum(r: Int, c: Int, value: Double) { sheet.value(r, c, value) }

            // Header info block (to be boxed & bold)
            write(row++, 0, context.getString(R.string.title_cash_book))
            write(row++, 0, context.getString(R.string.label_customer_with_value, accountName))
            write(row++, 0, context.getString(R.string.label_date_with_value, SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())))
            val headerStartRow = 0
            if (startMillis != null || endMillis != null) {
                val fmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                val parts = mutableListOf<String>()
                startMillis?.let { parts.add(context.getString(R.string.from_label, fmt.format(Date(it)))) }
                endMillis?.let { parts.add(context.getString(R.string.to_label, fmt.format(Date(it)))) }
                if (parts.isNotEmpty()) write(row++, 0, parts.joinToString("    "))
            }
            // Total balance as part of header block
            val totalCredit = txns.filter { it.isCredit }.sumOf { it.amount }
            val totalDebit = txns.filter { !it.isCredit }.sumOf { it.amount }
            val closingBalance = totalCredit - totalDebit
            write(row++, 0, context.getString(R.string.label_total_balance_with_value, Currency.inr(closingBalance)))
            val headerEndRow = row - 1

            // Determine table width (needed to span header card across)
            val includeCategory = showCategory
            val headers = buildList {
                add(context.getString(R.string.col_date))
                add(context.getString(R.string.col_particular))
                if (includeCategory) add(context.getString(R.string.category))
                add(context.getString(R.string.col_credit))
                add(context.getString(R.string.col_debit))
                add(context.getString(R.string.col_balance))
            }
            val headerEndCol = headers.size - 1

            // Merge each header row across columns A..last
            for (r in headerStartRow..headerEndRow) {
                sheet.range(r, 0, r, headerEndCol).merge()
            }
            // Fill + bold entire header block
            sheet.range(headerStartRow, 0, headerEndRow, headerEndCol)
                .style()
                .bold()
                .fillColor("FFF9C4")
                .set()
            // Note: No borders on header block as requested; only merged highlight with bold text

            // Blank row to separate header card and table
            row++
            headers.forEachIndexed { i, h -> write(row, i, h) }
            // Style header: bold, center, light blue fill, thin borders
            val headerRow = row
            val lastHeaderCol = headers.size - 1
            sheet.range(headerRow, 0, headerRow, lastHeaderCol)
                .style()
                .bold()
                .horizontalAlignment("center")
                .fillColor("D9EAF7")
                .borderStyle("thin")
                .set()
            row++

            // Rows
            var running = 0.0
            val dataStartRow = row
            txns.forEach { t ->
                running += if (t.isCredit) t.amount else -t.amount
                var col = 0
                write(row, col++, dateFmt.format(Date(t.date)))
                write(row, col++, t.note ?: "-")
                if (includeCategory) write(row, col++, t.category ?: "-")
                if (t.isCredit) {
                    writeNum(row, col++, t.amount)
                    write(row, col++, "-")
                } else {
                    write(row, col++, "-")
                    writeNum(row, col++, t.amount)
                }
                writeNum(row, col, running)
                row++
            }
            val dataEndRow = row - 1
            if (dataEndRow >= dataStartRow) {
                // Add thin borders around all transaction cells
                val lastDataCol = headers.size - 1
                sheet.range(dataStartRow, 0, dataEndRow, lastDataCol)
                    .style()
                    .borderStyle("thin")
                    .set()
            }

            // Summary
            val summaryRow = row
            var sCol = if (includeCategory) 2 else 1
            write(row, sCol++, context.getString(R.string.total))
            writeNum(row, sCol++, totalCredit)
            writeNum(row, sCol++, totalDebit)
            writeNum(row, sCol, running)
            // Style summary: bold labels, boxed with borders, light yellow fill
            val summaryStartCol = if (includeCategory) 2 else 1
            val lastSummaryCol = if (includeCategory) 5 else 4
            sheet.range(summaryRow, summaryStartCol, summaryRow, lastSummaryCol)
                .style()
                .bold()
                .fillColor("FFF9C4")
                .borderStyle("thin")
                .set()

            wb.finish()
        }

        shareFile(context, outFile, context.getString(R.string.title_cash_book))
    }

    private fun shareFile(context: Context, file: File, title: String) {
        val authority = context.packageName + ".fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }
}
