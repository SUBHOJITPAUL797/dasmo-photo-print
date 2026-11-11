package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.BorderStyle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CropLandscape
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.domain.model.PageLayout
import com.example.domain.model.PageOrientation
import com.example.domain.model.ProjectMode
import com.example.ui.ProjectViewModel
import com.example.util.BitmapUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    viewModel: ProjectViewModel,
    onBackClicked: () -> Unit,
    onApproved: () -> Unit
) {
    val pages = viewModel.computedPages
    var currentPageIndex by remember { mutableStateOf(0) }
    var showBorderControls by remember { mutableStateOf(true) }

    val pageSummary = remember(pages, viewModel.quantity, viewModel.mode, viewModel.batchItems.size, viewModel.selectedPaperSpec) {
        val paperName = viewModel.selectedPaperSpec.name
        if (viewModel.mode == ProjectMode.BATCH_PAPER_SAVER) {
            val total = viewModel.getTotalBatchPhotosCount()
            "$total mixed photos across ${pages.size} sheet(s) • $paperName"
        } else {
            "${viewModel.quantity} photos distributed across ${pages.size} page(s) • $paperName"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Print Layout Preview", fontWeight = FontWeight.Bold)
                        Text(
                            text = pageSummary,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClicked, modifier = Modifier.testTag("preview_back_btn")) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = viewModel.canUndo,
                        modifier = Modifier.testTag("undo_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo"
                        )
                    }
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = viewModel.canRedo,
                        modifier = Modifier.testTag("redo_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo"
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Button(
                    onClick = onApproved,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(52.dp)
                        .testTag("preview_approve_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(imageVector = Icons.Default.Done, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Looks Good • Generate PDF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Dynamic paper / page orientation + Live Border controls card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Page Layout",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isPortrait = viewModel.pageOrientation == PageOrientation.PORTRAIT

                            FilterChip(
                                selected = isPortrait,
                                onClick = {
                                    if (!isPortrait) {
                                        viewModel.pageOrientation = PageOrientation.PORTRAIT
                                        viewModel.pushHistoryState()
                                        viewModel.computeCurrentLayout()
                                        currentPageIndex = 0
                                    }
                                },
                                label = { Text("Portrait") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.CropPortrait,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.testTag("orientation_portrait_chip")
                            )

                            FilterChip(
                                selected = !isPortrait,
                                onClick = {
                                    if (isPortrait) {
                                        viewModel.pageOrientation = PageOrientation.LANDSCAPE
                                        viewModel.pushHistoryState()
                                        viewModel.computeCurrentLayout()
                                        currentPageIndex = 0
                                    }
                                },
                                label = { Text("Landscape") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.CropLandscape,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.testTag("orientation_landscape_chip")
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Live Border & Cutting Guide Quick Customizer Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showBorderControls = !showBorderControls }
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BorderStyle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Photo Border & Color",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = viewModel.cuttingGuidesEnabled,
                                onCheckedChange = {
                                    viewModel.cuttingGuidesEnabled = it
                                    viewModel.pushHistoryState()
                                },
                                modifier = Modifier.testTag("preview_border_switch")
                            )
                            IconButton(
                                onClick = { showBorderControls = !showBorderControls },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (showBorderControls) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Toggle border controls"
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = showBorderControls && viewModel.cuttingGuidesEnabled) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Color Picker Row + Solid/Dashed Toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val borderColors = listOf(
                                    "Pitch Black" to 0xFF000000.toInt(),
                                    "Dark Gray" to 0xFF424242.toInt(),
                                    "Light Gray" to 0xFF9E9E9E.toInt(),
                                    "Navy Blue" to 0xFF1A56DB.toInt(),
                                    "Crimson Red" to 0xFFD32F2F.toInt(),
                                    "Forest Green" to 0xFF2E7D32.toInt()
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    borderColors.forEach { (name, colorInt) ->
                                        val isSelected = viewModel.cuttingGuideColor == colorInt
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(Color(colorInt))
                                                .border(
                                                    width = if (isSelected) 2.5.dp else 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                                                    shape = CircleShape
                                                )
                                                .clickable {
                                                    viewModel.cuttingGuideColor = colorInt
                                                    viewModel.pushHistoryState()
                                                }
                                                .testTag("preview_border_color_${name.replace(" ", "_")}"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = name,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Solid vs Dashed compact toggle
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    FilterChip(
                                        selected = viewModel.cuttingGuideStyle == "solid",
                                        onClick = {
                                            viewModel.cuttingGuideStyle = "solid"
                                            viewModel.pushHistoryState()
                                        },
                                        label = { Text("Solid", style = MaterialTheme.typography.labelSmall) }
                                    )
                                    FilterChip(
                                        selected = viewModel.cuttingGuideStyle == "dashed",
                                        onClick = {
                                            viewModel.cuttingGuideStyle = "dashed"
                                            viewModel.pushHistoryState()
                                        },
                                        label = { Text("Dashed", style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }

                            // Border Thickness Quick Selector
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Thickness: ${String.format("%.1f", viewModel.cuttingGuideThicknessPt)} pt",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Slider(
                                    value = viewModel.cuttingGuideThicknessPt,
                                    onValueChange = { viewModel.cuttingGuideThicknessPt = it },
                                    onValueChangeFinished = { viewModel.pushHistoryState() },
                                    valueRange = 0.5f..3.5f,
                                    steps = 5,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 12.dp)
                                        .height(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (viewModel.mode == ProjectMode.ID_CARD) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ID Arrangement",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val isHorizontal = viewModel.idCardArrangement == "HORIZONTAL"

                            FilterChip(
                                selected = isHorizontal,
                                onClick = {
                                    if (!isHorizontal) {
                                        viewModel.idCardArrangement = "HORIZONTAL"
                                        viewModel.pushHistoryState()
                                        viewModel.generateJointComposite()
                                        viewModel.computeCurrentLayout()
                                        currentPageIndex = 0
                                    }
                                },
                                label = { Text("Side-by-Side") },
                                modifier = Modifier.testTag("id_arrangement_side_by_side")
                            )

                            FilterChip(
                                selected = !isHorizontal,
                                onClick = {
                                    if (isHorizontal) {
                                        viewModel.idCardArrangement = "VERTICAL"
                                        viewModel.pushHistoryState()
                                        viewModel.generateJointComposite()
                                        viewModel.computeCurrentLayout()
                                        currentPageIndex = 0
                                    }
                                },
                                label = { Text("Stacked") },
                                modifier = Modifier.testTag("id_arrangement_stacked")
                            )
                        }
                    }
                }
            }

            if (viewModel.mode == ProjectMode.MULTI_PERSON) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Person 1: ${viewModel.quantityA} pcs • Person 2: ${viewModel.quantityB} pcs",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Adjust copy split on sheet",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val total = viewModel.quantity.toIntOrNull() ?: 16
                            FilledTonalIconButton(
                                onClick = { viewModel.updateQuantityA(viewModel.quantityA - 1) },
                                enabled = viewModel.quantityA > 1,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Text("-", fontWeight = FontWeight.Bold)
                            }
                            FilledTonalIconButton(
                                onClick = { viewModel.updateQuantityA(viewModel.quantityA + 1) },
                                enabled = viewModel.quantityA < total - 1,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Text("+", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Pager-like index bar (Standard Back/Next Page controllers)
            if (pages.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (currentPageIndex > 0) currentPageIndex-- },
                        enabled = currentPageIndex > 0,
                        modifier = Modifier.testTag("preview_prev_page_btn")
                    ) {
                        Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "Previous Page")
                    }

                    Text(
                        text = "Page ${currentPageIndex + 1} of ${pages.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        textAlign = TextAlign.Center
                    )

                    IconButton(
                        onClick = { if (currentPageIndex < pages.size - 1) currentPageIndex++ },
                        enabled = currentPageIndex < pages.size - 1,
                        modifier = Modifier.testTag("preview_next_page_btn")
                    ) {
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Next Page")
                    }
                }
            }

            // Real-time WYSIWYG simulated sheet
            if (pages.isNotEmpty() && currentPageIndex < pages.size) {
                val activePage = pages[currentPageIndex]
                val unitBmp = viewModel.finalUnitBitmap

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val basePaperWidth = viewModel.selectedPaperSpec.widthCm
                    val basePaperHeight = viewModel.selectedPaperSpec.heightCm
                    val pWidth = if (viewModel.pageOrientation == PageOrientation.PORTRAIT) minOf(basePaperWidth, basePaperHeight) else maxOf(basePaperWidth, basePaperHeight)
                    val pHeight = if (viewModel.pageOrientation == PageOrientation.PORTRAIT) maxOf(basePaperWidth, basePaperHeight) else minOf(basePaperWidth, basePaperHeight)

                    A4SimulatedSheet(
                        pageLayout = activePage,
                        unitBitmap = unitBmp,
                        marginCm = viewModel.marginCm.toFloatOrNull() ?: 0.5f,
                        cuttingGuidesEnabled = viewModel.cuttingGuidesEnabled,
                        cuttingGuideThicknessPt = viewModel.cuttingGuideThicknessPt,
                        cuttingGuideStyle = viewModel.cuttingGuideStyle,
                        cuttingGuideColor = viewModel.cuttingGuideColor,
                        pageWidthCm = pWidth,
                        pageHeightCm = pHeight,
                        mode = viewModel.mode,
                        bitmapA = viewModel.cropABitmap,
                        bitmapB = viewModel.cropBBitmap,
                        quantityA = viewModel.quantityA,
                        pages = pages
                    )
                }
            } else {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Computing layout sheets...")
                }
            }
        }
    }
}

