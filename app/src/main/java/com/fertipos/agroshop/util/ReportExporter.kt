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

object ReportExporter {
    private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    fun exportCustomerCsv(
        context: Context,
        authority: String,
        customerName: String,
        rows: List<InvoiceDao.ExportRow>
    ): Uri {
        val outFile = File(context.cacheDir, "report_${sanitize(customerName)}_${System.currentTimeMillis()}.csv")
        writeCustomerCsv(context, outFile, customerName, rows)
        return FileProvider.getUriForFile(context, authority, outFile)
    }

    fun exportPLCsv(
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
        val outFile = File(context.cacheDir, "pl_${System.currentTimeMillis()}.csv")
        FileOutputStream(outFile).use { fos ->
            // Write UTF-8 BOM to improve Excel compatibility for non-English text
            fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            val lines = mutableListOf<String>()
            lines += listOf(context.getString(R.string.csv_from), context.getString(R.string.csv_to)).joinToString(",") + "\n"
            lines += listOf(
                escape(dateFmt.format(Date(from))),
                escape(dateFmt.format(Date(to)))
            ).joinToString(",") + "\n\n"

            lines += listOf(
                context.getString(R.string.csv_section),
                context.getString(R.string.csv_subtotal),
                context.getString(R.string.csv_gst),
                context.getString(R.string.csv_total)
            ).joinToString(",") + "\n"
            lines += listOf(
                context.getString(R.string.csv_sales),
                salesSubtotal.toString(), salesGst.toString(), salesTotal.toString()
            ).joinToString(",") + "\n"
            lines += listOf(
                context.getString(R.string.csv_purchases),
                purchasesSubtotal.toString(), purchasesGst.toString(), purchasesTotal.toString()
            ).joinToString(",") + "\n\n"

            lines += listOf(context.getString(R.string.csv_metric), context.getString(R.string.net_amount_label)).joinToString(",") + "\n"
            lines += listOf(context.getString(R.string.csv_gross_profit_metric), grossProfit.toString()).joinToString(",") + "\n"
            lines += listOf(context.getString(R.string.csv_net_amount_metric), netAmount.toString()).joinToString(",") + "\n"

            lines.forEach { fos.write(it.toByteArray(StandardCharsets.UTF_8)) }
        }
        return FileProvider.getUriForFile(context, authority, outFile)
    }

    fun exportAllCsv(
        context: Context,
        authority: String,
        rows: List<InvoiceDao.ExportRowAll>
    ): Uri {
        val outFile = File(context.cacheDir, "report_ALL_${System.currentTimeMillis()}.csv")
        writeAllCsv(context, outFile, rows)
        return FileProvider.getUriForFile(context, authority, outFile)
    }

    private fun writeCustomerCsv(context: Context, file: File, customerName: String, rows: List<InvoiceDao.ExportRow>) {
        val header = listOf(
            context.getString(R.string.csv_customer),
            context.getString(R.string.csv_invoice_no),
            context.getString(R.string.csv_date),
            context.getString(R.string.csv_product),
            context.getString(R.string.csv_qty),
            context.getString(R.string.csv_unit_price),
            context.getString(R.string.csv_gst_percent),
            context.getString(R.string.csv_line_total)
        )
        var total = 0.0
        var totalQty = 0.0
        FileOutputStream(file).use { fos ->
            // Write UTF-8 BOM to improve Excel compatibility for non-English text
            fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            fos.write((header.joinToString(",") + "\n").toByteArray(StandardCharsets.UTF_8))
            rows.forEach { r ->
                total += r.lineTotal
                totalQty += r.quantity
                val row = listOf(
                    escape(customerName),
                    r.invoiceId.toString(),
                    escape(dateFmt.format(Date(r.date))),
                    escape(r.productName ?: ""),
                    r.quantity.toString(),
                    r.unitPrice.toString(),
                    r.gstPercent.toString(),
                    r.lineTotal.toString()
                ).joinToString(",")
                fos.write((row + "\n").toByteArray(StandardCharsets.UTF_8))
            }
            // Summary line
            val sumLine = listOf(context.getString(R.string.csv_subtotal_summary), "", "", "", totalQty.toString(), "", "", total.toString()).joinToString(",")
            fos.write((sumLine + "\n").toByteArray(StandardCharsets.UTF_8))
        }
    }

    private fun writeAllCsv(context: Context, file: File, rows: List<InvoiceDao.ExportRowAll>) {
        val header = listOf(
            context.getString(R.string.csv_customer),
            context.getString(R.string.csv_invoice_no),
            context.getString(R.string.csv_date),
            context.getString(R.string.csv_product),
            context.getString(R.string.csv_qty),
            context.getString(R.string.csv_unit_price),
            context.getString(R.string.csv_gst_percent),
            context.getString(R.string.csv_line_total)
        )
        var total = 0.0
        var totalQty = 0.0
        FileOutputStream(file).use { fos ->
            // Write UTF-8 BOM to improve Excel compatibility for non-English text
            fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            fos.write((header.joinToString(",") + "\n").toByteArray(StandardCharsets.UTF_8))
            rows.forEach { r ->
                total += r.lineTotal
                totalQty += r.quantity
                val row = listOf(
                    escape(r.customerName ?: ""),
                    r.invoiceId.toString(),
                    escape(dateFmt.format(Date(r.date))),
                    escape(r.productName ?: ""),
                    r.quantity.toString(),
                    r.unitPrice.toString(),
                    r.gstPercent.toString(),
                    r.lineTotal.toString()
                ).joinToString(",")
                fos.write((row + "\n").toByteArray(StandardCharsets.UTF_8))
            }
            val sumLine = listOf(context.getString(R.string.csv_grand_total), "", "", "", totalQty.toString(), "", "", total.toString()).joinToString(",")
            fos.write((sumLine + "\n").toByteArray(StandardCharsets.UTF_8))
        }
    }

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
