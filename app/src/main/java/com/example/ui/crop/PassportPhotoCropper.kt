package com.example.ui.crop

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.BitmapUtils
import kotlin.math.max
import kotlin.math.min
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun PassportPhotoCropper(
    sourceBitmap: Bitmap,
    aspectRatio: Float, // W / H (e.g. 3.5 / 4.5 = 0.777f)
    showHeadSilhouette: Boolean = true,
    onCropCompleted: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    var rawBitmap by remember { mutableStateOf(sourceBitmap) }

    // Color Replacement State
    var targetReplacementBgColor by remember { mutableStateOf(Color.WHITE) } // Replacement color
    var colorToReplace by remember { mutableStateOf<Int?>(null) } // Color sampled to be replaced
    var replaceTolerance by remember { mutableStateOf(0.18f) } // default L2 tolerance (around 80/441)
    var replaceSeedFraction by remember { mutableStateOf<Pair<Float, Float>?>(null) } // Fraction coordinates of sampled pixel

    // Studio Enhancements (Brightness, Contrast, Saturation)
    var brightness by remember { mutableStateOf(0.0f) }
    var contrast by remember { mutableStateOf(1.0f) }
    var saturation by remember { mutableStateOf(1.0f) }
    var isBlackAndWhite by remember { mutableStateOf(false) }
    var isSilhouetteVisible by remember { mutableStateOf(showHeadSilhouette) }

    // ML Kit Subject Segmentation State
    var useMLKitSegmentation by remember { mutableStateOf(false) }
    var mlKitForegroundBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isSegmenting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // UI Tab selection: 0 = Adjust Frame, 1 = Erase/Replace BG, 2 = Enhancements
    var activeTab by remember { mutableStateOf(0) }
    var isSamplingMode by remember { mutableStateOf(false) } // Eye dropper activation status

    // Navigation and status
    var scale by remember { mutableStateOf(1.0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    var viewWidth by remember { mutableStateOf(0f) }
    var viewHeight by remember { mutableStateOf(0f) }

    // Visual bounds
    val isPortrait = aspectRatio < 1f

    // Quick Rotate 90 degree logic
    val rotateBitmap = {
        val matrix = Matrix().apply { postRotate(90f) }
        val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
        rawBitmap = rotated
        // Reset scale and offset on rotation
        scale = 1.0f
        offset = Offset.Zero
    }

    // Create a smaller thumbnail of rawBitmap for preview, e.g. max dimension of 800
    val previewThumbnailBitmap = remember(rawBitmap) {
        val maxDim = 800
        val srcW = rawBitmap.width
        val srcH = rawBitmap.height
        if (srcW > maxDim || srcH > maxDim) {
            var sampleSize = 1
            while (srcW / sampleSize > maxDim || srcH / sampleSize > maxDim) {
                sampleSize *= 2
            }
            val targetW = srcW / sampleSize
            val targetH = srcH / sampleSize
            try {
                Bitmap.createScaledBitmap(rawBitmap, targetW, targetH, true)
            } catch (e: Exception) {
                rawBitmap
            }
        } else {
            rawBitmap
        }
    }

    // Apply brightness, contrast, and saturation adjustments on the preview thumbnail
    val filteredThumbnail = remember(previewThumbnailBitmap, brightness, contrast, saturation, isBlackAndWhite) {
        if (brightness != 0.0f || contrast != 1.0f || saturation != 1.0f || isBlackAndWhite) {
            try {
                BitmapUtils.applyStudioFilters(previewThumbnailBitmap, brightness, contrast, saturation, isBlackAndWhite)
            } catch (e: Exception) {
                previewThumbnailBitmap
            }
        } else {
            previewThumbnailBitmap
        }
    }

    // ML Kit Subject Segmentation processing
    LaunchedEffect(useMLKitSegmentation, filteredThumbnail) {
        if (useMLKitSegmentation) {
            isSegmenting = true
            try {
                val options = SubjectSegmenterOptions.Builder()
                    .enableForegroundBitmap()
                    .build()
                val segmenter = SubjectSegmentation.getClient(options)
                val image = InputImage.fromBitmap(filteredThumbnail, 0)
                val result = segmenter.process(image).await()
                mlKitForegroundBitmap = result.foregroundBitmap
            } catch (e: Exception) {
                mlKitForegroundBitmap = null
            } finally {
                isSegmenting = false
            }
        } else {
            mlKitForegroundBitmap = null
        }
    }

    // Reactively compute the current displayed bitmap with background replacement on the FILTERED THUMBNAIL for buttery-smooth slider & frame updates
    val displayBitmap = remember(filteredThumbnail, colorToReplace, targetReplacementBgColor, replaceTolerance, replaceSeedFraction, useMLKitSegmentation, mlKitForegroundBitmap) {
        if (useMLKitSegmentation && mlKitForegroundBitmap != null) {
            val bgReplaced = Bitmap.createBitmap(filteredThumbnail.width, filteredThumbnail.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bgReplaced)
            canvas.drawColor(targetReplacementBgColor)
            
            // Draw the foreground bitmap scaled to match filteredThumbnail size if needed
            val fg = mlKitForegroundBitmap!!
            val matrix = Matrix()
            val scaleX = filteredThumbnail.width.toFloat() / fg.width
            val scaleY = filteredThumbnail.height.toFloat() / fg.height
            matrix.postScale(scaleX, scaleY)
            val paint = Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
            }
            canvas.drawBitmap(fg, matrix, paint)
            
            bgReplaced
        } else if (colorToReplace != null) {
            val fraction = replaceSeedFraction ?: Pair(0.02f, 0.02f)
            val seedX = (filteredThumbnail.width * fraction.first).toInt().coerceIn(0, filteredThumbnail.width - 1)
            val seedY = (filteredThumbnail.height * fraction.second).toInt().coerceIn(0, filteredThumbnail.height - 1)
            BitmapUtils.replaceBackgroundColor(
                src = filteredThumbnail,
                colorToReplace = colorToReplace!!,
                replacementColor = targetReplacementBgColor,
                tolerance = replaceTolerance,
                seedX = seedX,
                seedY = seedY
            )
        } else {
            filteredThumbnail
        }
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "Frame & Perfect Photo",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Center),
                    textAlign = TextAlign.Center
                )
                IconButton(
                    onClick = rotateBitmap,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        imageVector = Icons.Default.RotateRight,
                        contentDescription = "Rotate 90° clockwise"
                    )
                }
            }
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Modern Tab selector for dual/triple edit mode controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = { activeTab = 0 },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (activeTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (activeTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Frame / Zoom", fontSize = 11.sp, maxLines = 1)
                        }

                        Button(
                            onClick = { activeTab = 1 },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (activeTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (activeTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.1f)
                        ) {
                            Icon(imageVector = Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Change BG", fontSize = 11.sp, maxLines = 1)
                        }

                        Button(
                            onClick = { activeTab = 2 },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (activeTab == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (activeTab == 2) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Icon(imageVector = Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Studio Light", fontSize = 11.sp, maxLines = 1)
                        }
                    }

                    // Conditional controls layout matching tab state
                    when (activeTab) {
                        0 -> {
                            // ZOOM & BIOMETRIC GUIDELINES CONTROLS
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Zoom",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.width(48.dp)
                                    )
                                    Slider(
                                        value = scale,
                                        onValueChange = { scale = it },
                                        valueRange = 1.0f..5.0f,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Face,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Column {
                                            Text(
                                                text = "Biometric Head Silhouette",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Draws guide for facial chin & crown alignment",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = isSilhouetteVisible,
                                        onCheckedChange = { isSilhouetteVisible = it }
                                    )
                                }
                            }
                        }
                        1 -> {
                            // COLOR KEYING & BACKGROUND REPLACEMENT CONTROLS
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // 1. Color Selector Component
                                PassportBackgroundColorSelector(
                                    selectedColor = targetReplacementBgColor,
                                    onColorSelected = { selected ->
                                        targetReplacementBgColor = selected
                                        // If background has not been sampled/removed yet, Auto-Detect background instantly to make it visual
                                        if (colorToReplace == null && !useMLKitSegmentation) {
                                            useMLKitSegmentation = true
                                        }
                                    }
                                )

                                // 2. Background Detection / Removal Controller block
                                Surface(
                                    tonalElevation = 1.dp,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Background Key Status",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            if (colorToReplace != null || useMLKitSegmentation) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .clip(CircleShape)
                                                            .background(MaterialTheme.colorScheme.primary)
                                                    )
                                                    Text(
                                                        text = if (useMLKitSegmentation) "Smart Active" else "Active Remover",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            } else {
                                                Text(
                                                    text = "Inactive",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Smart Remove (ML Kit) Button
                                            Button(
                                                onClick = {
                                                    useMLKitSegmentation = true
                                                    colorToReplace = null
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                ),
                                                modifier = Modifier.weight(1.2f)
                                            ) {
                                                if (isSegmenting) {
                                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.AutoFixHigh,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Smart Remove", fontSize = 11.sp)
                                            }

                                            // Manual Custom Eyedropper Button
                                            OutlinedButton(
                                                onClick = { 
                                                    isSamplingMode = true
                                                    useMLKitSegmentation = false 
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = if (isSamplingMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                ),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.FilterCenterFocus,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(if (colorToReplace != null) "Re-Sample" else "Eyedropper", fontSize = 11.sp)
                                            }

                                            // Reset/Undo Keying button if active
                                            if (colorToReplace != null || useMLKitSegmentation) {
                                                IconButton(
                                                    onClick = {
                                                        colorToReplace = null
                                                        replaceSeedFraction = null
                                                        useMLKitSegmentation = false
                                                    },
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .background(
                                                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                                                            CircleShape
                                                        )
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Refresh,
                                                        contentDescription = "Restore original background",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // Tolerance slider if keying is active
                                        if (colorToReplace != null) {
                                            Column(modifier = Modifier.padding(top = 4.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Key Sensitivity",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        text = "${(replaceTolerance * 100).toInt()}%",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                Slider(
                                                    value = replaceTolerance,
                                                    onValueChange = { replaceTolerance = it },
                                                    valueRange = 0.05f..0.50f,
                                                    modifier = Modifier.height(24.dp)
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = "Tip: Choose any preset background color from the panel above to instantly test automatic background removal on your photo.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                lineHeight = 14.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        2 -> {
                            // STUDIO ENHANCEMENTS & LIGHT ADJUSTMENTS
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Studio Lighting & Color Adjust",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    if (brightness != 0f || contrast != 1f || saturation != 1f || isBlackAndWhite) {
                                        TextButton(
                                            onClick = {
                                                brightness = 0.0f
                                                contrast = 1.0f
                                                saturation = 1.0f
                                                isBlackAndWhite = false
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = null,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Reset Adjust", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }

                                // 1. Brightness / Exposure Slider
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Brightness / Exposure", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            text = if (brightness >= 0) "+${(brightness * 100).toInt()}" else "${(brightness * 100).toInt()}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Slider(
                                        value = brightness,
                                        onValueChange = { brightness = it },
                                        valueRange = -0.3f..0.3f,
                                        modifier = Modifier.height(24.dp)
                                    )
                                }

                                // 2. Contrast Slider
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Contrast / Contrast Ratio", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            text = "${(contrast * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Slider(
                                        value = contrast,
                                        onValueChange = { contrast = it },
                                        valueRange = 0.6f..1.6f,
                                        modifier = Modifier.height(24.dp)
                                    )
                                }

                                // 3. Saturation / Color Richness Slider
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Vividness / Saturation", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            text = "${(saturation * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Slider(
                                        value = saturation,
                                        onValueChange = { saturation = it },
                                        valueRange = 0.0f..2.0f,
                                        modifier = Modifier.height(24.dp)
                                    )
                                }
                                
                                // 4. Black & White Toggle
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Black & White Filter", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Switch(
                                        checked = isBlackAndWhite,
                                        onCheckedChange = { isBlackAndWhite = it }
                                    )
                                }
                            }
                        }
                    }

                    // CANCEL & CONFIRM CROPPING ACTIONS BUTTONS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                if (viewWidth > 0 && viewHeight > 0) {
                                    coroutineScope.launch {
                                        // Perform high-res cropping mathematically mapping display matrix to bitmap pixels
                                        val cropFrameH = if (viewWidth / viewHeight > aspectRatio) {
                                            viewHeight * 0.75f
                                        } else {
                                            (viewWidth * 0.75f) / aspectRatio
                                        }
                                        val cropFrameW = cropFrameH * aspectRatio
                                        val cropLeft = (viewWidth - cropFrameW) / 2f
                                        val cropTop = (viewHeight - cropFrameH) / 2f

                                        // Create output cropped bitmap
                                        val targetH = 1200f
                                        val targetW = targetH * aspectRatio
                                        val cropped = Bitmap.createBitmap(targetW.toInt(), targetH.toInt(), Bitmap.Config.ARGB_8888)
                                        val canvas = Canvas(cropped)
                                        canvas.drawColor(if (useMLKitSegmentation || colorToReplace != null) targetReplacementBgColor else Color.WHITE)

                                        // Apply studio enhancements on raw high-res bitmap
                                        val filteredRaw = if (brightness != 0.0f || contrast != 1.0f || saturation != 1.0f || isBlackAndWhite) {
                                            try {
                                                BitmapUtils.applyStudioFilters(rawBitmap, brightness, contrast, saturation, isBlackAndWhite)
                                            } catch (e: Exception) {
                                                rawBitmap
                                            }
                                        } else {
                                            rawBitmap
                                        }

                                        // We want to apply background replacement on the FULL resolution filteredRaw on Confirm
                                        val fullResSource = if (useMLKitSegmentation) {
                                            isSegmenting = true
                                            try {
                                                val options = SubjectSegmenterOptions.Builder()
                                                    .enableForegroundBitmap()
                                                    .build()
                                                val segmenter = SubjectSegmentation.getClient(options)
                                                val image = InputImage.fromBitmap(filteredRaw, 0)
                                                val result = segmenter.process(image).await()
                                                result.foregroundBitmap ?: filteredRaw
                                            } catch (e: Exception) {
                                                filteredRaw
                                            } finally {
                                                isSegmenting = false
                                            }
                                        } else if (colorToReplace != null) {
                                            val fraction = replaceSeedFraction ?: Pair(0.02f, 0.02f)
                                            val seedX = (filteredRaw.width * fraction.first).toInt().coerceIn(0, filteredRaw.width - 1)
                                            val seedY = (filteredRaw.height * fraction.second).toInt().coerceIn(0, filteredRaw.height - 1)
                                            BitmapUtils.replaceBackgroundColor(
                                                src = filteredRaw,
                                                colorToReplace = colorToReplace!!,
                                                replacementColor = targetReplacementBgColor,
                                                tolerance = replaceTolerance,
                                                seedX = seedX,
                                                seedY = seedY
                                            )
                                        } else {
                                            filteredRaw
                                        }

                                        // Map display coordinates (view dimensions) back
                                        val displayToCropScale = targetW / cropFrameW

                                        val matrix = Matrix()
                                        matrix.postTranslate(
                                            -fullResSource.width / 2f,
                                            -fullResSource.height / 2f
                                        )

                                        // Scale image to fill/fit view space (initial fitting)
                                        val imgRatio = fullResSource.width.toFloat() / fullResSource.height.toFloat()
                                        val initialScale = if (imgRatio > aspectRatio) {
                                            cropFrameH / fullResSource.height.toFloat()
                                        } else {
                                            cropFrameW / fullResSource.width.toFloat()
                                        }
                                        matrix.postScale(initialScale * scale, initialScale * scale)

                                        // Add the visual pan offsets
                                        val initialCenterX = viewWidth / 2f
                                        val initialCenterY = viewHeight / 2f
                                        matrix.postTranslate(
                                            initialCenterX + offset.x,
                                            initialCenterY + offset.y
                                        )

                                        // Shift so that the crop box top-left is at (0, 0)
                                        matrix.postTranslate(-cropLeft, -cropTop)

                                        // Scale to high resolution target
                                        matrix.postScale(displayToCropScale, displayToCropScale)

                                        val paint = Paint().apply {
                                            isAntiAlias = true
                                            isFilterBitmap = true
                                        }
                                        canvas.drawBitmap(fullResSource, matrix, paint)

                                        // Recycle fullResSource to save heap space if we generated a background replaced copy
                                        if (fullResSource != rawBitmap && fullResSource != filteredRaw) {
                                            fullResSource.recycle()
                                        }

                                        onCropCompleted(cropped)
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        ) {
                            Text("Save Photo")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.95f))
                .pointerInput(Unit) {
                    detectTransformGestures(panZoomLock = true) { centroid, pan, zoom, _ ->
                        if (!isSamplingMode) {
                            val oldScale = scale
                            val newScale = (scale * zoom).coerceIn(1.0f, 5.0f)
                            val actualZoom = if (oldScale > 0f) newScale / oldScale else 1f
                            scale = newScale

                            val screenCenter = Offset(viewWidth / 2f, viewHeight / 2f)
                            val relativeCentroid = centroid - screenCenter

                            offset = (offset - relativeCentroid) * actualZoom + relativeCentroid + pan
                        }
                    }
                }
                .onGloballyPositioned { coordinates ->
                    viewWidth = coordinates.size.width.toFloat()
                    viewHeight = coordinates.size.height.toFloat()
                },
            contentAlignment = Alignment.Center
        ) {
            if (viewWidth > 0f && viewHeight > 0f) {
                // Calculate display crop frame details
                val cropFrameH = if (viewWidth / viewHeight > aspectRatio) {
                    viewHeight * 0.75f
                } else {
                    (viewWidth * 0.75f) / aspectRatio
                }
                val cropFrameW = cropFrameH * aspectRatio

                val cropLeft = (viewWidth - cropFrameW) / 2f
                val cropTop = (viewHeight - cropFrameH) / 2f

                // Draw transformed image and frame highlights helper
                ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                    val composeCanvas = drawContext.canvas.nativeCanvas

                    // 1. Draw raw image (using background replaced displayBitmap)
                    val matrix = Matrix()
                    matrix.postTranslate(-displayBitmap.width / 2f, -displayBitmap.height / 2f)

                    val imgRatio = displayBitmap.width.toFloat() / displayBitmap.height.toFloat()
                    val initialScale = if (imgRatio > aspectRatio) {
                        cropFrameH / displayBitmap.height.toFloat()
                    } else {
                        cropFrameW / displayBitmap.width.toFloat()
                    }

                    matrix.postScale(initialScale * scale, initialScale * scale)
                    matrix.postTranslate(viewWidth / 2f + offset.x, viewHeight / 2f + offset.y)

                    val paint = Paint().apply {
                        isAntiAlias = true
                        isFilterBitmap = true
                    }
                    composeCanvas.save()
                    composeCanvas.drawBitmap(displayBitmap, matrix, paint)
                    composeCanvas.restore()

                    // 2. Draw surrounding dark overlays outside of crop box
                    val overlayPaint = Paint().apply {
                        color = Color.argb(180, 0, 0, 0)
                        style = Paint.Style.FILL
                    }
                    composeCanvas.drawRect(0f, 0f, viewWidth, cropTop, overlayPaint)
                    composeCanvas.drawRect(0f, cropTop + cropFrameH, viewWidth, viewHeight, overlayPaint)
                    composeCanvas.drawRect(0f, cropTop, cropLeft, cropTop + cropFrameH, overlayPaint)
                    composeCanvas.drawRect(cropLeft + cropFrameW, cropTop, viewWidth, cropTop + cropFrameH, overlayPaint)

                    // 3. Draw crop box border highlights
                    val borderPaint = Paint().apply {
                        color = Color.WHITE
                        style = Paint.Style.STROKE
                        strokeWidth = 3f
                    }
                    composeCanvas.drawRect(cropLeft, cropTop, cropLeft + cropFrameW, cropTop + cropFrameH, borderPaint)

                    // 4. Draw helper grid lines for facial alignment (Rule of Thirds + Passport head alignment helper)
                    val guidesPaint = Paint().apply {
                        color = Color.argb(120, 255, 255, 255)
                        style = Paint.Style.STROKE
                        strokeWidth = 1f
                    }
                    composeCanvas.drawLine(cropLeft + cropFrameW / 3f, cropTop, cropLeft + cropFrameW / 3f, cropTop + cropFrameH, guidesPaint)
                    composeCanvas.drawLine(cropLeft + 2 * cropFrameW / 3f, cropTop, cropLeft + 2 * cropFrameW / 3f, cropTop + cropFrameH, guidesPaint)
                    composeCanvas.drawLine(cropLeft, cropTop + cropFrameH / 3f, cropLeft + cropFrameW, cropTop + cropFrameH / 3f, guidesPaint)
                    composeCanvas.drawLine(cropLeft, cropTop + 2 * cropFrameH / 3f, cropLeft + cropFrameW, cropTop + 2 * cropFrameH / 3f, guidesPaint)

                    // 5. Draw head silhouette for passport framing aid if requested
                    if (isSilhouetteVisible) {
                        val silhouettePaint = Paint().apply {
                            color = Color.argb(80, 33, 150, 243)
                            style = Paint.Style.STROKE
                            strokeWidth = 3f
                        }
                        val headRect = RectF(
                            cropLeft + cropFrameW * 0.25f,
                            cropTop + cropFrameH * 0.15f,
                            cropLeft + cropFrameW * 0.75f,
                            cropTop + cropFrameH * 0.75f
                        )
                        composeCanvas.drawOval(headRect, silhouettePaint)
                    }
                }

                // Transparent full-size Tap sampling overlays when eyedropper is active
                if (isSamplingMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f))
                            .pointerInput(rawBitmap, viewWidth, viewHeight, scale, offset) {
                                detectTapGestures { tapLoc ->
                                    val matrix = Matrix()
                                    matrix.postTranslate(-rawBitmap.width / 2f, -rawBitmap.height / 2f)

                                    val imgRatio = rawBitmap.width.toFloat() / rawBitmap.height.toFloat()
                                    val initialScale = if (imgRatio > aspectRatio) {
                                        cropFrameH / rawBitmap.height.toFloat()
                                    } else {
                                        cropFrameW / rawBitmap.width.toFloat()
                                    }

                                    matrix.postScale(initialScale * scale, initialScale * scale)
                                    matrix.postTranslate(viewWidth / 2f + offset.x, viewHeight / 2f + offset.y)

                                    val inverseMatrix = Matrix()
                                    if (matrix.invert(inverseMatrix)) {
                                        val pts = floatArrayOf(tapLoc.x, tapLoc.y)
                                        inverseMatrix.mapPoints(pts)
                                        val bx = pts[0].toInt()
                                        val by = pts[1].toInt()
                                        if (bx in 0 until rawBitmap.width && by in 0 until rawBitmap.height) {
                                            colorToReplace = rawBitmap.getPixel(bx, by)
                                            replaceSeedFraction = Pair(bx.toFloat() / rawBitmap.width, by.toFloat() / rawBitmap.height)
                                        }
                                    }
                                    isSamplingMode = false
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            tonalElevation = 8.dp,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Magic Background Sampler",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                Text(
                                    text = "Tap on the background color directly in your photo to auto-replace it.",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            } else {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun PassportBackgroundColorSelector(
    selectedColor: Int,
    onColorSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var rawHue by remember(selectedColor) {
        val hsv = FloatArray(3)
        Color.colorToHSV(selectedColor, hsv)
        mutableStateOf(hsv[0])
    }

    val presets = remember {
        listOf(
            Triple("White", Color.WHITE, "Standard White backdrop"),
            Triple("Light Blue", Color.rgb(164, 211, 238), "Light Blue passport standard"),
            Triple("Royal Blue", Color.rgb(0, 51, 153), "Official Royal Blue standard"),
            Triple("Light Gray", Color.rgb(220, 224, 230), "Soft Light Gray standard"),
            Triple("Red", Color.rgb(204, 0, 0), "Official China Visa Backdrop"),
            Triple("Emerald Green", Color.rgb(27, 94, 32), "Visa Backdrop option"),
            Triple("Dark Gray", Color.rgb(66, 66, 66), "Corporate Portrait backdrop")
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                shape = RoundedCornerShape(16.dp)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Background Color Selection",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Small badge showing current color in hex format or name
            val activeName = presets.firstOrNull { it.second == selectedColor }?.first ?: "Custom"
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = activeName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Text(
            text = "Select an official solid passport backdrop color option:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Presets Grid-Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            presets.forEach { (name, colorVal, description) ->
                val isSelected = selectedColor == colorVal
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Color(colorVal))
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                        .clickable {
                            onColorSelected(colorVal)
                        }
                        .testTag("color_preset_$name"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = if (colorVal == Color.WHITE || colorVal == Color.rgb(220, 224, 230)) {
                                androidx.compose.ui.graphics.Color.Black
                            } else {
                                androidx.compose.ui.graphics.Color.White
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Custom hue slider title
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Alternative Custom Hue Tint",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val hexString = String.format("#%06X", (0xFFFFFF and selectedColor))
            Text(
                text = hexString,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }

        // Beautiful custom hue slider track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                        colors = listOf(
                            androidx.compose.ui.graphics.Color(0xFFFF0000), // Red
                            androidx.compose.ui.graphics.Color(0xFFFFFF00), // Yellow
                            androidx.compose.ui.graphics.Color(0xFF00FF00), // Green
                            androidx.compose.ui.graphics.Color(0xFF00FFFF), // Cyan
                            androidx.compose.ui.graphics.Color(0xFF0000FF), // Blue
                            androidx.compose.ui.graphics.Color(0xFFFF00FF), // Magenta
                            androidx.compose.ui.graphics.Color(0xFFFF0000)  // Red
                        )
                    )
                )
        )

        Slider(
            value = rawHue,
            onValueChange = { h ->
                rawHue = h
                val hsv = floatArrayOf(h, 0.85f, 0.95f)
                val computedColor = Color.HSVToColor(hsv)
                onColorSelected(computedColor)
            },
            valueRange = 0f..360f,
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .testTag("custom_hue_slider"),
            colors = SliderDefaults.colors(
                thumbColor = androidx.compose.ui.graphics.Color(selectedColor),
                activeTrackColor = androidx.compose.ui.graphics.Color.Transparent,
                inactiveTrackColor = androidx.compose.ui.graphics.Color.Transparent
            )
        )
    }
}