@Composable
fun A4SimulatedSheet(
    pageLayout: PageLayout,
    unitBitmap: Bitmap?,
    marginCm: Float,
    cuttingGuidesEnabled: Boolean,
    cuttingGuideThicknessPt: Float,
    cuttingGuideStyle: String,
    cuttingGuideColor: Int = 0xFF000000.toInt(),
    pageWidthCm: Float,
    pageHeightCm: Float,
    mode: ProjectMode = ProjectMode.SINGLE,
    bitmapA: Bitmap? = null,
    bitmapB: Bitmap? = null,
    quantityA: Int = 0,
    pages: List<PageLayout> = emptyList()
) {
    var sheetWidthPx by remember { mutableStateOf(0) }
    var sheetHeightPx by remember { mutableStateOf(0) }

    val isLandscape = pageWidthCm > pageHeightCm

    // Simulated paper sheet is drawn centered with fixed aspect ratio based on paper size
    Box(
        modifier = Modifier
            .fillMaxSize()
            .aspectRatio(pageWidthCm / pageHeightCm, matchHeightConstraintsFirst = !isLandscape)
            .shadow(8.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
            .onGloballyPositioned { coords ->
                sheetWidthPx = coords.size.width
                sheetHeightPx = coords.size.height
            }
            .testTag("a4_simulated_sheet")
    ) {
        if (sheetWidthPx > 0 && sheetHeightPx > 0 && (unitBitmap != null || (mode == ProjectMode.MULTI_PERSON && (bitmapA != null || bitmapB != null)))) {
            // Screen scale conversion (pixels per cm)
            val pXcm = sheetWidthPx / pageWidthCm

            ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                // Ensure pure white paper sheet background
                drawRect(color = Color.White, size = size)

                // 1. Draw all image units using native canvas with center-crop for zero distortion
                drawIntoCanvas { canvas ->
                    val nativeCanvas = canvas.nativeCanvas
                    val paint = android.graphics.Paint().apply {
                        isFilterBitmap = true
                        isAntiAlias = true
                    }
                    val whiteCellPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        style = android.graphics.Paint.Style.FILL
                    }

                    val placementsCountBefore = pages.take(pageLayout.pageIndex).sumOf { it.placements.size }

                    for ((placementIndex, placement) in pageLayout.placements.withIndex()) {
                        val x = placement.xCm * pXcm
                        val y = placement.yCm * pXcm

                        val isCellRotated = if (placement.widthCm > 0f) placement.isRotated else pageLayout.isRotated
                        val cellW = if (placement.widthCm > 0f) placement.widthCm else pageLayout.cellWidthCm
                        val cellH = if (placement.heightCm > 0f) placement.heightCm else pageLayout.cellHeightCm

                        val w = cellW * pXcm
                        val h = cellH * pXcm

                        // Pure white backing behind each photo unit
                        nativeCanvas.drawRect(x, y, x + w, y + h, whiteCellPaint)

                        val globalIndex = placementsCountBefore + placementIndex
                        val activeBmp = if (mode == ProjectMode.MULTI_PERSON) {
                            if (globalIndex < quantityA) (bitmapA ?: unitBitmap) else (bitmapB ?: unitBitmap)
                        } else {
                            unitBitmap
                        }

                        if (activeBmp != null) {
                            if (isCellRotated) {
                                nativeCanvas.save()
                                nativeCanvas.translate(x + w / 2f, y + h / 2f)
                                nativeCanvas.rotate(90f)
                                val dstRect = android.graphics.RectF(-h / 2f, -w / 2f, h / 2f, w / 2f)
                                BitmapUtils.drawWithCenterCrop(nativeCanvas, activeBmp, dstRect, paint)
                                nativeCanvas.restore()
                            } else {
                                val dstRect = android.graphics.RectF(x, y, x + w, y + h)
                                BitmapUtils.drawWithCenterCrop(nativeCanvas, activeBmp, dstRect, paint)
                            }
                        }
                    }
                }

                // 2. Draw all photo borders / cutting guides cleanly inset so the white gap between photos stays 100% pure white
                if (cuttingGuidesEnabled && cuttingGuideThicknessPt > 0f) {
                    val strokeWidthPx = (cuttingGuideThicknessPt * (2.54f / 72f) * pXcm).coerceAtLeast(1f)
                    val dashUnitPx = (4f * (2.54f / 72f) * pXcm).coerceAtLeast(3f)

                    for (placement in pageLayout.placements) {
                        val x = placement.xCm * pXcm
                        val y = placement.yCm * pXcm

                        val cellWVal = if (placement.widthCm > 0f) placement.widthCm else pageLayout.cellWidthCm
                        val cellHVal = if (placement.heightCm > 0f) placement.heightCm else pageLayout.cellHeightCm

                        val w = cellWVal * pXcm
                        val h = cellHVal * pXcm
                        val halfStroke = (strokeWidthPx / 2f).coerceAtMost(minOf(w, h) * 0.1f)

                        drawRect(
                            color = Color(cuttingGuideColor),
                            topLeft = Offset(x + halfStroke, y + halfStroke),
                            size = Size((w - strokeWidthPx).coerceAtLeast(1f), (h - strokeWidthPx).coerceAtLeast(1f)),
                            style = Stroke(
                                width = strokeWidthPx,
                                pathEffect = if (cuttingGuideStyle == "dashed") PathEffect.dashPathEffect(floatArrayOf(dashUnitPx, dashUnitPx), 0f) else null
                            )
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
