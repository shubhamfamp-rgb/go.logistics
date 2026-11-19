package com.example.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import com.example.core.DateUtil
import com.example.data.BusinessProfile
import com.example.data.RowStatus
import com.example.data.SheetRow
import com.example.data.TransportSheet
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class PdfTextEdit(
    val id: String,
    val pageIndex: Int,
    val xRatio: Float,
    val yRatio: Float,
    val text: String,
    val fontSizeSp: Float = 14f,
    val isRedaction: Boolean = false
)

data class PdfPhotoOverlay(
    val id: String,
    val pageIndex: Int,
    val xRatio: Float,
    val yRatio: Float,
    val widthRatio: Float,
    val bitmap: Bitmap,
    val caption: String
)

data class BillPhotoAttachment(
    val id: String,
    val bitmap: Bitmap,
    val caption: String
)

data class OptimizedImageResult(
    val bitmap: Bitmap,
    val jpegBytes: ByteArray,
    val originalBytes: Int,
    val optimizedBytes: Int
)

object ExportEngine {

    fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    fun exportSheetToCsvFile(
        context: Context,
        sheet: TransportSheet,
        rows: List<SheetRow>
    ): File {
        val safeName = sheet.name.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "transport_sheet" }
        val file = File(context.cacheDir, "${safeName}_${DateUtil.todayIso()}.csv")
        val sb = StringBuilder()
        sb.append("Date,Vehicle No,Party / Route,LR / Bill No,Freight (INR),Expense (INR),Net (INR),Status,Notes,Source\n")
        for (r in rows) {
            val net = r.freightAmount - r.expenseAmount
            sb.append(csvEscape(r.dateStr)).append(',')
                .append(csvEscape(r.vehicleNo)).append(',')
                .append(csvEscape(r.partyOrRoute)).append(',')
                .append(csvEscape(r.billNo)).append(',')
                .append(r.freightAmount).append(',')
                .append(r.expenseAmount).append(',')
                .append(net).append(',')
                .append(csvEscape(RowStatus.fromRaw(r.status).label)).append(',')
                .append(csvEscape(r.notes)).append(',')
                .append(csvEscape(r.sourceLabel)).append('\n')
        }
        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }

    fun parseCsvToRows(context: Context, uri: Uri, sheetId: String): List<SheetRow> {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: return emptyList()
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val startIndex = if (lines.first().lowercase().contains("vehicle") || lines.first().lowercase().contains("date")) 1 else 0
        val result = mutableListOf<SheetRow>()
        for (i in startIndex until lines.size) {
            val cols = parseCsvLine(lines[i])
            if (cols.isEmpty()) continue
            val dateStr = cols.getOrNull(0)?.trim()?.ifBlank { DateUtil.todayIso().toString() } ?: DateUtil.todayIso().toString()
            val vehicleNo = cols.getOrNull(1)?.trim()?.uppercase() ?: ""
            val party = cols.getOrNull(2)?.trim()?.ifBlank { "Imported Trip" } ?: "Imported Trip"
            val billNo = cols.getOrNull(3)?.trim() ?: ""
            val freight = cols.getOrNull(4)?.replace(Regex("[^0-9.]"), "")?.toDoubleOrNull() ?: 0.0
            val expense = cols.getOrNull(5)?.replace(Regex("[^0-9.]"), "")?.toDoubleOrNull() ?: 0.0
            val statusRaw = cols.getOrNull(7) ?: cols.getOrNull(6) ?: "PENDING"
            val status = when {
                statusRaw.contains("paid", true) -> RowStatus.PAID
                statusRaw.contains("transit", true) -> RowStatus.IN_TRANSIT
                statusRaw.contains("bill", true) -> RowStatus.BILLED
                else -> RowStatus.PENDING
            }
            val notes = cols.getOrNull(8)?.trim() ?: ""
            result.add(
                SheetRow(
                    sheetId = sheetId,
                    dateStr = dateStr,
                    vehicleNo = vehicleNo,
                    partyOrRoute = party,
                    billNo = billNo,
                    freightAmount = freight,
                    expenseAmount = expense,
                    status = status.name,
                    notes = notes,
                    sourceLabel = "CSV Import"
                )
            )
        }
        return result
    }

    /**
     * Generates a standards-compliant OpenXML .xlsx workbook openable in Microsoft Excel,
     * Google Sheets, and Apple Numbers without external heavy POI dependencies.
     */
    fun exportSheetToXlsxFile(
        context: Context,
        sheet: TransportSheet,
        rows: List<SheetRow>
    ): File {
        val safeName = sheet.name.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "transport_sheet" }
        val file = File(context.cacheDir, "${safeName}_${DateUtil.todayIso()}.xlsx")

        val strings = mutableListOf<String>()
        val stringIndex = mutableMapOf<String, Int>()
        fun intern(value: String): Int {
            return stringIndex.getOrPut(value) {
                val idx = strings.size
                strings.add(value)
                idx
            }
        }

        val headers = listOf("Date", "Vehicle No", "Party / Route", "LR / Bill", "Freight", "Expense", "Net Margin", "Status", "Notes", "Source")
        val headerIndices = headers.map { intern(it) }

        val sheetRowsXml = StringBuilder()
        sheetRowsXml.append("""<row r="1">""")
        headerIndices.forEachIndexed { colIdx, strIdx ->
            val ref = "${colLetter(colIdx)}1"
            sheetRowsXml.append("""<c r="$ref" t="s" s="1"><v>$strIdx</v></c>""")
        }
        sheetRowsXml.append("</row>")

        rows.forEachIndexed { idx, row ->
            val rNum = idx + 2
            val net = row.freightAmount - row.expenseAmount
            sheetRowsXml.append("""<row r="$rNum">""")
            sheetRowsXml.append("""<c r="A$rNum" t="s"><v>${intern(row.dateStr)}</v></c>""")
            sheetRowsXml.append("""<c r="B$rNum" t="s"><v>${intern(row.vehicleNo)}</v></c>""")
            sheetRowsXml.append("""<c r="C$rNum" t="s"><v>${intern(row.partyOrRoute)}</v></c>""")
            sheetRowsXml.append("""<c r="D$rNum" t="s"><v>${intern(row.billNo)}</v></c>""")
            sheetRowsXml.append("""<c r="E$rNum"><v>${row.freightAmount}</v></c>""")
            sheetRowsXml.append("""<c r="F$rNum"><v>${row.expenseAmount}</v></c>""")
            sheetRowsXml.append("""<c r="G$rNum"><v>$net</v></c>""")
            sheetRowsXml.append("""<c r="H$rNum" t="s"><v>${intern(RowStatus.fromRaw(row.status).label)}</v></c>""")
            sheetRowsXml.append("""<c r="I$rNum" t="s"><v>${intern(row.notes)}</v></c>""")
            sheetRowsXml.append("""<c r="J$rNum" t="s"><v>${intern(row.sourceLabel)}</v></c>""")
            sheetRowsXml.append("</row>")
        }

        val totalRowNum = rows.size + 2
        val totalFreight = rows.sumOf { it.freightAmount }
        val totalExpense = rows.sumOf { it.expenseAmount }
        val totalNet = totalFreight - totalExpense
        sheetRowsXml.append("""<row r="$totalRowNum">""")
        sheetRowsXml.append("""<c r="A$totalRowNum" t="s" s="1"><v>${intern("TOTAL")}</v></c>""")
        sheetRowsXml.append("""<c r="E$totalRowNum" s="1"><v>$totalFreight</v></c>""")
        sheetRowsXml.append("""<c r="F$totalRowNum" s="1"><v>$totalExpense</v></c>""")
        sheetRowsXml.append("""<c r="G$totalRowNum" s="1"><v>$totalNet</v></c>""")
        sheetRowsXml.append("</row>")

        val sharedStringsXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${strings.size}" uniqueCount="${strings.size}">""")
            strings.forEach { s ->
                append("<si><t>${xmlEscape(s)}</t></si>")
            }
            append("</sst>")
        }

        val worksheetXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <cols>
    <col min="1" max="1" width="14" customWidth="1"/>
    <col min="2" max="2" width="16" customWidth="1"/>
    <col min="3" max="3" width="32" customWidth="1"/>
    <col min="4" max="4" width="14" customWidth="1"/>
    <col min="5" max="7" width="14" customWidth="1"/>
    <col min="8" max="8" width="14" customWidth="1"/>
    <col min="9" max="10" width="28" customWidth="1"/>
  </cols>
  <sheetData>$sheetRowsXml</sheetData>
