package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.UiState
import com.example.data.BusinessDocument
import com.example.data.DocType
import com.example.ui.LogisticsViewModel
import com.example.ui.components.AppleCard
import com.example.utils.BillPhotoAttachment
import com.example.utils.ExportEngine
import com.example.utils.OptimizedImageResult
import com.example.utils.PdfPhotoOverlay
import com.example.utils.PdfTextEdit
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

private enum class StudioMode(val label: String) {
    PDF_EDITOR("PDF Editor"),
    BILL_STUDIO("Bill + Photos"),
    IMAGE_QUALITY("Image Quality")
}

@Composable
fun DocumentStudioScreen(
    viewModel: LogisticsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by viewModel.businessProfile.collectAsStateWithLifecycle()
    val docsState by viewModel.documentsState.collectAsStateWithLifecycle()

    var mode by remember { mutableStateOf(StudioMode.PDF_EDITOR) }

    // PDF Editor State
    var pdfPages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var currentPageIdx by remember { mutableIntStateOf(0) }
    val pdfTextEdits = remember { mutableStateListOf<PdfTextEdit>() }
    val pdfPhotos = remember { mutableStateListOf<PdfPhotoOverlay>() }
    var tapXRatio by remember { mutableFloatStateOf(0.18f) }
    var tapYRatio by remember { mutableFloatStateOf(0.28f) }
    var annotationText by remember { mutableStateOf("") }
    var useWhiteout by remember { mutableStateOf(false) }

    // Bill + Photo Creator State
    var billDocType by remember { mutableStateOf(DocType.FREIGHT_INVOICE) }
    var billTitle by remember { mutableStateOf("Freight Settlement Invoice") }
    var billNo by remember { mutableStateOf("INV-2026-108") }
    var billVehicle by remember { mutableStateOf("MH12PQ4821") }
    var billParty by remember { mutableStateOf("") }
    var billDesc by remember { mutableStateOf("Full Truckload Dispatch") }
    var billAmount by remember { mutableStateOf("24500") }
    var billGst by remember { mutableStateOf("5") }
    var billNotes by remember { mutableStateOf("") }
    val billPhotos = remember { mutableStateListOf<BillPhotoAttachment>() }

    // Image Quality Optimizer State
    var sourcePhoto by remember { mutableStateOf<Pair<Bitmap, Int>?>(null) }
    var enhanceClarity by remember { mutableStateOf(true) }
    var jpegQuality by remember { mutableFloatStateOf(72f) }
    var maxDimension by remember { mutableFloatStateOf(1400f) }
    var optimizedResult by remember { mutableStateOf<OptimizedImageResult?>(null) }

    val openPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val pages = ExportEngine.renderPdfPages(context, it)
            if (pages.isNotEmpty()) {
                pdfPages = pages
                currentPageIdx = 0
                pdfTextEdits.clear()
                pdfPhotos.clear()
            }
        }
    }

    val pdfPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            val decoded = ExportEngine.decodeSampledBitmap(context, it, maxDim = 1200)
            if (decoded != null) {
                scope.launch {
                    val caption = viewModel.generatePhotoCaption(decoded.first, billVehicle)
                    pdfPhotos.add(
                        PdfPhotoOverlay(
                            id = UUID.randomUUID().toString(),
                            pageIndex = currentPageIdx,
                            xRatio = tapXRatio.coerceIn(0.05f, 0.55f),
                            yRatio = tapYRatio.coerceIn(0.05f, 0.65f),
                            widthRatio = 0.36f,
                            bitmap = decoded.first,
                            caption = caption
                        )
                    )
                }
            }
        }
    }

    val billPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            val decoded = ExportEngine.decodeSampledBitmap(context, it, maxDim = 1200)
            if (decoded != null) {
                scope.launch {
                    val caption = viewModel.generatePhotoCaption(decoded.first, billVehicle)
                    billPhotos.add(
                        BillPhotoAttachment(
                            id = UUID.randomUUID().toString(),
                            bitmap = decoded.first,
                            caption = caption
                        )
                    )
                }
            }
        }
    }

    val qualityPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            val decoded = ExportEngine.decodeSampledBitmap(context, it, maxDim = 2200)
            if (decoded != null) {
                sourcePhoto = decoded
                optimizedResult = ExportEngine.processImageQuality(
                    source = decoded.first,
                    originalByteCount = decoded.second,
                    enhanceClarity = enhanceClarity,
                    jpegQuality = jpegQuality.roundToInt(),
                    maxDimension = maxDimension.roundToInt()
                )
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "PDF, Bill & Image Studio",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Edit agency PDFs, embed photos in bills, and enhance or compress images",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                StudioMode.entries.forEachIndexed { idx, studioMode ->
                    SegmentedButton(
                        selected = mode == studioMode,
                        onClick = { mode = studioMode },
                        shape = SegmentedButtonDefaults.itemShape(idx, StudioMode.entries.size)
                    ) {
                        Text(studioMode.label, maxLines = 1)
                    }
                }
            }
        }

        when (mode) {
            StudioMode.PDF_EDITOR -> {
                item {
                    AppleCard {
                        Text(
                            text = "Official Statement & Invoice PDF Editor",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Open any agency statement or PDF invoice, tap where you want to edit, add corrections, whiteout old figures, or embed photos.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { openPdfLauncher.launch(arrayOf("application/pdf")) },
                                modifier = Modifier.testTag("open_pdf_button")
                            ) {
                                Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (pdfPages.isEmpty()) "Open PDF" else "Change PDF")
                            }
                            if (pdfPages.isNotEmpty()) {
                                FilledTonalButton(onClick = {
                                    val out = ExportEngine.exportEditedPdf(
                                        context = context,
                                        basePages = pdfPages,
                                        textEdits = pdfTextEdits.toList(),
                                        photoOverlays = pdfPhotos.toList(),
                                        fileNamePrefix = "edited_statement"
                                    )
                                    viewModel.saveDocumentHistory(
                                        BusinessDocument(
                                            title = "Edited PDF Statement (${pdfPages.size} pg)",
                                            docType = DocType.OFFICIAL_STATEMENT.name,
                                            vehicleNo = billVehicle,
                                            partyName = "Agency PDF",
                                            amount = 0.0,
                                            photoCount = pdfPhotos.size.toLong(),
                                            summaryNote = "${pdfTextEdits.size} text edit(s) and ${pdfPhotos.size} photo(s) applied."
                                        )
                                    )
                                    ExportEngine.shareFile(context, out, "application/pdf", "Export Edited PDF")
                                }) {
                                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Export PDF")
                                }
                            }
                        }
                    }
                }

                if (pdfPages.isNotEmpty()) {
                    item {
                        AppleCard {
                            if (pdfPages.size > 1) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { if (currentPageIdx > 0) currentPageIdx-- },
                                        enabled = currentPageIdx > 0
                                    ) { Text("Prev") }
                                    Text(
                                        text = "Page ${currentPageIdx + 1} of ${pdfPages.size}",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    OutlinedButton(
                                        onClick = { if (currentPageIdx < pdfPages.lastIndex) currentPageIdx++ },
                                        enabled = currentPageIdx < pdfPages.lastIndex
                                    ) { Text("Next") }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            val pageBmp = pdfPages[currentPageIdx]
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(pageBmp.width.toFloat() / pageBmp.height.coerceAtLeast(1))
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                    .pointerInput(currentPageIdx) {
                                        detectTapGestures { offset ->
                                            tapXRatio = (offset.x / size.width).coerceIn(0.04f, 0.85f)
                                            tapYRatio = (offset.y / size.height).coerceIn(0.05f, 0.92f)
                                        }
                                    }
                            ) {
                                val boxW = constraints.maxWidth.toFloat()
                                val boxH = constraints.maxHeight.toFloat()

                                Image(
                                    bitmap = pageBmp.asImageBitmap(),
                                    contentDescription = "PDF Page ${currentPageIdx + 1}",
                                    contentScale = ContentScale.FillBounds,
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Target placement marker
                                Box(
                                    modifier = Modifier
                                        .offset {
                                            IntOffset(
                                                (tapXRatio * boxW).roundToInt(),
                                                (tapYRatio * boxH).roundToInt()
                                            )
                                        }
                                        .size(12.dp)
                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                                )

                                pdfTextEdits.filter { it.pageIndex == currentPageIdx }.forEach { edit ->
                                    Box(
                                        modifier = Modifier
                                            .offset {
                                                IntOffset(
                                                    (edit.xRatio * boxW).roundToInt(),
                                                    (edit.yRatio * boxH).roundToInt()
                                                )
                                            }
                                            .background(if (edit.isRedaction) Color.White else Color.Transparent)
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = edit.text,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF14161A)
                                        )
                                    }
                                }

                                pdfPhotos.filter { it.pageIndex == currentPageIdx }.forEach { overlay ->
                                    Image(
                                        bitmap = overlay.bitmap.asImageBitmap(),
                                        contentDescription = overlay.caption,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .offset {
                                                IntOffset(
                                                    (overlay.xRatio * boxW).roundToInt(),
                                                    (overlay.yRatio * boxH).roundToInt()
                                                )
                                            }
                                            .size(96.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Tap anywhere on the page above to position your edit (${(tapXRatio * 100).toInt()}%, ${(tapYRatio * 100).toInt()}%).",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = annotationText,
                                onValueChange = { annotationText = it },
                                label = { Text("Replacement / Note Text") },
                                placeholder = { Text("e.g. Verified: Rs 24,500") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Checkbox(checked = useWhiteout, onCheckedChange = { useWhiteout = it })
                                Text("Whiteout background behind text", style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (annotationText.isNotBlank() || useWhiteout) {
                                            pdfTextEdits.add(
                                                PdfTextEdit(
                                                    id = UUID.randomUUID().toString(),
                                                    pageIndex = currentPageIdx,
                                                    xRatio = tapXRatio,
                                                    yRatio = tapYRatio,
                                                    text = annotationText,
                                                    isRedaction = useWhiteout
                                                )
                                            )
                                            annotationText = ""
                                        }
                                    }
                                ) {
                                    Icon(Icons.Rounded.TextFields, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Place Text")
                                }
                                FilledTonalButton(
                                    onClick = {
                                        pdfPhotoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                ) {
                                    Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Insert Photo")
                                }
                            }
                        }
                    }
                }
            }

            StudioMode.BILL_STUDIO -> {
                item {
                    AppleCard {
                        Text(
                            text = "Transport Bill & Photo Composer",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Generate crisp humanized freight invoices or POD bills with dispatch photos embedded directly inside the PDF.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(DocType.entries) { dt ->
                                FilterChip(
                                    selected = billDocType == dt,
                                    onClick = {
                                        billDocType = dt
                                        billTitle = dt.label
                                    },
                                    label = { Text(dt.label) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = billNo,
                                    onValueChange = { billNo = it },
                                    label = { Text("Bill / LR No") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = billVehicle,
                                    onValueChange = { billVehicle = it.uppercase() },
                                    label = { Text("Vehicle No") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            OutlinedTextField(
                                value = billParty,
                                onValueChange = { billParty = it },
                                label = { Text("Consignor / Agency Name") },
                                placeholder = { Text("e.g. Apex Steel Traders") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = billDesc,
                                onValueChange = { billDesc = it },
                                label = { Text("Route / Particulars") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = billAmount,
                                    onValueChange = { billAmount = it },
                                    label = { Text("Base Freight ₹") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = billGst,
                                    onValueChange = { billGst = it },
                                    label = { Text("GST %") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(0.7f)
                                )
                            }
                            OutlinedTextField(
                                value = billNotes,
                                onValueChange = { billNotes = it },
                                label = { Text("Office Remarks") },
                                placeholder = { Text("POD verified at destination warehouse.") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Photos Inside Bill (${billPhotos.size})",
                                style = MaterialTheme.typography.titleMedium
                            )
                            FilledTonalButton(
                                onClick = {
                                    billPhotoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            ) {
                                Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Photo")
                            }
                        }

                        if (billPhotos.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            billPhotos.forEachIndexed { idx, att ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        bitmap = att.bitmap.asImageBitmap(),
                                        contentDescription = att.caption,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    OutlinedTextField(
                                        value = att.caption,
                                        onValueChange = { newText ->
                                            billPhotos[idx] = att.copy(caption = newText)
                                        },
                                        label = { Text("Photo Caption") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { billPhotos.removeAt(idx) }) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove photo")
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                val baseAmt = billAmount.toDoubleOrNull() ?: 0.0
                                val gstPct = billGst.toDoubleOrNull() ?: 0.0
                                val file = ExportEngine.generateHumanizedBillPdf(
                                    context = context,
                                    profile = profile,
                                    billTitle = billTitle,
                                    docTypeLabel = billDocType.label,
                                    billNo = billNo,
                                    vehicleNo = billVehicle,
                                    partyName = billParty,
                                    routeOrDescription = billDesc,
                                    baseAmount = baseAmt,
                                    gstPercent = gstPct,
                                    notes = billNotes,
                                    photos = billPhotos.toList()
                                )
                                viewModel.saveDocumentHistory(
                                    BusinessDocument(
                                        title = "$billTitle ($billNo)",
                                        docType = billDocType.name,
                                        vehicleNo = billVehicle,
                                        partyName = billParty.ifBlank { "Consignor" },
                                        amount = baseAmt * (1.0 + gstPct / 100.0),
                                        photoCount = billPhotos.size.toLong(),
                                        summaryNote = billDesc
                                    )
                                )
                                ExportEngine.shareFile(context, file, "application/pdf", "Share $billNo PDF")
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("export_bill_pdf_button")
                        ) {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate & Share PDF Bill")
                        }
                    }
                }
            }

            StudioMode.IMAGE_QUALITY -> {
                item {
                    AppleCard {
                        Text(
                            text = "Enhance or Reduce Image Quality",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Sharpen faded paper bills & RC cards, or reduce file size for fast WhatsApp & email sharing.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                qualityPhotoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.testTag("pick_image_quality_button")
                        ) {
                            Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (sourcePhoto == null) "Select Photo to Optimize" else "Pick Different Photo")
                        }

                        sourcePhoto?.let { src ->
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Enhance Document Clarity", style = MaterialTheme.typography.bodyLarge)
                                Switch(
                                    checked = enhanceClarity,
                                    onCheckedChange = { checked ->
                                        enhanceClarity = checked
                                        optimizedResult = ExportEngine.processImageQuality(
                                            src.first,
                                            src.second,
                                            checked,
                                            jpegQuality.roundToInt(),
                                            maxDimension.roundToInt()
                                        )
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "JPEG Quality: ${jpegQuality.roundToInt()}%",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Slider(
                                value = jpegQuality,
                                onValueChange = { jpegQuality = it },
                                valueRange = 15f..100f,
                                onValueChangeFinished = {
                                    optimizedResult = ExportEngine.processImageQuality(
                                        src.first,
                                        src.second,
                                        enhanceClarity,
                                        jpegQuality.roundToInt(),
                                        maxDimension.roundToInt()
                                    )
                                }
                            )

                            Text(
                                text = "Max Resolution: ${maxDimension.roundToInt()} px",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Slider(
                                value = maxDimension,
                                onValueChange = { maxDimension = it },
                                valueRange = 480f..2200f,
                                onValueChangeFinished = {
                                    optimizedResult = ExportEngine.processImageQuality(
                                        src.first,
                                        src.second,
                                        enhanceClarity,
                                        jpegQuality.roundToInt(),
                                        maxDimension.roundToInt()
                                    )
                                }
                            )

                            optimizedResult?.let { opt ->
                                val origKb = (opt.originalBytes / 1024).coerceAtLeast(1)
                                val newKb = (opt.optimizedBytes / 1024).coerceAtLeast(1)
                                val deltaPct = (((origKb - newKb).toFloat() / origKb) * 100f).roundToInt()

                                Spacer(modifier = Modifier.height(8.dp))
                                AppleCard(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                                    Text(
                                        text = "Size: $origKb KB -> $newKb KB (${if (deltaPct >= 0) "$deltaPct% smaller" else "enhanced"})",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Image(
                                        bitmap = opt.bitmap.asImageBitmap(),
                                        contentDescription = "Optimized image preview",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(210.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = {
                                        val f = ExportEngine.saveImageBytesToCache(context, opt.jpegBytes, "gologistics_photo")
                                        ExportEngine.shareFile(context, f, "image/jpeg", "Share Optimized Document")
                                    }) {
                                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Export / Share")
                                    }
                                    FilledTonalButton(onClick = {
                                        billPhotos.add(
                                            BillPhotoAttachment(
                                                id = UUID.randomUUID().toString(),
                                                bitmap = opt.bitmap,
                                                caption = "Verified receipt photo"
                                            )
                                        )
                                        mode = StudioMode.BILL_STUDIO
                                    }) {
                                        Text("Attach to Bill")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val savedDocs = (docsState as? UiState.Success)?.data ?: emptyList()
        if (savedDocs.isNotEmpty()) {
            item {
                Text(
                    text = "Recent Studio Documents (${savedDocs.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            items(savedDocs.take(5), key = { it.id }) { doc ->
                AppleCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(doc.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${doc.partyName} · ${doc.vehicleNo} · ${doc.photoCount} photo(s)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (doc.amount > 0) {
                            Text(
                                text = "₹${doc.amount.toInt()}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}
