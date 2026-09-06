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
import com.example.domain.model.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import com.example.ui.ProjectViewModel

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SizeInputScreen(
    viewModel: ProjectViewModel,
    onBackClicked: () -> Unit,
    onNextClicked: () -> Unit
) {
    val scrollState = rememberScrollState()

    val isBatchMode = viewModel.mode == ProjectMode.BATCH_PAPER_SAVER

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

    val isBatchValid = remember(viewModel.batchItems.size, viewModel.batchItems.map { "${it.quantity}_${it.widthCm}_${it.heightCm}" }) {
        viewModel.batchItems.isNotEmpty() && viewModel.batchItems.all { it.quantity > 0 && it.widthCm >= 0.5f && it.heightCm >= 0.5f }
    }

    var isAdvancedExpanded by remember { mutableStateOf(false) }

    val canProceed = if (isBatchMode) isBatchValid else (isWidthValid && isHeightValid && isQuantityValid)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (viewModel.mode == ProjectMode.BATCH_PAPER_SAVER) "Mixed Photo Batch Studio"
                        else if (viewModel.mode == ProjectMode.JOINT) "Joint Size Config"
                        else "Photo Size Config"
                    )
                },
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

            if (isBatchMode) {
                MixedBatchStudioContent(
                    viewModel = viewModel
                )
            } else {
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

                        if (viewModel.cuttingGuidesEnabled) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Border Color", style = MaterialTheme.typography.bodyMedium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val colors = listOf(
                                        "Black" to 0xFF000000.toInt(),
                                        "Gray" to 0xFF999999.toInt(),
                                        "Red" to 0xFFFF0000.toInt(),
                                        "Blue" to 0xFF0000FF.toInt()
                                    )
                                    colors.forEach { (name, colorInt) ->
                                        androidx.compose.foundation.layout.Box(
                                            modifier = Modifier
                                                .padding(horizontal = 4.dp)
                                                .size(24.dp)
                                                .background(
                                                    androidx.compose.ui.graphics.Color(colorInt),
                                                    androidx.compose.foundation.shape.CircleShape
                                                )
                                                .border(
                                                    if (viewModel.cuttingGuideColor == colorInt) 2.dp else 1.dp,
                                                    if (viewModel.cuttingGuideColor == colorInt) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Gray,
                                                    androidx.compose.foundation.shape.CircleShape
                                                )
                                                .clickable {
                                                    viewModel.cuttingGuideColor = colorInt
                                                    viewModel.pushHistoryState()
                                                }
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Border Style", style = MaterialTheme.typography.bodyMedium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.material3.OutlinedButton(
                                        onClick = { viewModel.cuttingGuideStyle = "dashed"; viewModel.pushHistoryState() },
                                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = if (viewModel.cuttingGuideStyle == "dashed") MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                                    ) { Text("Dashed") }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    androidx.compose.material3.OutlinedButton(
                                        onClick = { viewModel.cuttingGuideStyle = "solid"; viewModel.pushHistoryState() },
                                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = if (viewModel.cuttingGuideStyle == "solid") MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                                    ) { Text("Solid") }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Border Thickness", style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.width(16.dp))
                                androidx.compose.material3.Slider(
                                    value = viewModel.cuttingGuideThicknessPt,
                                    onValueChange = { viewModel.cuttingGuideThicknessPt = it },
                                    onValueChangeFinished = { viewModel.pushHistoryState() },
                                    valueRange = 0.5f..5.0f,
                                    steps = 8,
                                    modifier = Modifier.weight(1f)
                                )
                            }
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
                    ProjectMode.BATCH_PAPER_SAVER -> "MIXED MULTI-PHOTO BATCH PREVIEW"
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
                            ProjectMode.BATCH_PAPER_SAVER -> DummyPassportPhotoContent()
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
                                ProjectMode.BATCH_PAPER_SAVER -> Color(0xFFF3E5F5) // Light purple batch representation
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
                            ProjectMode.BATCH_PAPER_SAVER -> {
                                val headRadius = photoWPx * 0.15f
                                val headX = x + photoWPx * 0.5f
                                val headY = y + photoHPx * 0.35f
                                drawCircle(
                                    color = Color(0xFF7B1FA2).copy(alpha = 0.5f),
                                    radius = headRadius,
                                    center = Offset(headX, headY)
                                )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MixedBatchStudioContent(
    viewModel: ProjectViewModel
) {
    val totalPhotos = viewModel.getTotalBatchPhotosCount()
    val pages = viewModel.computedPages
    val selectedPaper = viewModel.selectedPaperSpec

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title & Description
        Column {
            Text(
                text = "Mixed Photo Batch Studio",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Combine multiple photo sizes on 1 sheet to eliminate photo paper waste. All photos of the same height sit in straight cutting rows!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 1. One-Tap Quick Templates (Matches customer request 8 Passport + 4 Stamp = 12 pcs!)
        Text(
            text = "⚡ Quick Order Presets:",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            viewModel.batchTemplates.forEach { template ->
                val isSelected = viewModel.batchItems.size == template.items.size &&
                        viewModel.batchItems.zip(template.items).all { (actual, expected) ->
                            actual.widthCm == expected.widthCm &&
                            actual.heightCm == expected.heightCm &&
                            actual.quantity == expected.quantity
                        }

                FilterChip(
                    selected = isSelected,
                    onClick = {
                        viewModel.applyBatchTemplate(template)
                    },
                    label = {
                        Text("${template.title} (${template.totalCount} pcs)")
                    },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else {
                        { Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) }
                    }
                )
            }
        }

        // 2. Paper Format & Orientation Bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            )
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Photo Paper Format",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${selectedPaper.widthCm} × ${selectedPaper.heightCm} cm",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PaperSizeSpec.DEFAULT_PAPERS.forEach { paper ->
                        FilterChip(
                            selected = selectedPaper.id == paper.id,
                            onClick = {
                                viewModel.selectedPaperSpec = paper
                                viewModel.computeCurrentLayout()
                                viewModel.pushHistoryState()
                            },
                            label = { Text(paper.name) },
                            leadingIcon = {
                                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }

                    // Orientation toggles
                    val isPortrait = viewModel.pageOrientation == PageOrientation.PORTRAIT
                    FilterChip(
                        selected = isPortrait,
                        onClick = {
                            if (!isPortrait) {
                                viewModel.pageOrientation = PageOrientation.PORTRAIT
                                viewModel.computeCurrentLayout()
                                viewModel.pushHistoryState()
                            }
                        },
                        label = { Text("Portrait") },
                        leadingIcon = {
                            Icon(Icons.Default.CropPortrait, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                    FilterChip(
                        selected = !isPortrait,
                        onClick = {
                            if (isPortrait) {
                                viewModel.pageOrientation = PageOrientation.LANDSCAPE
                                viewModel.computeCurrentLayout()
                                viewModel.pushHistoryState()
                            }
                        },
                        label = { Text("Landscape") },
                        leadingIcon = {
                            Icon(Icons.Default.CropRotate, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                }
            }
        }

        // 3. Live Order Summary & Paper Savings Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$totalPhotos PHOTOS TOTAL",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${pages.size} Sheet${if (pages.size > 1) "s" else ""} Needed",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                val breakdownText = viewModel.batchItems.joinToString(" + ") {
                    "${it.quantity} pcs ${it.label} (${it.widthCm}×${it.heightCm}cm)"
                }
                Text(
                    text = breakdownText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCut,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Straight cutting lines guaranteed: Each size sits in uniform rows for 1-cut trimming.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // 4. Interactive Live Sheet Canvas Preview
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "SHEET LAYOUT PREVIEW",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val paperW = if (viewModel.pageOrientation == PageOrientation.PORTRAIT) minOf(selectedPaper.widthCm, selectedPaper.heightCm) else maxOf(selectedPaper.widthCm, selectedPaper.heightCm)
                val paperH = if (viewModel.pageOrientation == PageOrientation.PORTRAIT) maxOf(selectedPaper.widthCm, selectedPaper.heightCm) else minOf(selectedPaper.widthCm, selectedPaper.heightCm)
                val marginCm = viewModel.marginCm.toFloatOrNull() ?: 0.5f
                val spacingCm = viewModel.spacingCm.toFloatOrNull() ?: 0.2f

                Box(
                    modifier = Modifier
                        .height(230.dp)
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MixedBatchSheetPreviewCanvas(
                        pageLayout = pages.firstOrNull(),
                        paperWidthCm = paperW,
                        paperHeightCm = paperH,
                        marginCm = marginCm,
                        spacingCm = spacingCm,
                        cuttingGuidesEnabled = viewModel.cuttingGuidesEnabled
                    )
                }

                Text(
                    text = "Paper: ${selectedPaper.name} (${paperW}×${paperH} cm) • Margins: ${marginCm}cm • Spacing: ${spacingCm}cm",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        // 5. Configured Photo Size Groups
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Photo Size Groups (${viewModel.batchItems.size}):",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            FilledTonalButton(
                onClick = {
                    viewModel.addBatchItem("Stamp Size", 2.0f, 2.5f, 4)
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Size", style = MaterialTheme.typography.labelMedium)
            }
        }

        // Render each BatchItem Card
        viewModel.batchItems.forEachIndexed { index, item ->
            BatchItemCard(
                index = index,
                item = item,
                canDelete = viewModel.batchItems.size > 1,
                onUpdate = { label, w, h, q ->
                    viewModel.updateBatchItem(item.id, label, w, h, q)
                },
                onDelete = {
                    viewModel.removeBatchItem(item.id)
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BatchItemCard(
    index: Int,
    item: BatchItem,
    canDelete: Boolean,
    onUpdate: (label: String, widthCm: Float, heightCm: Float, quantity: Int) -> Unit,
    onDelete: () -> Unit
) {
    var widthText by remember(item.widthCm) { mutableStateOf(item.widthCm.toString()) }
    var heightText by remember(item.heightCm) { mutableStateOf(item.heightCm.toString()) }
    var qtyText by remember(item.quantity) { mutableStateOf(item.quantity.toString()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row: Name & Delete Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (canDelete) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete this size group",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Quick Presets Chips for this size group
            val sizePresets = listOf(
                Triple("India Passport", 3.5f, 4.5f),
                Triple("Stamp Size", 2.0f, 2.5f),
                Triple("US Visa (2×2\")", 5.08f, 5.08f),
                Triple("PAN / Voter Photo", 2.5f, 3.5f)
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                sizePresets.forEach { (name, w, h) ->
                    val isCurrent = item.widthCm == w && item.heightCm == h
                    FilterChip(
                        selected = isCurrent,
                        onClick = {
                            widthText = w.toString()
                            heightText = h.toString()
                            onUpdate(name, w, h, item.quantity)
                        },
                        label = { Text(name, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // Dimensions Row (Width & Height)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = widthText,
                    onValueChange = { input ->
                        widthText = input
                        val f = input.toFloatOrNull()
                        if (f != null && f >= 0.5f && f <= 30f) {
                            onUpdate(item.label, f, item.heightCm, item.quantity)
                        }
                    },
                    label = { Text("Width (cm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                OutlinedTextField(
                    value = heightText,
                    onValueChange = { input ->
                        heightText = input
                        val f = input.toFloatOrNull()
                        if (f != null && f >= 0.5f && f <= 30f) {
                            onUpdate(item.label, item.widthCm, f, item.quantity)
                        }
                    },
                    label = { Text("Height (cm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            // Quantity Stepper Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Number of Copies:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            val newQ = (item.quantity - 1).coerceAtLeast(1)
                            qtyText = newQ.toString()
                            onUpdate(item.label, item.widthCm, item.heightCm, newQ)
                        },
                        enabled = item.quantity > 1,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                    }

                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { input ->
                            qtyText = input
                            val q = input.toIntOrNull()
                            if (q != null && q in 1..200) {
                                onUpdate(item.label, item.widthCm, item.heightCm, q)
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(60.dp),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    )

                    FilledTonalIconButton(
                        onClick = {
                            val newQ = (item.quantity + 1).coerceAtMost(200)
                            qtyText = newQ.toString()
                            onUpdate(item.label, item.widthCm, item.heightCm, newQ)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                    }

                    // Quick buttons
                    Button(
                        onClick = {
                            val newQ = (item.quantity + 4).coerceAtMost(200)
                            qtyText = newQ.toString()
                            onUpdate(item.label, item.widthCm, item.heightCm, newQ)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("+4", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun MixedBatchSheetPreviewCanvas(
    pageLayout: PageLayout?,
    paperWidthCm: Float,
    paperHeightCm: Float,
    marginCm: Float,
    spacingCm: Float,
    cuttingGuidesEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    if (paperWidthCm <= 0f || paperHeightCm <= 0f) return

    Box(
        modifier = modifier
            .fillMaxHeight()
            .aspectRatio(paperWidthCm / paperHeightCm)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
    ) {
        val strokeColor = MaterialTheme.colorScheme.primary
        val outlineColor = MaterialTheme.colorScheme.outlineVariant

        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val scale = size.width / paperWidthCm

            val marginPx = marginCm * scale

            // Printable Bounds Margin Guide (light gray dashed line)
            drawRect(
                color = outlineColor.copy(alpha = 0.35f),
                topLeft = Offset(marginPx, marginPx),
                size = Size(size.width - 2 * marginPx, size.height - 2 * marginPx),
                style = Stroke(
                    width = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f), 0f)
                )
            )

            if (pageLayout != null) {
                for (placement in pageLayout.placements) {
                    val x = placement.xCm * scale
                    val y = placement.yCm * scale
                    val w = placement.widthCm * scale
                    val h = placement.heightCm * scale

                    if (x + w <= size.width + 1f && y + h <= size.height + 1f) {
                        // Color code based on size
                        val cellColor = when {
                            placement.widthCm >= 3.0f && placement.heightCm >= 4.0f -> Color(0xFFFFF3E0) // Light orange for passport
                            placement.widthCm <= 2.5f && placement.heightCm <= 3.0f -> Color(0xFFF3E5F5) // Light purple for stamp
                            placement.widthCm >= 4.5f -> Color(0xFFE3F2FD) // Light blue for visa/joint
                            else -> Color(0xFFE8F5E9) // Light green
                        }

                        drawRect(
                            color = cellColor,
                            topLeft = Offset(x, y),
                            size = Size(w, h)
                        )

                        // Draw neat inner border
                        drawRect(
                            color = strokeColor.copy(alpha = 0.3f),
                            topLeft = Offset(x, y),
                            size = Size(w, h),
                            style = Stroke(width = 0.8f)
                        )

                        // Silhouette avatar
                        val headRadius = (w * 0.16f).coerceAtLeast(3f)
                        val headX = x + w * 0.5f
                        val headY = y + h * 0.35f
                        drawCircle(
                            color = strokeColor.copy(alpha = 0.35f),
                            radius = headRadius,
                            center = Offset(headX, headY)
                        )

                        // Cutting guides
                        if (cuttingGuidesEnabled) {
                            drawRect(
                                color = Color.Gray.copy(alpha = 0.7f),
                                topLeft = Offset(x, y),
                                size = Size(w, h),
                                style = Stroke(
                                    width = 0.8f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.5f, 2.5f), 0f)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

