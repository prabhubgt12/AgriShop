package com.fertipos.agroshop.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.fertipos.agroshop.data.local.dao.InvoiceDao
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.fertipos.agroshop.R
import org.dhatim.fastexcel.Workbook
import org.dhatim.fastexcel.Worksheet

object ReportExporter {
    private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    fun exportCustomerExcel(
        context: Context,
        authority: String,
        customerName: String,
        rows: List<InvoiceDao.ExportRow>
    ): Uri {
        val safe = sanitize(customerName)
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val outFile = File(context.cacheDir, "report_${safe}_${ts}.xlsx")
        FileOutputStream(outFile).use { fos ->
            val wb = Workbook(fos, "SimpleShop", "1.0")
            val sheet: Worksheet = wb.newWorksheet(context.getString(R.string.customer_title))

            var r = 0
            fun w(row:Int,col:Int,v:String) = sheet.value(row, col, v)
            fun wn(row:Int,col:Int,v:Double) = sheet.value(row, col, v)

            // Header block
            w(r++, 0, context.getString(R.string.customer_title))
            w(r++, 0, context.getString(R.string.csv_customer) + ": " + customerName)

            val headerStart = 0
            val headerEnd = r - 1
            val headers = listOf(
                context.getString(R.string.csv_customer),
                context.getString(R.string.csv_invoice_no),
                context.getString(R.string.csv_date),
                context.getString(R.string.csv_product),
                context.getString(R.string.csv_qty),
                context.getString(R.string.csv_unit_price),
                context.getString(R.string.csv_gst_percent),
                context.getString(R.string.csv_line_total)
            )
            val lastCol = headers.size - 1
            for (rr in headerStart..headerEnd) sheet.range(rr, 0, rr, lastCol).merge()
            sheet.range(headerStart, 0, headerEnd, lastCol).style().bold().fillColor("FFF9C4").set()

            // Blank
            r++
            headers.forEachIndexed { i, h -> w(r, i, h) }
            val tableHeaderRow = r
            sheet.range(tableHeaderRow, 0, tableHeaderRow, lastCol).style().bold().horizontalAlignment("center").fillColor("D9EAF7").borderStyle("thin").set()
            r++

            var total = 0.0
            var totalQty = 0.0
            val dataStart = r
            rows.forEach { x ->
                var c = 0
                w(r, c++, customerName)
                w(r, c++, x.invoiceId.toString())
                w(r, c++, dateFmt.format(Date(x.date)))
                w(r, c++, x.productName ?: "")
                wn(r, c++, x.quantity)
                wn(r, c++, x.unitPrice)
                wn(r, c++, x.gstPercent)
                wn(r, c, x.lineTotal)
                total += x.lineTotal
                totalQty += x.quantity
                r++
            }
            val dataEnd = r - 1
            if (dataEnd >= dataStart) sheet.range(dataStart, 0, dataEnd, lastCol).style().borderStyle("thin").set()

            // Totals row
            val sumRow = r
            var sc = 0
            w(sumRow, sc++, context.getString(R.string.csv_subtotal_summary))
            w(sumRow, sc++, "")
            w(sumRow, sc++, "")
            w(sumRow, sc++, "")
            wn(sumRow, sc++, totalQty)
            w(sumRow, sc++, "")
            w(sumRow, sc++, "")
            wn(sumRow, sc, total)
            sheet.range(sumRow, 0, sumRow, lastCol).style().bold().fillColor("FFF9C4").borderStyle("thin").set()

            wb.finish()
        }
        return FileProvider.getUriForFile(context, authority, outFile)
    }

