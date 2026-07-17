package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.CropLandscape
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material3.*
import com.example.domain.model.PageOrientation
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.domain.model.PageLayout
import com.example.domain.model.ProjectMode
import com.example.ui.ProjectViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    viewModel: ProjectViewModel,
    onBackClicked: () -> Unit,
    onApproved: () -> Unit
) {
    val pages = viewModel.computedPages
    var currentPageIndex by remember { mutableStateOf(0) }

    val pageSummary = remember(pages, viewModel.quantity) {
        "${viewModel.quantity} photos distributed across ${pages.size} page(s)"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Print Layout Preview") },
                navigationIcon = {
                    IconButton(onClick = onBackClicked, modifier = Modifier.testTag("preview_back_btn")) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Go back")
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
                modifier = Modifier.navigationBarsPadding()
            ) {
                Button(
                    onClick = onApproved,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(50.dp)
                        .testTag("preview_approve_btn")
                ) {
                    Icon(imageVector = Icons.Default.Done, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(" looks Good - Generate PDF", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // General status text
            Text(
                text = pageSummary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp),
                textAlign = TextAlign.Center
            )

            // Dynamic paper / page orientation toggles using Filter Chips
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
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
                        text = "Page Layout",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                    modifier = Modifier.size(18.dp)
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
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.testTag("orientation_landscape_chip")
                        )
                    }
                }
            }

            if (viewModel.mode == ProjectMode.ID_CARD) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
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
                            text = "Arrangement",
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
                        .padding(bottom = 10.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "Copy Distribution",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Adjust how many copies of each person are printed on the sheet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Person 1 (A)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${viewModel.quantityA} copies",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val total = viewModel.quantity.toIntOrNull() ?: 16
                                IconButton(
                                    onClick = {
                                        viewModel.updateQuantityA(viewModel.quantityA - 1)
                                    },
                                    enabled = viewModel.quantityA > 1,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    )
                                ) {
                                    Text("-", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                }

                                IconButton(
                                    onClick = {
                                        viewModel.updateQuantityA(viewModel.quantityA + 1)
                                    },
                                    enabled = viewModel.quantityA < total - 1,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    )
                                ) {
                                    Text("+", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Person 2 (B)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${viewModel.quantityB} copies",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
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
                        .padding(bottom = 12.dp),
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
                        style = MaterialTheme.typography.bodyLarge,
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
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val pWidth = if (viewModel.pageOrientation == PageOrientation.PORTRAIT) 21.0f else 29.7f
                    val pHeight = if (viewModel.pageOrientation == PageOrientation.PORTRAIT) 29.7f else 21.0f

                    A4SimulatedSheet(
                        pageLayout = activePage,
                        unitBitmap = unitBmp,
                        marginCm = viewModel.marginCm.toFloatOrNull() ?: 0.5f,
                        cuttingGuidesEnabled = viewModel.cuttingGuidesEnabled,
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

    // Simulated A4 page is drawn centered with fixed aspect ratio based on paper size
    Box(
        modifier = Modifier
            .fillMaxSize()
            .aspectRatio(pageWidthCm / pageHeightCm, matchHeightConstraintsFirst = !isLandscape)
            .shadow(6.dp, RoundedCornerShape(8.dp))
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
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
                // 1. Draw all image units using native canvas for robust transform support
                drawIntoCanvas { canvas ->
                    val nativeCanvas = canvas.nativeCanvas
                    val paint = android.graphics.Paint().apply {
                        isFilterBitmap = true
                        isAntiAlias = true
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
                                nativeCanvas.drawBitmap(activeBmp, null, dstRect, paint)
                                nativeCanvas.restore()
                            } else {
                                val dstRect = android.graphics.RectF(x, y, x + w, y + h)
                                nativeCanvas.drawBitmap(activeBmp, null, dstRect, paint)
                            }
                        }
                    }
                }

                // 2. Draw all overlays/outlines using Compose draw scope
                for (placement in pageLayout.placements) {
                    val x = placement.xCm * pXcm
                    val y = placement.yCm * pXcm

                    val cellW = if (placement.widthCm > 0f) placement.isRotated else pageLayout.isRotated // fallbacks
                    val cellWVal = if (placement.widthCm > 0f) placement.widthCm else pageLayout.cellWidthCm
                    val cellHVal = if (placement.heightCm > 0f) placement.heightCm else pageLayout.cellHeightCm

                    val w = cellWVal * pXcm
                    val h = cellHVal * pXcm

                    // Draw cutting guides if enabled
                    if (cuttingGuidesEnabled) {
                        drawRect(
                            color = Color(0xFF999999),
                            topLeft = Offset(x, y),
                            size = Size(w, h),
                            style = Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                            )
                        )
                    }
                }

                // Draw page safe-margin border markers to aid in cyber café layout inspections
                val marginBorderPx = marginCm * pXcm
                drawRect(
                    color = Color.Red.copy(alpha = 0.2f),
                    topLeft = Offset(marginBorderPx, marginBorderPx),
                    size = Size(size.width - 2 * marginBorderPx, size.height - 2 * marginBorderPx),
                    style = Stroke(
                        width = 0.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f), 0f)
                    )
                )
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
