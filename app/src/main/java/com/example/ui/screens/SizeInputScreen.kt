package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.ProjectMode
import com.example.ui.ProjectViewModel

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SizeInputScreen(
    viewModel: ProjectViewModel,
    onBackClicked: () -> Unit,
    onNextClicked: () -> Unit
) {
    val scrollState = rememberScrollState()

    // Validation checks
    val isWidthValid = remember(viewModel.widthCm) {
        val w = viewModel.widthCm.toFloatOrNull()
        w != null && w >= 0.5f && w <= 29.7f
    }

    val isHeightValid = remember(viewModel.heightCm) {
        val h = viewModel.heightCm.toFloatOrNull()
        h != null && h >= 0.5f && h <= 29.7f
    }

    val isQuantityValid = remember(viewModel.quantity) {
        val q = viewModel.quantity.toIntOrNull()
        q != null && q > 0 && q <= 1000
    }

    var isAdvancedExpanded by remember { mutableStateOf(false) }

    val canProceed = isWidthValid && isHeightValid && isQuantityValid

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.mode == ProjectMode.JOINT) "Joint Size Config" else "Photo Size Config") },
                navigationIcon = {
                    IconButton(onClick = onBackClicked, modifier = Modifier.testTag("size_back_btn")) {
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
                    onClick = onNextClicked,
                    enabled = canProceed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(50.dp)
                        .testTag("size_next_btn")
                ) {
                    Text("Select & Crop Photos", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Step marker indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Step 2 of 5",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                LinearProgressIndicator(
                    progress = { 0.4f },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = "Enter Print Dimensions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )

            // Dynamic Interactive Studio Guide
            DimensionPreview(
                mode = viewModel.mode,
                widthStr = viewModel.widthCm,
                heightStr = viewModel.heightCm,
                spacingStr = viewModel.spacingCm,
                cuttingGuidesEnabled = viewModel.cuttingGuidesEnabled,
                marginStr = viewModel.marginCm,
                modifier = Modifier.fillMaxWidth()
            )

            // Preset Chips Selection
            Text(
                text = "Quick Presets:",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val presetChips = when (viewModel.mode) {
                ProjectMode.JOINT -> {
                    listOf(
                        PresetSize("Standard Joint (6×4.5 cm)", 6.0f, 4.5f),
                        PresetSize("Small Joint (5×3.5 cm)", 5.0f, 3.5f),
                        PresetSize("Custom Joint", 0.0f, 0.0f)
                    )
                }
                ProjectMode.ID_CARD -> {
                    listOf(
                        PresetSize("Aadhaar Card", 8.5f, 5.5f),
                        PresetSize("PAN Card", 8.5f, 5.4f),
                        PresetSize("Voter ID / DL", 8.5f, 5.4f),
                        PresetSize("Custom ID Card", 0.0f, 0.0f)
                    )
                }
                else -> {
                    listOf(
                        PresetSize("Passport (India)", 3.5f, 4.5f),
                        PresetSize("PAN / Voter ID Photo", 2.5f, 3.5f),
                        PresetSize("US Visa (2×2')", 5.08f, 5.08f),
                        PresetSize("Stamp size", 2.0f, 2.5f),
                        PresetSize("Custom Size", 0.0f, 0.0f)
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presetChips.forEach { chip ->
                    val matchesAnyOtherPreset = presetChips.any { other ->
                        other.width > 0f && viewModel.widthCm == other.width.toString() && viewModel.heightCm == other.height.toString()
                    }
                    val isSelected = if (chip.width == 0f) {
                        !matchesAnyOtherPreset || (viewModel.widthCm.isEmpty() && viewModel.heightCm.isEmpty())
                    } else {
                        viewModel.widthCm == chip.width.toString() && viewModel.heightCm == chip.height.toString()
                    }

                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (chip.width > 0f) {
                                viewModel.selectPreset(chip.name, chip.width, chip.height)
                            } else {
                                viewModel.widthCm = ""
                                viewModel.heightCm = ""
                            }
                            viewModel.pushHistoryState()
                        },
                        label = { Text(chip.name) },
                        modifier = Modifier.testTag("preset_chip_${chip.name.replace(" ", "_")}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Dimensions inputs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = viewModel.widthCm,
                    onValueChange = { 
                        viewModel.widthCm = it 
                        viewModel.pushHistoryStateDebounced()
                    },
                    label = { Text("Width (cm)") },
                    isError = !isWidthValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f).testTag("size_width_field"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )

                OutlinedTextField(
                    value = viewModel.heightCm,
                    onValueChange = { 
                        viewModel.heightCm = it 
                        viewModel.pushHistoryStateDebounced()
                    },
                    label = { Text("Height (cm)") },
                    isError = !isHeightValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f).testTag("size_height_field"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            if (!isWidthValid && viewModel.widthCm.isNotEmpty()) {
                Text(
                    text = "Width must be between 0.5 cm and 29.7 cm.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!isHeightValid && viewModel.heightCm.isNotEmpty()) {
                Text(
                    text = "Height must be between 0.5 cm and 29.7 cm.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Print Quantity Card
            OutlinedTextField(
                value = viewModel.quantity,
                onValueChange = { 
                    viewModel.quantity = it 
                    viewModel.pushHistoryStateDebounced()
                },
                label = { Text("Number of Copies") },
                isError = !isQuantityValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("size_quantity_field"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )

            if (!isQuantityValid && viewModel.quantity.isNotEmpty()) {
                Text(
                    text = "Enter a valid quantity (between 1 and 1000).",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (viewModel.isLayoutTooLargeError) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().testTag("layout_engine_error_card")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = viewModel.layoutEngineErrorText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // ADVANCED SETTINGS HEADER
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isAdvancedExpanded = !isAdvancedExpanded }
                    .testTag("advanced_settings_card"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Advanced Layout Options", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    }
                    Icon(
                        imageVector = if (isAdvancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle expandable options"
                    )
                }
            }

            // Collapsible panels
            AnimatedVisibility(
                visible = isAdvancedExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Page Margins Config
                        OutlinedTextField(
                            value = viewModel.marginCm,
                            onValueChange = { 
                                viewModel.marginCm = it 
                                viewModel.pushHistoryStateDebounced()
                            },
                            label = { Text("A4 Border Margins (cm)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth().testTag("adv_margin_field")
                        )

                        // Spacing gap Config
                        OutlinedTextField(
                            value = viewModel.spacingCm,
                            onValueChange = { 
                                viewModel.spacingCm = it 
                                viewModel.pushHistoryStateDebounced()
                            },
                            label = { Text("Gap between photos (cm)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth().testTag("adv_spacing_field")
                        )

                        // DPI Selection Option
                        Column {
                            Text("Print Resolution Density (DPI):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(150, 300, 600).forEach { dpiOption ->
                                    val isSelected = viewModel.dpi == dpiOption
                                    InputChip(
                                        selected = isSelected,
                                        onClick = { 
                                            viewModel.dpi = dpiOption 
                                            viewModel.pushHistoryState()
                                        },
                                        label = { Text("$dpiOption DPI") },
                                        modifier = Modifier.weight(1f).testTag("adv_dpi_chip_$dpiOption")
                                    )
                                }
                            }
                        }

                        // Cutting outline Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Cutting Guide Borders", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("Draw thin dashed boundary lines around photos to simplify cutting.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = viewModel.cuttingGuidesEnabled,
                                onCheckedChange = { 
                                    viewModel.cuttingGuidesEnabled = it 
                                    viewModel.pushHistoryState()
                                },
                                modifier = Modifier.testTag("adv_cutting_switch")
                            )
                        }

                        // Auto rotation density optimizer Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Maximize Paper Layout Pack", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("Check rotated fitting configurations and choose which packs more photos per A4 page.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = viewModel.allowRotation,
                                onCheckedChange = { 
                                    viewModel.allowRotation = it 
                                    viewModel.pushHistoryState()
                                },
                                modifier = Modifier.testTag("adv_rotation_switch")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DimensionPreview(
    mode: ProjectMode,
    widthStr: String,
    heightStr: String,
    spacingStr: String,
    cuttingGuidesEnabled: Boolean,
    marginStr: String,
    modifier: Modifier = Modifier
) {
    val w = widthStr.toFloatOrNull() ?: 0f
    val h = heightStr.toFloatOrNull() ?: 0f
    val validW = if (w in 0.1f..40.0f) w else 3.5f
    val validH = if (h in 0.1f..40.0f) h else 4.5f
    val aspect = validW / validH

    val spacing = spacingStr.toFloatOrNull() ?: 0.2f
    val margin = marginStr.toFloatOrNull() ?: 1.0f

    var selectedTab by remember { mutableStateOf(0) } // 0 = Single Unit Spec, 1 = A4 Sheet Grid Spacing

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dimension_preview_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = when (mode) {
                    ProjectMode.ID_CARD -> "DOCUMENT PREVIEW"
                    ProjectMode.JOINT -> "JOINT PORTRAIT PREVIEW"
                    ProjectMode.SINGLE -> "PASSPORT & VISA PHOTO PREVIEW"
                    ProjectMode.MULTI_PERSON -> "MULTI-PERSON PRINT PREVIEW"
                },
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Dynamic Tab Selector for Single Spec vs A4 Grid Spacing
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Single Unit Spec", "A4 Sheet Grid Spacing").forEachIndexed { index, title ->
                    val isSelected = selectedTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            )
                            .clickable { selectedTab = index }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Viewport Box
            Box(
                modifier = Modifier
                    .height(200.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (selectedTab == 0) {
                    // Interactive aspect-ratio mock-up card
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(aspect)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        when (mode) {
                            ProjectMode.ID_CARD -> DummyIdCardContent()
                            ProjectMode.JOINT -> DummyJointPhotoContent()
                            ProjectMode.SINGLE -> DummyPassportPhotoContent()
                            ProjectMode.MULTI_PERSON -> DummyPassportPhotoContent()
                        }
                    }
                } else {
                    // Interactive A4 Sheet Grid preview showing precise Spacing and Cutting Guides
                    A4SheetPreviewCanvas(
                        validW = validW,
                        validH = validH,
                        spacing = spacing,
                        margin = margin,
                        cuttingGuidesEnabled = cuttingGuidesEnabled,
                        mode = mode
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Dimension stats badge bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Size Badge
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$validW × $validH cm",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Spacing Badge
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Gap: ${spacing} cm",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun A4SheetPreviewCanvas(
    validW: Float,
    validH: Float,
    spacing: Float,
    margin: Float,
    cuttingGuidesEnabled: Boolean,
    mode: ProjectMode,
    modifier: Modifier = Modifier
) {
    val a4WidthCm = 21.0f
    val a4HeightCm = 29.7f

    Box(
        modifier = modifier
            .fillMaxHeight()
            .aspectRatio(a4WidthCm / a4HeightCm)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
    ) {
        val strokeColor = MaterialTheme.colorScheme.primary
        val outlineColor = MaterialTheme.colorScheme.outlineVariant
        val onSurfaceColor = MaterialTheme.colorScheme.onSurface
        val secondaryColor = MaterialTheme.colorScheme.secondary

        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val scale = size.width / a4WidthCm

            val marginPx = margin * scale
            val spacingPx = spacing * scale
            val photoWPx = validW * scale
            val photoHPx = validH * scale

            // Draw Printable Bounds Margin Guide (subtle light gray dashed line)
            drawRect(
                color = outlineColor.copy(alpha = 0.35f),
                topLeft = Offset(marginPx, marginPx),
                size = Size(size.width - 2 * marginPx, size.height - 2 * marginPx),
                style = Stroke(
                    width = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                )
            )

            // Let's compute columns and rows that fit in the printable area
            val cols = ((a4WidthCm - 2 * margin + spacing) / (validW + spacing)).toInt().coerceAtLeast(1)
            val rows = ((a4HeightCm - 2 * margin + spacing) / (validH + spacing)).toInt().coerceAtLeast(1)

            // Limit columns and rows visually to avoid rendering off-canvas
            val maxCols = cols.coerceAtMost(10)
            val maxRows = rows.coerceAtMost(15)

            // Draw each photo rectangle
            var cellCount = 0
            for (r in 0 until maxRows) {
                for (c in 0 until maxCols) {
                    val x = marginPx + c * (photoWPx + spacingPx)
                    val y = marginPx + r * (photoHPx + spacingPx)

                    // Check if photo is within sheet boundaries
                    if (x + photoWPx <= size.width && y + photoHPx <= size.height) {
                        cellCount++
                        val totalCellsEstimate = cols * rows
                        val isPersonA = mode == ProjectMode.MULTI_PERSON && (cellCount <= totalCellsEstimate / 2)

                        // Draw photo base rectangle (representing photo print out)
                        drawRect(
                            color = when (mode) {
                                ProjectMode.JOINT -> Color(0xFFE8F5E9) // Light green representation
                                ProjectMode.ID_CARD -> Color(0xFFE3F2FD) // Light blue representation
                                ProjectMode.SINGLE -> Color(0xFFFFF3E0) // Light orange representation
                                ProjectMode.MULTI_PERSON -> if (isPersonA) Color(0xFFFFF3E0) else Color(0xFFE8F5E9)
                            },
                            topLeft = Offset(x, y),
                            size = Size(photoWPx, photoHPx)
                        )

                        // Draw neat inner border
                        drawRect(
                            color = strokeColor.copy(alpha = 0.2f),
                            topLeft = Offset(x, y),
                            size = Size(photoWPx, photoHPx),
                            style = Stroke(width = 1f)
                        )

                        // Draw miniature stylized content depending on mode
                        when (mode) {
                            ProjectMode.SINGLE -> {
                                // Draw miniature passport head silhouette
                                val headRadius = photoWPx * 0.18f
                                val headX = x + photoWPx * 0.5f
                                val headY = y + photoHPx * 0.35f
                                drawCircle(
                                    color = strokeColor.copy(alpha = 0.4f),
                                    radius = headRadius,
                                    center = Offset(headX, headY)
                                )
                                // Draw shoulder arc
                                drawOval(
                                    color = strokeColor.copy(alpha = 0.4f),
                                    topLeft = Offset(headX - photoWPx * 0.25f, headY + headRadius * 0.8f),
                                    size = Size(photoWPx * 0.5f, photoHPx * 0.4f)
                                )
                            }
                            ProjectMode.MULTI_PERSON -> {
                                // Draw single head silhouette, colored depending on Person A vs Person B
                                val headRadius = photoWPx * 0.18f
                                val headX = x + photoWPx * 0.5f
                                val headY = y + photoHPx * 0.35f
                                val silhouetteColor = if (isPersonA) Color(0xFFE65100).copy(alpha = 0.5f) else Color(0xFF2E7D32).copy(alpha = 0.5f)
                                drawCircle(
                                    color = silhouetteColor,
                                    radius = headRadius,
                                    center = Offset(headX, headY)
                                )
                                drawOval(
                                    color = silhouetteColor,
                                    topLeft = Offset(headX - photoWPx * 0.25f, headY + headRadius * 0.8f),
                                    size = Size(photoWPx * 0.5f, photoHPx * 0.4f)
                                )
                            }
                            ProjectMode.JOINT -> {
                                // Draw joint photo division line
                                drawLine(
                                    color = strokeColor.copy(alpha = 0.15f),
                                    start = Offset(x + photoWPx * 0.5f, y),
                                    end = Offset(x + photoWPx * 0.5f, y + photoHPx),
                                    strokeWidth = 1f
                                )
                                // Draw Person A silhouette (Left)
                                val headRadiusA = photoWPx * 0.13f
                                val headXA = x + photoWPx * 0.25f
                                val headYA = y + photoHPx * 0.38f
                                drawCircle(
                                    color = Color(0xFF43A047).copy(alpha = 0.5f),
                                    radius = headRadiusA,
                                    center = Offset(headXA, headYA)
                                )
                                drawOval(
                                    color = Color(0xFF43A047).copy(alpha = 0.5f),
                                    topLeft = Offset(headXA - photoWPx * 0.18f, headYA + headRadiusA * 0.8f),
                                    size = Size(photoWPx * 0.36f, photoHPx * 0.35f)
                                )

                                // Draw Person B silhouette (Right)
                                val headRadiusB = photoWPx * 0.13f
                                val headXB = x + photoWPx * 0.75f
                                val headYB = y + photoHPx * 0.38f
                                drawCircle(
                                    color = Color(0xFFFB8C00).copy(alpha = 0.5f),
                                    radius = headRadiusB,
                                    center = Offset(headXB, headYB)
                                )
                                drawOval(
                                    color = Color(0xFFFB8C00).copy(alpha = 0.5f),
                                    topLeft = Offset(headXB - photoWPx * 0.18f, headYB + headRadiusB * 0.8f),
                                    size = Size(photoWPx * 0.36f, photoHPx * 0.35f)
                                )
                            }
                            ProjectMode.ID_CARD -> {
                                // Draw micro ID Card details
                                val avatarW = photoWPx * 0.25f
                                val avatarH = photoHPx * 0.6f
                                drawRect(
                                    color = strokeColor.copy(alpha = 0.15f),
                                    topLeft = Offset(x + photoWPx * 0.08f, y + photoHPx * 0.2f),
                                    size = Size(avatarW, avatarH)
                                )
                                // Draw lines
                                for (l in 0 until 3) {
                                    val lineY = y + photoHPx * (0.25f + l * 0.18f)
                                    drawLine(
                                        color = onSurfaceColor.copy(alpha = 0.25f),
                                        start = Offset(x + photoWPx * 0.4f, lineY),
                                        end = Offset(x + photoWPx * 0.88f, lineY),
                                        strokeWidth = 1.5f
                                    )
                                }
                            }
                        }

                        // Draw Cutting Guides Borders if checked
                        if (cuttingGuidesEnabled) {
                            drawRect(
                                color = Color.Gray.copy(alpha = 0.7f),
                                topLeft = Offset(x, y),
                                size = Size(photoWPx, photoHPx),
                                style = Stroke(
                                    width = 0.8f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f), 0f)
                                )
                            )
                        }
                    }
                }
            }

            // Highlight the Spacing Gap visually (draw a bracket between first and second photo, if cols > 1)
            if (cols > 1) {
                val x0 = marginPx + photoWPx
                val x1 = marginPx + photoWPx + spacingPx
                val yMid = marginPx + photoHPx / 2.5f

                // High-visibility bracket highlighting spacing
                if (spacingPx > 1.5f) {
                    // Draw a highlight bar/arrow
                    drawLine(
                        color = secondaryColor,
                        start = Offset(x0, yMid),
                        end = Offset(x1, yMid),
                        strokeWidth = 2f
                    )
                    // Draw end T-bars
                    drawLine(
                        color = secondaryColor,
                        start = Offset(x0, yMid - 6f),
                        end = Offset(x0, yMid + 6f),
                        strokeWidth = 2f
                    )
                    drawLine(
                        color = secondaryColor,
                        start = Offset(x1, yMid - 6f),
                        end = Offset(x1, yMid + 6f),
                        strokeWidth = 2f
                    )
                } else {
                    // Draw a indicator line showing they are touching or almost touching
                    drawLine(
                        color = secondaryColor,
                        start = Offset(x0, yMid - 8f),
                        end = Offset(x0, yMid + 8f),
                        strokeWidth = 1.5f
                    )
                }
            } else if (rows > 1) {
                // If only 1 col but multiple rows, highlight vertical spacing
                val y0 = marginPx + photoHPx
                val y1 = marginPx + photoHPx + spacingPx
                val xMid = marginPx + photoWPx / 2.5f

                if (spacingPx > 1.5f) {
                    drawLine(
                        color = secondaryColor,
                        start = Offset(xMid, y0),
                        end = Offset(xMid, y1),
                        strokeWidth = 2f
                    )
                    drawLine(
                        color = secondaryColor,
                        start = Offset(xMid - 6f, y0),
                        end = Offset(xMid + 6f, y0),
                        strokeWidth = 2f
                    )
                    drawLine(
                        color = secondaryColor,
                        start = Offset(xMid - 6f, y1),
                        end = Offset(xMid + 6f, y1),
                        strokeWidth = 2f
                    )
                }
            }
        }
    }
}

@Composable
fun DummyPassportPhotoContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E88E5)) // Classic passport blue backdrop
    ) {
        // Face positioning guide oval (dotted)
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawOval(
                color = Color.White.copy(alpha = 0.35f),
                topLeft = Offset(cx - size.width * 0.25f, cy - size.height * 0.35f),
                size = Size(size.width * 0.5f, size.height * 0.7f),
                style = Stroke(
                    width = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            )
            // Draw crosshair/alignment lines
            drawLine(
                color = Color.White.copy(alpha = 0.2f),
                start = Offset(0f, cy),
                end = Offset(size.width, cy),
                strokeWidth = 1f
            )
            drawLine(
                color = Color.White.copy(alpha = 0.2f),
                start = Offset(cx, 0f),
                end = Offset(cx, size.height),
                strokeWidth = 1f
            )
        }

        // Silhouette/Avatar centered
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .fillMaxHeight(0.75f)
                    .aspectRatio(1f)
            )
        }
    }
}

@Composable
fun DummyIdCardContent() {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    )
                )
            )
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Avatar placeholder inside card
        Box(
            modifier = Modifier
                .weight(0.35f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxSize(0.75f)
            )
        }

        // Right: ID Card lines & details
        Column(
            modifier = Modifier
                .weight(0.65f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header / Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mini chip decoration
                Box(
                    modifier = Modifier
                        .size(width = 16.dp, height = 12.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFFFFD54F)) // Gold chip color
                )
                // Text label for ID Card
                Box(
                    modifier = Modifier
                        .height(6.dp)
                        .width(32.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), RoundedCornerShape(50))
                )
            }

            // Fake texts lines
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Name line
                Box(
                    modifier = Modifier
                        .height(6.dp)
                        .fillMaxWidth(0.9f)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), RoundedCornerShape(50))
                )
                // Number line
                Box(
                    modifier = Modifier
                        .height(4.dp)
                        .fillMaxWidth(0.6f)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), RoundedCornerShape(50))
                )
                // Expiry line
                Box(
                    modifier = Modifier
                        .height(4.dp)
                        .fillMaxWidth(0.4f)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), RoundedCornerShape(50))
                )
            }

            // Bottom barcode design
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val barcodePattern = listOf(2, 4, 1, 3, 2, 4, 1, 2, 3)
                barcodePattern.forEach { weight ->
                    Box(
                        modifier = Modifier
                            .height(10.dp)
                            .weight(weight.toFloat())
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    )
                }
            }
        }
    }
}

@Composable
fun DummyJointPhotoContent() {
    Row(
        modifier = Modifier.fillMaxSize()
    ) {
        // Left Photo: Person A
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(0xFFE8F5E9)), // Light green backdrop for Person A
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Face,
                    contentDescription = null,
                    tint = Color(0xFF43A047),
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = "Photo A",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Joint Vertical Divider line
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(Color.Black.copy(alpha = 0.2f))
        )

        // Right Photo: Person B
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color(0xFFFFF3E0)), // Light orange backdrop for Person B
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Face,
                    contentDescription = null,
                    tint = Color(0xFFFB8C00),
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = "Photo B",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFE65100),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

data class PresetSize(
    val name: String,
    val width: Float,
    val height: Float
)