    fun exportAllExcel(
        context: Context,
        authority: String,
        rows: List<InvoiceDao.ExportRowAll>
    ): Uri {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val outFile = File(context.cacheDir, "report_ALL_${ts}.xlsx")
        FileOutputStream(outFile).use { fos ->
            val wb = Workbook(fos, "SimpleShop", "1.0")
            val sheet: Worksheet = wb.newWorksheet(context.getString(R.string.reports_title))
            var r = 0
            fun w(row:Int,col:Int,v:String) = sheet.value(row, col, v)
            fun wn(row:Int,col:Int,v:Double) = sheet.value(row, col, v)

            val headers = listOf(
                context.getString(R.string.csv_customer),
                context.getString(R.string.csv_invoice_no),
                context.getString(R.string.csv_date),
                context.getString(R.string.csv_product),
                context.getString(R.string.csv_qty),
                context.getString(R.string.csv_unit_price),
                context.getString(R.string.csv_gst_percent),
                context.getString(R.string.csv_line_total)
            )
            headers.forEachIndexed { i, h -> w(r, i, h) }
            val lastCol = headers.size - 1
            sheet.range(r, 0, r, lastCol).style().bold().horizontalAlignment("center").fillColor("D9EAF7").borderStyle("thin").set()
            r++

            var total = 0.0
            var totalQty = 0.0
            val dataStart = r
            rows.forEach { x ->
                var c = 0
                w(r, c++, x.customerName ?: "")
                w(r, c++, x.invoiceId.toString())
                w(r, c++, dateFmt.format(Date(x.date)))
                w(r, c++, x.productName ?: "")
                wn(r, c++, x.quantity)
                wn(r, c++, x.unitPrice)
                wn(r, c++, x.gstPercent)
                wn(r, c, x.lineTotal)
                total += x.lineTotal
                totalQty += x.quantity
                r++
            }
            val dataEnd = r - 1
            if (dataEnd >= dataStart) sheet.range(dataStart, 0, dataEnd, lastCol).style().borderStyle("thin").set()

            val sumRow = r
            var sc = 0
            w(sumRow, sc++, context.getString(R.string.csv_grand_total))
            w(sumRow, sc++, "")
            w(sumRow, sc++, "")
            w(sumRow, sc++, "")
            wn(sumRow, sc++, totalQty)
            w(sumRow, sc++, "")
            w(sumRow, sc++, "")
            wn(sumRow, sc, total)
            sheet.range(sumRow, 0, sumRow, lastCol).style().bold().fillColor("FFF9C4").borderStyle("thin").set()

            wb.finish()
        }
        return FileProvider.getUriForFile(context, authority, outFile)
    }

    fun exportPLExcel(
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
        netAmount: Double
    ): Uri {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val outFile = File(context.cacheDir, "PL_Report_${ts}.xlsx")
        FileOutputStream(outFile).use { fos ->
            val wb = Workbook(fos, "SimpleShop", "1.0")
            val sheet: Worksheet = wb.newWorksheet("P&L")
            var r = 0
            fun w(row:Int,col:Int,v:String) = sheet.value(row, col, v)
            fun wn(row:Int,col:Int,v:Double) = sheet.value(row, col, v)

            // Title
            val title = context.getString(R.string.pl_title) + " Report"
            w(r, 0, title)
            sheet.range(r, 0, r, 3).merge()
            sheet.range(r, 0, r, 3).style().bold().fillColor("FFF9C4").set()
            r++
            // Date range in one line
            val rangeLine = context.getString(R.string.csv_from) + ": " + dateFmt.format(Date(from)) +
                    "    " + context.getString(R.string.csv_to) + ": " + dateFmt.format(Date(to))
            w(r, 0, rangeLine)
            sheet.range(r, 0, r, 3).merge()
            sheet.range(r, 0, r, 3).style().bold().fillColor("FFF9C4").set()
            r++

            // Section table
            val hdr = listOf(
                context.getString(R.string.csv_section),
                context.getString(R.string.csv_subtotal),
                context.getString(R.string.csv_gst),
                context.getString(R.string.csv_total)
            )
            hdr.forEachIndexed { i, h -> w(r, i, h) }
            sheet.range(r, 0, r, hdr.size - 1).style().bold().horizontalAlignment("center").fillColor("D9EAF7").borderStyle("thin").set()
            r++
            fun row(n:String,a:Double,b:Double,c:Double){
                var cidx = 0
                w(r, cidx++, n)
                wn(r, cidx++, a)
                wn(r, cidx++, b)
                wn(r, cidx, c)
                r++
            }
            row(context.getString(R.string.csv_sales), salesSubtotal, salesGst, salesTotal)
            row(context.getString(R.string.csv_purchases), purchasesSubtotal, purchasesGst, purchasesTotal)

            // Metrics
            r++
            val mh = listOf(context.getString(R.string.csv_metric), context.getString(R.string.net_amount_label))
            mh.forEachIndexed { i, h -> w(r, i, h) }
            sheet.range(r, 0, r, mh.size - 1).style().bold().horizontalAlignment("center").fillColor("D9EAF7").borderStyle("thin").set()
            r++
            var cidx = 0
            w(r, cidx++, context.getString(R.string.csv_gross_profit_metric)); wn(r, cidx, grossProfit); r++
            cidx = 0
            w(r, cidx++, context.getString(R.string.csv_net_amount_metric)); wn(r, cidx, netAmount)
            sheet.range(r-1, 0, r, 1).style().bold().fillColor("FFF9C4").borderStyle("thin").set()

            wb.finish()
        }
        return FileProvider.getUriForFile(context, authority, outFile)
    }

    // CSV/HTML helpers removed; using native .xlsx via FastExcel

    private fun sanitize(name: String): String = name.replace("[^A-Za-z0-9_-]".toRegex(), "_")

    private fun escape(value: String): String {
        // Enclose in quotes if contains comma or quote; escape quotes
        var v = value.replace("\"", "\"\"")
        if (v.contains(',') || v.contains('\n') || v.contains('"')) {
            v = "\"$v\""
        }
        return v
    }
}