</worksheet>"""

        val contentTypesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

        val rootRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

        val workbookRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

        val workbookXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="${xmlEscape(sheet.name.take(28).ifBlank { "Ledger" })}" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>"""

        val stylesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><name val="Calibri"/></font>
  </fonts>
  <fills count="2">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
  </fills>
  <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="2">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
  </cellXfs>
</styleSheet>"""

        ZipOutputStream(BufferedOutputStream(FileOutputStream(file))).use { zip ->
            fun addEntry(path: String, xml: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(xml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            addEntry("[Content_Types].xml", contentTypesXml)
            addEntry("_rels/.rels", rootRelsXml)
            addEntry("xl/_rels/workbook.xml.rels", workbookRelsXml)
            addEntry("xl/workbook.xml", workbookXml)
            addEntry("xl/sharedStrings.xml", sharedStringsXml)
            addEntry("xl/styles.xml", stylesXml)
            addEntry("xl/worksheets/sheet1.xml", worksheetXml)
        }
        return file
    }

    fun renderPdfPages(context: Context, uri: Uri): List<Bitmap> {
        val pages = mutableListOf<Bitmap>()
        val tempFile = File(context.cacheDir, "opened_pdf_${System.currentTimeMillis()}.pdf")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        } ?: return emptyList()

        val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
        pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val count = renderer.pageCount.coerceAtMost(20)
                for (i in 0 until count) {
                    renderer.openPage(i).use { page ->
                        val scale = 2
                        val bmp = Bitmap.createBitmap(
                            page.width * scale,
                            page.height * scale,
                            Bitmap.Config.ARGB_8888
                        )
                        val canvas = Canvas(bmp)
                        canvas.drawColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        pages.add(bmp)
                    }
                }
            }
        }
        return pages
    }

    fun exportEditedPdf(
        context: Context,
        basePages: List<Bitmap>,
        textEdits: List<PdfTextEdit>,
        photoOverlays: List<PdfPhotoOverlay>,
        fileNamePrefix: String
    ): File {
        val pdf = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 22, 26)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val redactionPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(70, 75, 85)
            textSize = 9.5f
        }

        basePages.forEachIndexed { pageIdx, baseBmp ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIdx + 1).create()
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas

            val destRect = RectF(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat())
            canvas.drawBitmap(baseBmp, null, destRect, null)

            textEdits.filter { it.pageIndex == pageIdx }.forEach { edit ->
                val x = edit.xRatio * pageWidth
                val y = edit.yRatio * pageHeight
                if (edit.isRedaction) {
                    canvas.drawRect(x, y - 16f, x + 150f, y + 8f, redactionPaint)
                }
                if (edit.text.isNotBlank()) {
                    textPaint.textSize = edit.fontSizeSp
                    canvas.drawText(edit.text, x, y, textPaint)
                }
            }

            photoOverlays.filter { it.pageIndex == pageIdx }.forEach { overlay ->
                val x = overlay.xRatio * pageWidth
                val y = overlay.yRatio * pageHeight
                val w = (overlay.widthRatio * pageWidth).coerceIn(80f, 260f)
                val aspect = overlay.bitmap.height.toFloat() / max(1, overlay.bitmap.width)
                val h = (w * aspect).coerceIn(60f, 220f)
                canvas.drawBitmap(overlay.bitmap, null, RectF(x, y, x + w, y + h), null)
                if (overlay.caption.isNotBlank()) {
                    canvas.drawText(overlay.caption.take(65), x, y + h + 12f, captionPaint)
                }
            }
            pdf.finishPage(page)
        }

        val outFile = File(context.cacheDir, "${fileNamePrefix}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(outFile).use { pdf.writeTo(it) }
        pdf.close()
        return outFile
    }

    fun generateHumanizedBillPdf(
        context: Context,
        profile: BusinessProfile,
        billTitle: String,
        docTypeLabel: String,
        billNo: String,
        vehicleNo: String,
        partyName: String,
        routeOrDescription: String,
        baseAmount: Double,
        gstPercent: Double,
        notes: String,
        photos: List<BillPhotoAttachment>
    ): File {
        val pdf = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(29, 29, 31)
            textSize = 20f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(110, 110, 115)
            textSize = 10f
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(110, 110, 115)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(29, 29, 31)
            textSize = 11.5f
        }
        val boldBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(29, 29, 31)
            textSize = 12f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val linePaint = Paint().apply {
            color = Color.rgb(225, 225, 230)
            strokeWidth = 1f
        }
        val cardFillPaint = Paint().apply {
            color = Color.rgb(245, 245, 247)
            style = Paint.Style.FILL
        }

        val pageInfo1 = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page1 = pdf.startPage(pageInfo1)
        val c1 = page1.canvas
        c1.drawColor(Color.WHITE)

        // Header
        c1.drawText(profile.companyName.ifBlank { "Transport Office" }, 44f, 58f, headerPaint)
        c1.drawText(profile.officeAddress, 44f, 76f, subPaint)
        c1.drawText("GSTIN: ${profile.gstin}   |   Ph: ${profile.contactPhone}", 44f, 92f, subPaint)

        c1.drawText(docTypeLabel.uppercase(), 400f, 54f, labelPaint)
        c1.drawText("No: ${billNo.ifBlank { "INV-01" }}", 400f, 72f, boldBodyPaint)
        c1.drawText("Date: ${DateUtil.todayIso()}", 400f, 88f, subPaint)
        c1.drawLine(44f, 110f, pageWidth - 44f, 110f, linePaint)

        // Party & Vehicle Card
        c1.drawRoundRect(RectF(44f, 128f, pageWidth - 44f, 206f), 10f, 10f, cardFillPaint)
        c1.drawText("BILLED TO / AGENCY", 60f, 148f, labelPaint)
        c1.drawText(partyName.ifBlank { "Valued Consignor" }, 60f, 166f, boldBodyPaint)
        c1.drawText("Document: $billTitle", 60f, 186f, subPaint)

        c1.drawText("VEHICLE NO", 360f, 148f, labelPaint)
        c1.drawText(vehicleNo.ifBlank { "Fleet Dispatch" }, 360f, 166f, boldBodyPaint)

        // Table Header
        var y = 236f
        c1.drawText("PARTICULARS / ROUTE", 44f, y, labelPaint)
        c1.drawText("AMOUNT (INR)", 450f, y, labelPaint)
        y += 10f
        c1.drawLine(44f, y, pageWidth - 44f, y, linePaint)
        y += 24f

        c1.drawText(routeOrDescription.ifBlank { "Freight & Transport Charges" }.take(64), 44f, y, bodyPaint)
        c1.drawText("Rs %.2f".format(baseAmount), 450f, y, bodyPaint)
        y += 24f

        val gstAmount = baseAmount * (gstPercent / 100.0)
        if (gstPercent > 0.0) {
            c1.drawText("GST (${gstPercent.toInt()}% Freight Service)", 44f, y, bodyPaint)
            c1.drawText("Rs %.2f".format(gstAmount), 450f, y, bodyPaint)
            y += 24f
        }

        c1.drawLine(44f, y, pageWidth - 44f, y, linePaint)
        y += 24f
        val grandTotal = baseAmount + gstAmount
        c1.drawText("TOTAL PAYABLE", 320f, y, boldBodyPaint)
        c1.drawText("Rs %.2f".format(grandTotal), 450f, y, boldBodyPaint)

        y += 32f
        if (notes.isNotBlank()) {
            c1.drawText("REMARKS", 44f, y, labelPaint)
            y += 16f
            c1.drawText(notes.take(86), 44f, y, bodyPaint)
            y += 24f
        }

        // Integrated Photos inside the bill
        if (photos.isNotEmpty()) {
            y += 10f
            c1.drawText("ATTACHED DISPATCH / PROOF PHOTOS (${photos.size})", 44f, y, labelPaint)
            y += 16f
            val firstRowPhotos = photos.take(2)
            firstRowPhotos.forEachIndexed { idx, att ->
                val x = if (idx == 0) 44f else 308f
                val boxW = 240f
                val boxH = 165f
                if (y + boxH + 30f < pageHeight - 50f) {
                    c1.drawBitmap(att.bitmap, null, RectF(x, y, x + boxW, y + boxH), null)
                    c1.drawText(att.caption.take(42), x, y + boxH + 14f, subPaint)
                }
            }
        }

        c1.drawText("Authorized Signatory · ${profile.companyName}", 44f, pageHeight - 36f, subPaint)
        pdf.finishPage(page1)

        // Extra page if > 2 photos
        if (photos.size > 2) {
            val pageInfo2 = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create()
            val page2 = pdf.startPage(pageInfo2)
            val c2 = page2.canvas
            c2.drawColor(Color.WHITE)
            c2.drawText("ADDITIONAL PROOF PHOTOS · $billNo", 44f, 52f, labelPaint)
            var py = 74f
            photos.drop(2).chunked(2).forEach { pair ->
                pair.forEachIndexed { idx, att ->
                    val x = if (idx == 0) 44f else 308f
                    c2.drawBitmap(att.bitmap, null, RectF(x, py, x + 240f, py + 180f), null)
                    c2.drawText(att.caption.take(42), x, py + 194f, subPaint)
                }
                py += 225f
            }
            pdf.finishPage(page2)
        }

        val cleanName = billNo.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "bill" }
        val file = File(context.cacheDir, "${cleanName}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        return file
    }

    fun decodeSampledBitmap(context: Context, uri: Uri, maxDim: Int = 1800): Pair<Bitmap, Int>? {
        val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDim || bounds.outHeight / sampleSize > maxDim) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bmp = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, opts) ?: return null
        return bmp to rawBytes.size
    }

    fun processImageQuality(
        source: Bitmap,
        originalByteCount: Int,
        enhanceClarity: Boolean,
        jpegQuality: Int,
        maxDimension: Int
    ): OptimizedImageResult {
        val scale = min(1f, maxDimension.toFloat() / max(source.width, source.height).coerceAtLeast(1))
        val targetW = max(1, (source.width * scale).roundToInt())
        val targetH = max(1, (source.height * scale).roundToInt())

        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(source, targetW, targetH, true)
        } else {
            source.copy(Bitmap.Config.ARGB_8888, false)
        }

        val processed = if (enhanceClarity) {
            val out = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val contrast = 1.24f
            val brightness = -18f
            val cm = ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, brightness,
                    0f, contrast, 0f, 0f, brightness,
                    0f, 0f, contrast, 0f, brightness,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(cm)
            }
            canvas.drawBitmap(scaled, 0f, 0f, paint)
            out
        } else {
            scaled
        }

        val baos = ByteArrayOutputStream()
        processed.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(10, 100), baos)
        val compressedBytes = baos.toByteArray()
        val finalBmp = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size) ?: processed

        return OptimizedImageResult(
            bitmap = finalBmp,
            jpegBytes = compressedBytes,
            originalBytes = originalByteCount,
            optimizedBytes = compressedBytes.size
        )
    }

    fun saveImageBytesToCache(context: Context, bytes: ByteArray, prefix: String = "optimized_doc"): File {
        val file = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.jpg")
        file.writeBytes(bytes)
        return file
    }

    private fun csvEscape(value: String): String {
        val clean = value.replace("\"", "\"\"")
        return "\"$clean\""
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    result.add(sb.toString())
                    sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        result.add(sb.toString())
        return result
    }

    private fun colLetter(index: Int): String = ('A'.code + index).toChar().toString()

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
