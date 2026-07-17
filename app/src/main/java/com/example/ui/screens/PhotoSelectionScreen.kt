package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.PhotoCamera
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
import androidx.compose.ui.unit.sp
import com.example.domain.model.ProjectMode
import com.example.ui.ProjectViewModel
import com.example.ui.crop.PassportPhotoCropper
import com.example.util.BitmapUtils
import java.io.File
import androidx.core.content.FileProvider

fun createTempPictureUri(context: android.content.Context): Uri {
    val tempFile = File.createTempFile("temp_camera_image", ".jpg", context.cacheDir).apply {
        createNewFile()
        deleteOnExit()
    }
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoSelectionScreen(
    viewModel: ProjectViewModel,
    isPhotoA: Boolean, // true for Photo A (or Single mode), false for Photo B (Joint mode)
    onBackClicked: () -> Unit
) {
    val context = LocalContext.current
    var selectedLocalUri by remember { mutableStateOf<Uri?>(null) }
    var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showResolutionWarning by remember { mutableStateOf(false) }

    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            val uri = tempCameraUri!!
            selectedLocalUri = uri

            val bitmap = BitmapUtils.loadScaledBitmap(context, uri)
            if (bitmap != null) {
                loadedBitmap = bitmap
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

                showResolutionWarning = (bitmap.width < requiredPxW) || (bitmap.height < requiredPxH)
            }
        }
    }

    // System Picker launcher
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedLocalUri = uri

            // Safely loading high resolution scale
            val bitmap = BitmapUtils.loadScaledBitmap(context, uri)
            if (bitmap != null) {
                loadedBitmap = bitmap
                viewModel.handlePhotoSelected(uri, isPhotoA, context, bitmap)

                // Quality resolution check (PRD Section 7.1.4)
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

                showResolutionWarning = (bitmap.width < requiredPxW) || (bitmap.height < requiredPxH)
            }
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

    if (loadedBitmap != null) {
        // Render Full Screen Cropper directly
        Box(modifier = Modifier.fillMaxSize()) {
            PassportPhotoCropper(
                sourceBitmap = loadedBitmap!!,
                aspectRatio = targetAspectRatio,
                showHeadSilhouette = viewModel.mode != ProjectMode.ID_CARD,
                onCropCompleted = { cropped ->
                    viewModel.handlePhotoCropped(cropped, isPhotoA)
                    // Reset local screen variables
                    loadedBitmap = null
                    selectedLocalUri = null
                    showResolutionWarning = false
                },
                onCancel = {
                    loadedBitmap = null
                    selectedLocalUri = null
                    showResolutionWarning = false
                }
            )

            // Quality Warning Overlay Banner Info (PRD Section 7.1.4)
            if (showResolutionWarning) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 50.dp)
                        .testTag("quality_warning_banner"),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Resolution Warning",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "This photo's resolution is lower than recommended for sharp printing at this size. It may look slightly blurry.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    } else {
        val existingCroppedBitmap = if (isPhotoA) viewModel.cropABitmap else viewModel.cropBBitmap

        // Standard picker selection greeting screen
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when {
                                viewModel.mode == ProjectMode.JOINT || viewModel.mode == ProjectMode.MULTI_PERSON -> if (isPhotoA) "Select Person 1 Photo" else "Select Person 2 Photo"
                                viewModel.mode == ProjectMode.ID_CARD -> if (isPhotoA) "Select Front Side" else "Select Back Side"
                                else -> "Select Portrait Photo"
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClicked, modifier = Modifier.testTag("picker_back_btn")) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Go back")
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
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentStepVal,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    LinearProgressIndicator(
                        progress = { progressVal },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (existingCroppedBitmap != null) {
                    // Render existing cropped preview card when navigating back/forward (keeps workflow state alive)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(bottom = 32.dp)
                            .testTag("picker_card_existing_status"),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                modifier = Modifier
                                    .size(160.dp, 200.dp)
                                    .testTag("cropped_preview_frame"),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                                shadowElevation = 4.dp
                            ) {
                                Image(
                                    bitmap = existingCroppedBitmap.asImageBitmap(),
                                    contentDescription = "Existing portrait crop preview",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Text(
                                text = "Current Portrait Crop Ready",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "You have an approved portrait cropped and loaded. You can keep using it, or load a new one from your gallery to replace it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                if (isPhotoA) {
                                    if (viewModel.mode == ProjectMode.SINGLE) {
                                        viewModel.finalUnitBitmap = existingCroppedBitmap
                                        val success = viewModel.computeCurrentLayout()
                                        if (success) {
                                            viewModel.currentStep = 6
                                        }
                                    } else {
                                        viewModel.currentStep = 4
                                    }
                                } else {
                                    if (viewModel.mode == ProjectMode.MULTI_PERSON) {
                                        viewModel.finalUnitBitmap = viewModel.cropABitmap ?: existingCroppedBitmap
                                        val success = viewModel.computeCurrentLayout()
                                        if (success) {
                                            viewModel.currentStep = 6
                                        }
                                    } else {
                                        viewModel.generateJointComposite()
                                        if (viewModel.mode == ProjectMode.ID_CARD) {
                                            val success = viewModel.computeCurrentLayout()
                                            if (success) {
                                                viewModel.currentStep = 6
                                            }
                                        } else {
                                            viewModel.currentStep = 5
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("proceed_existing_btn")
                        ) {
                            Text("Keep & Proceed")
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    pickerLauncher.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                                modifier = Modifier.weight(1f).testTag("replace_photo_btn")
                            ) {
                                Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Gallery")
                            }
                            
                            OutlinedButton(
                                onClick = {
                                    val uri = createTempPictureUri(context)
                                    tempCameraUri = uri
                                    cameraLauncher.launch(uri)
                                },
                                modifier = Modifier.weight(1f).testTag("take_new_photo_btn")
                            ) {
                                Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Camera")
                            }

                            val originalUri = if (isPhotoA) viewModel.photoAUri else viewModel.photoBUri
                            OutlinedButton(
                                onClick = {
                                    if (originalUri != null) {
                                        val bitmap = BitmapUtils.loadScaledBitmap(context, originalUri)
                                        if (bitmap != null) {
                                            loadedBitmap = bitmap
                                        }
                                    }
                                },
                                enabled = originalUri != null,
                                modifier = Modifier.weight(1f).testTag("recrop_existing_btn")
                            ) {
                                Icon(imageVector = Icons.Default.Warning, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Tweak")
                            }
                        }
                    }
                } else {
                    // Interactive Picker Card for pristine step entry
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("picker_card_launch"),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Text(
                                text = when {
                                    viewModel.mode == ProjectMode.JOINT -> if (isPhotoA) "Select Photo of Person 1" else "Select Photo of Person 2"
                                    viewModel.mode == ProjectMode.ID_CARD -> if (isPhotoA) "Select Front Side" else "Select Back Side"
                                    else -> "Select Customer Photo"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Take a high quality photo or select one from your gallery.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val uri = createTempPictureUri(context)
                                tempCameraUri = uri
                                cameraLauncher.launch(uri)
                            },
                            modifier = Modifier.weight(1f).testTag("camera_btn")
                        ) {
                            Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Camera")
                        }

                        Button(
                            onClick = {
                                pickerLauncher.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f).testTag("gallery_btn")
                        ) {
                            Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Gallery")
                        }
                    }
                }
            }
        }
    }
}
