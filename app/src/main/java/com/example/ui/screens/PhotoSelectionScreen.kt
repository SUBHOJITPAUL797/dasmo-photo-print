package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.R
import com.example.domain.model.ProjectMode
import com.example.ui.ProjectViewModel
import com.example.ui.components.ClayWizardStepBanner
import com.example.ui.crop.PassportPhotoCropper
import com.example.util.BitmapUtils
import java.io.File

fun createTempPictureUri(context: android.content.Context): Uri {
    val tempFile = File.createTempFile("temp_camera_image", ".jpg", context.cacheDir).apply {
        createNewFile()
        deleteOnExit()
    }
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
}

private sealed class PhotoCropSessionState {
    object Picking : PhotoCropSessionState()
    data class Cropping(
        val uri: Uri,
        val bitmap: Bitmap,
        val isLowResolution: Boolean
    ) : PhotoCropSessionState()
}

private enum class StudioToolMenu {
    NONE,
    SUIT,
    BACKDROP
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoSelectionScreen(
    viewModel: ProjectViewModel,
    isPhotoA: Boolean, // true for Photo A (or Single mode), false for Photo B (Joint mode)
    onBackClicked: () -> Unit
) {
    val context = LocalContext.current
    var cropSessionState by remember { mutableStateOf<PhotoCropSessionState>(PhotoCropSessionState.Picking) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val beginCropSession: (Uri) -> Unit = { uri ->
        val bitmap = BitmapUtils.loadScaledBitmap(context, uri)
        if (bitmap != null) {
            viewModel.handlePhotoSelected(uri, isPhotoA, context, bitmap)

            val w = viewModel.widthCm.toFloatOrNull() ?: 3.5f
            val h = viewModel.heightCm.toFloatOrNull() ?: 4.5f
            val finalUnitW = if (viewModel.mode == ProjectMode.JOINT) {
                w * viewModel.jointSplitRatio
            } else {
                w
            }
            val finalUnitH = h

            val requiredPxW = (finalUnitW * viewModel.dpi) / 2.54f
            val requiredPxH = (finalUnitH * viewModel.dpi) / 2.54f
            val isLowRes = (bitmap.width < requiredPxW) || (bitmap.height < requiredPxH)

            cropSessionState = PhotoCropSessionState.Cropping(
                uri = uri,
                bitmap = bitmap,
                isLowResolution = isLowRes
            )
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = tempCameraUri
        if (success && uri != null) {
            beginCropSession(uri)
        }
    }

    // System Picker launcher
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            beginCropSession(uri)
        }
    }

    // Determine target crop ratios
    val targetAspectRatio = remember(viewModel.widthCm, viewModel.heightCm, viewModel.jointSplitRatio) {
        val w = viewModel.widthCm.toFloatOrNull() ?: 3.5f
        val h = viewModel.heightCm.toFloatOrNull() ?: 4.5f
        if (viewModel.mode == ProjectMode.JOINT) {
            val halfW = if (isPhotoA) w * viewModel.jointSplitRatio else w * (1f - viewModel.jointSplitRatio)
            halfW / h
        } else {
            w / h
        }
    }

    when (val session = cropSessionState) {
        is PhotoCropSessionState.Cropping -> {
            // Render Full Screen Cropper directly
            Box(modifier = Modifier.fillMaxSize()) {
                PassportPhotoCropper(
                    sourceBitmap = session.bitmap,
                    aspectRatio = targetAspectRatio,
                    showHeadSilhouette = viewModel.mode != ProjectMode.ID_CARD,
                    onCropCompleted = { cropped ->
                        cropSessionState = PhotoCropSessionState.Picking
                        viewModel.handlePhotoCropped(cropped, isPhotoA)
                    },
                    onCancel = {
                        cropSessionState = PhotoCropSessionState.Picking
                    }
                )

                // Quality Warning Overlay Banner Info
                if (session.isLowResolution) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 54.dp)
                            .testTag("quality_warning_banner"),
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Resolution Warning",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "This photo's resolution is lower than recommended for 300 DPI printing at this size.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        is PhotoCropSessionState.Picking -> {
            val existingCroppedBitmap = if (isPhotoA) viewModel.cropABitmap else viewModel.cropBBitmap

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = when {
                                    viewModel.mode == ProjectMode.JOINT || viewModel.mode == ProjectMode.MULTI_PERSON -> if (isPhotoA) "Select Person 1 Photo" else "Select Person 2 Photo"
                                    viewModel.mode == ProjectMode.ID_CARD -> if (isPhotoA) "Select Front Side" else "Select Back Side"
                                    else -> "Select Portrait Photo"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBackClicked, modifier = Modifier.testTag("picker_back_btn")) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                            }
                        }
                    )
                }
            ) { innerPadding ->
                val currentStepVal = if (viewModel.mode == ProjectMode.JOINT || viewModel.mode == ProjectMode.ID_CARD || viewModel.mode == ProjectMode.MULTI_PERSON) {
                    if (isPhotoA) "Step 3 of 5" else "Step 4 of 5"
                } else {
                    "Step 3 of 4"
                }
                val progressVal = if (viewModel.mode == ProjectMode.JOINT || viewModel.mode == ProjectMode.ID_CARD || viewModel.mode == ProjectMode.MULTI_PERSON) {
                    if (isPhotoA) 0.6f else 0.8f
                } else {
                    0.75f
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ClayWizardStepBanner(
                        stepLabel = currentStepVal.uppercase(),
                        title = when {
                            viewModel.mode == ProjectMode.JOINT || viewModel.mode == ProjectMode.MULTI_PERSON -> if (isPhotoA) "Person 1 Portrait Studio" else "Person 2 Portrait Studio"
                            viewModel.mode == ProjectMode.ID_CARD -> if (isPhotoA) "Front Side of ID Card" else "Back Side of ID Card"
                            else -> "Portrait Studio & Cropper"
                        },
                        subtitle = "Capture or pick photo, remove backdrop & apply formal studio attire",
                        progress = progressVal,
                        accentColor = MaterialTheme.colorScheme.primary,
                        artworkRes = if (viewModel.mode == ProjectMode.ID_CARD) R.drawable.img_clay_studio_hero else R.drawable.img_clay_passport
                    )

                    if (existingCroppedBitmap != null) {
                        var activeStudioMenu by remember { mutableStateOf(StudioToolMenu.NONE) }

                        // Render existing cropped preview card with labelled Studio Retouching & Suit Overlay tools
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("picker_card_existing_status"),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .size(168.dp, 210.dp)
                                        .testTag("cropped_preview_frame"),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                                    shadowElevation = 6.dp
                                ) {
                                    Image(
                                        bitmap = existingCroppedBitmap.asImageBitmap(),
                                        contentDescription = "Existing portrait crop preview",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "Portrait Crop Ready",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "Apply studio formal attire, backdrop tint, or auto-lighting below, or proceed directly to the print sheet preview.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // STUDIO RETOUCHING & BACKDROP TOOLS (Mutually Exclusive Menu State Machine)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Formal Suit Overlay
                                    Box(modifier = Modifier.weight(1f)) {
                                        FilledTonalButton(
                                            onClick = { activeStudioMenu = StudioToolMenu.SUIT },
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Checkroom, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Suit", style = MaterialTheme.typography.labelMedium)
                                        }
                                        DropdownMenu(
                                            expanded = activeStudioMenu == StudioToolMenu.SUIT,
                                            onDismissRequest = { activeStudioMenu = StudioToolMenu.NONE }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Navy Suit & Tie") },
                                                onClick = { viewModel.applyFormalAttire(isPhotoA, "navy_suit"); activeStudioMenu = StudioToolMenu.NONE }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Black Suit & Tie") },
                                                onClick = { viewModel.applyFormalAttire(isPhotoA, "black_suit"); activeStudioMenu = StudioToolMenu.NONE }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("White Formal Shirt") },
                                                onClick = { viewModel.applyFormalAttire(isPhotoA, "white_shirt"); activeStudioMenu = StudioToolMenu.NONE }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Women's Blazer") },
                                                onClick = { viewModel.applyFormalAttire(isPhotoA, "women_blazer"); activeStudioMenu = StudioToolMenu.NONE }
                                            )
                                        }
                                    }

                                    // Studio Backdrop Color
                                    Box(modifier = Modifier.weight(1f)) {
                                        FilledTonalButton(
                                            onClick = { activeStudioMenu = StudioToolMenu.BACKDROP },
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.FormatColorFill, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Backdrop", style = MaterialTheme.typography.labelMedium)
                                        }
                                        DropdownMenu(
                                            expanded = activeStudioMenu == StudioToolMenu.BACKDROP,
                                            onDismissRequest = { activeStudioMenu = StudioToolMenu.NONE }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Light Blue") },
                                                onClick = { viewModel.applyBackdropColor(isPhotoA, android.graphics.Color.parseColor("#E1F5FE")); activeStudioMenu = StudioToolMenu.NONE }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Pure White") },
                                                onClick = { viewModel.applyBackdropColor(isPhotoA, android.graphics.Color.WHITE); activeStudioMenu = StudioToolMenu.NONE }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Light Grey") },
                                                onClick = { viewModel.applyBackdropColor(isPhotoA, android.graphics.Color.parseColor("#F5F5F5")); activeStudioMenu = StudioToolMenu.NONE }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Soft Red") },
                                                onClick = { viewModel.applyBackdropColor(isPhotoA, android.graphics.Color.parseColor("#FFCDD2")); activeStudioMenu = StudioToolMenu.NONE }
                                            )
                                        }
                                    }

                                    // Auto Retouch
                                    FilledTonalButton(
                                        onClick = { viewModel.applyAutoRetouch(isPhotoA) },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Retouch", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.proceedAfterPhotoConfirmed(isPhotoA)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("proceed_existing_btn"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Keep & Proceed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        pickerLauncher.launch(
                                            PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    },
                                    modifier = Modifier.weight(1f).testTag("replace_photo_btn"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Gallery")
                                }

                                OutlinedButton(
                                    onClick = {
                                        val uri = createTempPictureUri(context)
                                        tempCameraUri = uri
                                        cameraLauncher.launch(uri)
                                    },
                                    modifier = Modifier.weight(1f).testTag("take_new_photo_btn"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Camera")
                                }

                                val originalUri = if (isPhotoA) viewModel.photoAUri else viewModel.photoBUri
                                OutlinedButton(
                                    onClick = {
                                        if (originalUri != null) {
                                            beginCropSession(originalUri)
                                        }
                                    },
                                    enabled = originalUri != null,
                                    modifier = Modifier.weight(1f).testTag("recrop_existing_btn"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Re-Crop")
                                }
                            }
                        }
                    } else {
                    // Interactive Picker Card for pristine step entry
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 280.dp)
                            .clickable {
                                pickerLauncher.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            }
                            .testTag("picker_card_launch"),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(84.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Text(
                                text = when {
                                    viewModel.mode == ProjectMode.JOINT || viewModel.mode == ProjectMode.MULTI_PERSON -> if (isPhotoA) "Select Photo of Person 1" else "Select Photo of Person 2"
                                    viewModel.mode == ProjectMode.ID_CARD -> if (isPhotoA) "Select Front Side of ID" else "Select Back Side of ID"
                                    else -> "Select Customer Photo"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Tap here to browse gallery or use the camera below. Built-in AI background removal & studio lighting included.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val uri = createTempPictureUri(context)
                                tempCameraUri = uri
                                cameraLauncher.launch(uri)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("camera_btn"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Camera", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                pickerLauncher.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("gallery_btn"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Gallery", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
}


