package com.example.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.auth.UserRecoverableAuthException
import com.example.R
import com.example.data.GoogleDriveService
import com.example.data.DriveFolder
import com.example.ui.ProjectViewModel
import com.example.ui.components.ClayWizardStepBanner
import com.example.util.PrintUtils
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream

private sealed class DiskSaveState {
    object Idle : DiskSaveState()
    object Saving : DiskSaveState()
    data class Saved(val pathInfo: String) : DiskSaveState()
    data class Error(val message: String) : DiskSaveState()
}

private sealed class DriveUploadState {
    object Idle : DriveUploadState()
    object Uploading : DriveUploadState()
    data class Success(val message: String) : DriveUploadState()
    data class Error(val message: String) : DriveUploadState()
}

private sealed class DriveBrowserModalState {
    object Closed : DriveBrowserModalState()
    data class Loading(val pathStack: List<Pair<String, String>>) : DriveBrowserModalState()
    data class Browsing(
        val pathStack: List<Pair<String, String>>,
        val folders: List<DriveFolder>,
        val isCreatingNewFolder: Boolean = false,
        val newFolderName: String = ""
    ) : DriveBrowserModalState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    viewModel: ProjectViewModel,
    onBackClicked: () -> Unit,
    onFinishClicked: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("drive_prefs", Context.MODE_PRIVATE) }
    var diskSaveState by remember { mutableStateOf<DiskSaveState>(DiskSaveState.Idle) }
    var driveUploadState by remember { mutableStateOf<DriveUploadState>(DriveUploadState.Idle) }
    var driveBrowserState by remember { mutableStateOf<DriveBrowserModalState>(DriveBrowserModalState.Closed) }
    var showUnderReviewDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    var googleAccount by remember { mutableStateOf(GoogleSignIn.getLastSignedInAccount(context)) }
    var accessToken by remember { mutableStateOf<String?>(null) }

    var selectedFolderId by remember { mutableStateOf(sharedPrefs.getString("pinned_folder_id", "root") ?: "root") }
    var selectedFolderName by remember { mutableStateOf(sharedPrefs.getString("pinned_folder_name", "My Drive") ?: "My Drive") }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                googleAccount = account
                Toast.makeText(context, "Successfully connected Google account!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Google Sign-In failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    // Generates a temp PDF URI inside app sandbox cache so print spooler has instantaneous access
    val localCachePdfFile = remember(viewModel.filename) {
        File(context.cacheDir, "${viewModel.filename}.pdf")
    }

    val localCachePdfUri = remember(localCachePdfFile) {
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            localCachePdfFile
        )
    }

    // Trigger PDF generation & history persistence into sandbox cache (debounced when renaming, calibrating, or updating billing)
    var hasRenderedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(
        viewModel.filename,
        viewModel.topOffsetMm,
        viewModel.leftOffsetMm,
        viewModel.customerName,
        viewModel.customerPhone,
        viewModel.ratePerSheet,
        viewModel.extraServicesFee
    ) {
        if (hasRenderedOnce) {
            kotlinx.coroutines.delay(450)
        }
        val targetCacheUri = localCachePdfUri
        viewModel.saveProjectPdf(context, targetCacheUri)
        hasRenderedOnce = true
    }

    // Google Drive Folder Browser State Machine Dialogs
    when (val browser = driveBrowserState) {
        is DriveBrowserModalState.Closed -> Unit
        is DriveBrowserModalState.Loading,
        is DriveBrowserModalState.Browsing -> {
            val activeStack = when (browser) {
                is DriveBrowserModalState.Loading -> browser.pathStack
                is DriveBrowserModalState.Browsing -> browser.pathStack
                else -> listOf("root" to "My Drive")
            }

            AlertDialog(
                onDismissRequest = { driveBrowserState = DriveBrowserModalState.Closed },
                title = {
                    Column {
                        Text(
                            text = "Select Google Drive Folder",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = activeStack.joinToString(" / ") { it.second },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                text = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    ) {
                        when (browser) {
                            is DriveBrowserModalState.Loading -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.align(Alignment.Center),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            is DriveBrowserModalState.Browsing -> {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = {
                                                if (activeStack.size > 1) {
                                                    val newStack = activeStack.dropLast(1)
                                                    val parentId = newStack.last().first
                                                    driveBrowserState = DriveBrowserModalState.Loading(newStack)
                                                    coroutineScope.launch {
                                                        try {
                                                            val token = accessToken
                                                            val folders = if (token != null) {
                                                                GoogleDriveService.listFolders(token, parentId)
                                                            } else emptyList()
                                                            driveBrowserState = DriveBrowserModalState.Browsing(newStack, folders)
                                                        } catch (e: Throwable) {
                                                            e.printStackTrace()
                                                            driveBrowserState = DriveBrowserModalState.Browsing(newStack, emptyList())
                                                        }
                                                    }
                                                }
                                            },
                                            enabled = activeStack.size > 1
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowUpward,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Go Up")
                                        }

                                        Button(
                                            onClick = {
                                                driveBrowserState = browser.copy(isCreatingNewFolder = true, newFolderName = "")
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CreateNewFolder,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("New Folder")
                                        }
                                    }

                                    if (browser.folders.isEmpty()) {
                                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                            Text(
                                                text = "No subfolders found here.\nClick \"Select This Folder\" to use the current directory, or create a new folder.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.align(Alignment.Center)
                                            )
                                        }
                                    } else {
                                        androidx.compose.foundation.lazy.LazyColumn(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth()
                                        ) {
                                            items(browser.folders.size) { index ->
                                                val folder = browser.folders[index]
                                                Card(
                                                    onClick = {
                                                        val newStack = activeStack + (folder.id to folder.name)
                                                        driveBrowserState = DriveBrowserModalState.Loading(newStack)
                                                        coroutineScope.launch {
                                                            try {
                                                                val token = accessToken
                                                                val folders = if (token != null) {
                                                                    GoogleDriveService.listFolders(token, folder.id)
                                                                } else emptyList()
                                                                driveBrowserState = DriveBrowserModalState.Browsing(newStack, folders)
                                                            } catch (e: Throwable) {
                                                                e.printStackTrace()
                                                                driveBrowserState = DriveBrowserModalState.Browsing(newStack, emptyList())
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp),
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                                    ),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(12.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Folder,
                                                            contentDescription = null,
                                                            tint = Color(0xFFFFB300)
                                                        )
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Text(
                                                            text = folder.name,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            else -> Unit
                        }
                    }
                },
                confirmButton = {
                    Row {
                        TextButton(
                            onClick = {
                                selectedFolderId = activeStack.last().first
                                selectedFolderName = activeStack.last().second
                                sharedPrefs.edit()
                                    .putString("pinned_folder_id", selectedFolderId)
                                    .putString("pinned_folder_name", selectedFolderName)
                                    .apply()
                                Toast.makeText(context, "Pinned '$selectedFolderName'!", Toast.LENGTH_SHORT).show()
                                driveBrowserState = DriveBrowserModalState.Closed
                            }
                        ) {
                            Text("Pin Folder")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                selectedFolderId = activeStack.last().first
                                selectedFolderName = activeStack.last().second
                                driveBrowserState = DriveBrowserModalState.Closed
                            }
                        ) {
                            Text("Select This Folder")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { driveBrowserState = DriveBrowserModalState.Closed }) {
                        Text("Cancel")
                    }
                }
            )

            // Nested New Folder Creation sub-state inside DriveBrowserModalState.Browsing
            if (browser is DriveBrowserModalState.Browsing && browser.isCreatingNewFolder) {
                AlertDialog(
                    onDismissRequest = {
                        driveBrowserState = browser.copy(isCreatingNewFolder = false, newFolderName = "")
                    },
                    title = { Text("Create New Folder") },
                    text = {
                        OutlinedTextField(
                            value = browser.newFolderName,
                            onValueChange = { updated ->
                                driveBrowserState = browser.copy(newFolderName = updated)
                            },
                            label = { Text("Folder Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val folderName = browser.newFolderName.trim()
                                if (folderName.isNotBlank()) {
                                    driveBrowserState = DriveBrowserModalState.Loading(activeStack)
                                    coroutineScope.launch {
                                        try {
                                            val token = accessToken
                                            val parentId = activeStack.last().first
                                            if (token != null) {
                                                val createdId = GoogleDriveService.createFolder(token, folderName, parentId)
                                                val updatedFolders = GoogleDriveService.listFolders(token, parentId)
                                                driveBrowserState = DriveBrowserModalState.Browsing(activeStack, updatedFolders)
                                                if (createdId != null) {
                                                    Toast.makeText(context, "Folder created successfully!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Failed to create folder.", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                driveBrowserState = browser.copy(isCreatingNewFolder = false, newFolderName = "")
                                            }
                                        } catch (e: Throwable) {
                                            e.printStackTrace()
                                            Toast.makeText(context, "Failed to create folder: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                            driveBrowserState = browser.copy(isCreatingNewFolder = false, newFolderName = "")
                                        }
                                    }
                                }
                            }
                        ) {
                            Text("Create")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                driveBrowserState = browser.copy(isCreatingNewFolder = false, newFolderName = "")
                            }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }

    // Under Review or Expired Dialog (Secured Guard)
    if (showUnderReviewDialog) {
        val isExpired = viewModel.isExpired
        AlertDialog(
            onDismissRequest = { showUnderReviewDialog = false },
            icon = {
                Icon(
                    imageVector = if (isExpired) Icons.Default.Warning else Icons.Default.Info,
                    contentDescription = null,
                    tint = if (isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text(
                    text = if (isExpired) "Access Plan Expired" else "Account Pending Approval",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = if (isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Text(
                    text = if (isExpired) {
                        "Your subscription access plan has expired. Please contact DASMO Cyber Cafe Admin to purchase or renew your duration plan. You can explore all editing and layout creation features, but printing, exporting, and Google Drive uploads will remain blocked until access is renewed."
                    } else {
                        "Your device and account are registered and currently pending administrator approval. You can explore all editing and layout creation features, but printing, exporting, and Google Drive uploads will remain blocked until approval is granted. Please contact the administrator for access."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = { showUnderReviewDialog = false },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Got it")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export & Print Layout") },
                navigationIcon = {
                    IconButton(onClick = onBackClicked, modifier = Modifier.testTag("export_back_btn")) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Go back")
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
                    onClick = onFinishClicked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(50.dp)
                        .testTag("export_finish_btn")
                ) {
                    Icon(imageVector = Icons.Default.Home, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Return to home screen", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 3D Claymorphic Export & Print Ready Banner
            val stepTextRepr = if (viewModel.mode == com.example.domain.model.ProjectMode.JOINT) "STEP 5 OF 5" else "STEP 4 OF 4"
            ClayWizardStepBanner(
                stepLabel = "$stepTextRepr • READY TO PRINT",
                title = "Save, Share or Direct Print",
                subtitle = "${viewModel.computedPages.size} sheet(s) compiled at ${viewModel.dpi} DPI on ${viewModel.selectedPaperSpec.name}",
                progress = 1.0f,
                accentColor = Color(0xFF00B894),
                artworkRes = R.drawable.img_clay_paper_saver
            )

            // Dynamic renaming card field (Pre filled with time stamped labels)
            OutlinedTextField(
                value = viewModel.filename,
                onValueChange = { viewModel.filename = it },
                label = { Text("Export PDF Filename") },
                modifier = Modifier.fillMaxWidth().testTag("export_filename_field"),
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = null)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Exhaustive PDF Compilation State Machine Banner
            when (val pdfState = viewModel.pdfExportState) {
                is com.example.ui.PdfExportState.Idle -> Unit
                is com.example.ui.PdfExportState.Compiling -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Compiling layouts and drawing vector targets...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                is com.example.ui.PdfExportState.Success -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth().testTag("export_save_banner"),
                        color = Color(0xFFE8F5E9),
                        contentColor = Color(0xFF2E7D32),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = pdfState.message,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                is com.example.ui.PdfExportState.Error -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth().testTag("export_save_banner"),
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = pdfState.message,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // PRIMARY ACTION Triggers Row
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // PRINTER ALIGNMENT CALIBRATION CARD (Feature 4)
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("printer_calibration_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Printer Alignment Calibration",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Text(
                            text = "Calibrate margins (±mm) to align with physical printer paper feed trays.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = viewModel.topOffsetMm,
                                onValueChange = {
                                    viewModel.topOffsetMm = it
                                    viewModel.computeCurrentLayout()
                                },
                                label = { Text("Top Offset (mm)") },
                                modifier = Modifier.weight(1f).testTag("top_offset_field")
                            )

                            OutlinedTextField(
                                value = viewModel.leftOffsetMm,
                                onValueChange = {
                                    viewModel.leftOffsetMm = it
                                    viewModel.computeCurrentLayout()
                                },
                                label = { Text("Left Offset (mm)") },
                                modifier = Modifier.weight(1f).testTag("left_offset_field")
                            )
                        }
                    }
                }

                // CYBER CAFE BUSINESS COST CALCULATOR & INVOICING CARD (Feature 5)
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("business_cost_calculator_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Cyber Café Cost Calculator & Billing",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = viewModel.customerName,
                                onValueChange = { viewModel.customerName = it },
                                label = { Text("Customer Name") },
                                modifier = Modifier.weight(1f).testTag("customer_name_field")
                            )

                            OutlinedTextField(
                                value = viewModel.customerPhone,
                                onValueChange = { viewModel.customerPhone = it },
                                label = { Text("Phone Number") },
                                modifier = Modifier.weight(1f).testTag("customer_phone_field")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = viewModel.ratePerSheet,
                                onValueChange = { viewModel.ratePerSheet = it },
                                label = { Text("Rate / Sheet (₹)") },
                                modifier = Modifier.weight(1f).testTag("rate_per_sheet_field")
                            )

                            OutlinedTextField(
                                value = viewModel.extraServicesFee,
                                onValueChange = { viewModel.extraServicesFee = it },
                                label = { Text("Edits/Suit Fee (₹)") },
                                modifier = Modifier.weight(1f).testTag("extra_fee_field")
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val totalSheets = viewModel.computedPages.size.coerceAtLeast(1)
                        val rate = viewModel.ratePerSheet.toDoubleOrNull() ?: 20.0
                        val extra = viewModel.extraServicesFee.toDoubleOrNull() ?: 0.0
                        val totalBill = (totalSheets * rate) + extra

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "$totalSheets Sheet(s) @ ₹$rate/sheet + ₹$extra edits",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        text = "Total Billing: ₹${String.format("%.2f", totalBill)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }

                                Button(
                                    onClick = {
                                        val cName = if (viewModel.customerName.isNotBlank()) viewModel.customerName else "Valued Customer"
                                        val text = "🧾 *DASMO PHOTO PRINT BILL*\n\nCustomer: $cName\nJob: ${viewModel.filename}\nSheets: $totalSheets sheet(s)\nTotal Amount: ₹${String.format("%.2f", totalBill)}\n\nThank you for choosing DASMO Cyber Café!"
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, text)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share WhatsApp Invoice"))
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF25D366), // WhatsApp Green
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("Send Invoice", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // ACTION 1: Direct System Print Spooler (CRITICAL CORE REQUIREMENT)
                Button(
                    onClick = {
                        if (!viewModel.isApproved) {
                            showUnderReviewDialog = true
                        } else if (localCachePdfFile.exists()) {
                            PrintUtils.printPdf(
                                context = context,
                                pdfUri = localCachePdfUri,
                                jobName = viewModel.filename,
                                isLandscape = viewModel.pageOrientation == com.example.domain.model.PageOrientation.LANDSCAPE
                            )
                        } else {
                            Toast.makeText(context, "Please wait until the PDF finishes compiling.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("action_print_now_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(imageVector = Icons.Default.Print, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Print Now (${viewModel.selectedPaperSpec.name})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // GOOGLE DRIVE SYNC CARD
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("google_drive_sync_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CloudQueue,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Google Drive Upload",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (googleAccount != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "Connected",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (googleAccount == null) {
                            Text(
                                text = "Connect your Google account to select folders and upload PDFs directly to your Google Drive in high quality so that you can access and print them from your PC seamlessly.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    val signInClient = GoogleDriveService.getSignInClient(context)
                                    signInLauncher.launch(signInClient.signInIntent)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("connect_drive_btn")
                            ) {
                                Icon(imageVector = Icons.Default.Login, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Connect Google Drive", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Text(
                                text = "Connected as: ${googleAccount?.email}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Current Selected Destination Folder Info
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FolderOpen,
                                            contentDescription = null,
                                            tint = Color(0xFFFFB300),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "Upload Destination",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = selectedFolderName,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    TextButton(
                                        onClick = {
                                            val account = googleAccount?.account
                                            if (account != null) {
                                                val initialStack = listOf("root" to "My Drive")
                                                driveBrowserState = DriveBrowserModalState.Loading(initialStack)
                                                coroutineScope.launch {
                                                    try {
                                                        val token = GoogleDriveService.getAccessToken(context, account)
                                                        if (token != null) {
                                                            accessToken = token
                                                            val folders = GoogleDriveService.listFolders(token, "root")
                                                            driveBrowserState = DriveBrowserModalState.Browsing(initialStack, folders)
                                                        } else {
                                                            Toast.makeText(context, "Failed to connect. Please sign in again.", Toast.LENGTH_SHORT).show()
                                                            driveBrowserState = DriveBrowserModalState.Closed
                                                        }
                                                    } catch (e: UserRecoverableAuthException) {
                                                        context.startActivity(e.intent)
                                                        driveBrowserState = DriveBrowserModalState.Closed
                                                    } catch (e: Throwable) {
                                                        e.printStackTrace()
                                                        Toast.makeText(context, "Failed to connect: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                        driveBrowserState = DriveBrowserModalState.Closed
                                                    }
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Select Folder")
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            val isCurrentlyUploading = driveUploadState is DriveUploadState.Uploading
                            Button(
                                onClick = {
                                    if (!viewModel.isApproved) {
                                        showUnderReviewDialog = true
                                    } else if (localCachePdfFile.exists() && !isCurrentlyUploading) {
                                        val account = googleAccount?.account
                                        if (account != null) {
                                            driveUploadState = DriveUploadState.Uploading
                                            coroutineScope.launch {
                                                try {
                                                    val token = GoogleDriveService.getAccessToken(context, account)
                                                    if (token != null) {
                                                        accessToken = token
                                                        val success = GoogleDriveService.uploadPdf(
                                                            token,
                                                            localCachePdfFile,
                                                            viewModel.filename,
                                                            selectedFolderId
                                                        )
                                                        if (success) {
                                                            driveUploadState = DriveUploadState.Success("Successfully uploaded to folder '$selectedFolderName'!")
                                                            Toast.makeText(context, "Uploaded to Google Drive!", Toast.LENGTH_LONG).show()
                                                        } else {
                                                            driveUploadState = DriveUploadState.Error("Upload failed. Please check permissions.")
                                                            Toast.makeText(context, "Upload failed. Please check permissions.", Toast.LENGTH_LONG).show()
                                                        }
                                                    } else {
                                                        driveUploadState = DriveUploadState.Error("Auth token expired. Logging in again.")
                                                        Toast.makeText(context, "Auth token expired. Logging in again.", Toast.LENGTH_SHORT).show()
                                                    }
                                                } catch (e: UserRecoverableAuthException) {
                                                    driveUploadState = DriveUploadState.Idle
                                                    context.startActivity(e.intent)
                                                } catch (e: Throwable) {
                                                    e.printStackTrace()
                                                    driveUploadState = DriveUploadState.Error("Upload failed: ${e.localizedMessage}")
                                                    Toast.makeText(context, "Upload failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    } else {
                                        Toast.makeText(context, "Please wait for PDF to compile before uploading.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("upload_to_drive_btn"),
                                enabled = localCachePdfFile.exists() && !isCurrentlyUploading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                if (isCurrentlyUploading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                                } else {
                                    Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Upload PDF to Drive Now")
                                }
                            }

                            when (val uploadState = driveUploadState) {
                                is DriveUploadState.Idle,
                                is DriveUploadState.Uploading -> Unit
                                is DriveUploadState.Success -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp)
                                            .background(Color(0xFFE8F5E9), shape = RoundedCornerShape(8.dp))
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF2E7D32),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = uploadState.message,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF2E7D32),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                is DriveUploadState.Error -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp)
                                            .background(MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp))
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Error,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = uploadState.message,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Disconnect/Logout option
                            Text(
                                text = "Disconnect Account",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .clickable {
                                        coroutineScope.launch {
                                            val client = GoogleDriveService.getSignInClient(context)
                                            client.signOut()
                                            googleAccount = null
                                            accessToken = null
                                            selectedFolderId = "root"
                                            selectedFolderName = "My Drive"
                                            driveUploadState = DriveUploadState.Idle
                                            driveBrowserState = DriveBrowserModalState.Closed
                                            Toast.makeText(context, "Disconnected Google Drive account.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            )
                        }
                    }
                }

                // ACTION 2: Save File to Device storage (Explicit DiskSaveState Machine)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Permanent Storage",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val isCurrentlySavingDisk = diskSaveState is DiskSaveState.Saving
                        OutlinedButton(
                            onClick = {
                                if (!viewModel.isApproved) {
                                    showUnderReviewDialog = true
                                } else if (localCachePdfFile.exists() && !isCurrentlySavingDisk) {
                                    diskSaveState = DiskSaveState.Saving
                                    val publicUri = writeToPublicDocuments(context, viewModel.filename, localCachePdfFile)
                                    if (publicUri != null) {
                                        diskSaveState = DiskSaveState.Saved("Saved copy in Documents / PassportPhotos")
                                        Toast.makeText(context, "Saved successfully into Documents folder!", Toast.LENGTH_LONG).show()
                                    } else {
                                        diskSaveState = DiskSaveState.Error("Failed to export file to external downloads.")
                                        Toast.makeText(context, "Failed to export file to external downloads.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("action_save_to_documents_btn"),
                            enabled = localCachePdfFile.exists() && !isCurrentlySavingDisk
                        ) {
                            if (isCurrentlySavingDisk) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            } else {
                                Icon(imageVector = Icons.Default.Save, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Export copy to Documents Folder")
                            }
                        }

                        when (val saveState = diskSaveState) {
                            is DiskSaveState.Idle,
                            is DiskSaveState.Saving -> Unit
                            is DiskSaveState.Saved -> {
                                Text(
                                    text = saveState.pathInfo,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF2E7D32),
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .testTag("save_path_info_text")
                                )
                            }
                            is DiskSaveState.Error -> {
                                Text(
                                    text = saveState.message,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .testTag("save_path_info_text")
                                )
                            }
                        }
                    }
                }

                // ACTION 3: Secure Intent Sharing
                OutlinedButton(
                    onClick = {
                        if (!viewModel.isApproved) {
                            showUnderReviewDialog = true
                        } else if (localCachePdfFile.exists()) {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, localCachePdfUri)
                                putExtra(Intent.EXTRA_SUBJECT, viewModel.filename)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Print Document"))
                        } else {
                            Toast.makeText(context, "PDF layout compiling still.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("action_share_btn"),
                    enabled = localCachePdfFile.exists()
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share PDF to WhatsApp / Device")
                }
            }
        }
    }
}

/**
 * Copies Sandbox PDF document file stream directly into public external MediaStore Documents directory.
 * Works permissionless on modern Android versions utilizing scoped storage pathways.
 */
fun writeToPublicDocuments(context: Context, filename: String, cacheFile: File): Uri? {
    try {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$filename.pdf")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/PassportPhotos")
            }
        }
        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri("external")
        } else {
            // Decoupling legacy database file inserts
            MediaStore.Files.getContentUri("external")
        }

        val targetUri = resolver.insert(collectionUri, contentValues)
        if (targetUri != null) {
            resolver.openOutputStream(targetUri)?.use { output ->
                FileInputStream(cacheFile).use { input ->
                    input.copyTo(output)
                }
            }
            return targetUri
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}
