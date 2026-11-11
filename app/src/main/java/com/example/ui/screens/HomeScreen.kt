package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.Project
import com.example.domain.model.ProjectMode
import com.example.ui.ProjectViewModel
import com.example.ui.components.ClayArtworkBadge
import com.example.ui.components.ClayCard
import com.example.ui.components.ClayIconOrb
import com.example.ui.components.ClayPillBadge
import com.example.ui.components.ClayQuickBentoTile
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isAdmin: Boolean = false,
    onAdminClicked: () -> Unit = {},
    onLogoutClicked: () -> Unit = {},
    viewModel: ProjectViewModel,
    onNewProjectClicked: () -> Unit,
    onReprintClicked: (Project) -> Unit
) {
    val recentProjects by viewModel.recentProjects.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var projectToDelete by remember { mutableStateOf<Project?>(null) }
    var photoToPreview by remember { mutableStateOf<Project?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<com.example.util.UpdateChecker.UpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ClayIconOrb(
                            icon = Icons.Default.Print,
                            accentColor = MaterialTheme.colorScheme.primary,
                            size = 36.dp,
                            iconSize = 18.dp,
                            cornerRadius = 12.dp
                        )
                        Column {
                            Text(
                                text = "DASMO PHOTO PRINT",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "300 DPI Studio & Cyber Suite",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                actions = {
                    if (isAdmin) {
                        FilledTonalButton(
                            onClick = onAdminClicked,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("admin_panel_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = "Admin Panel",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Admin", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("app_settings_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "App Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onLogoutClicked,
                        modifier = Modifier.testTag("logout_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Logout",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewProjectClicked,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .shadow(8.dp, RoundedCornerShape(20.dp))
                    .testTag("new_project_fab")
            ) {
                Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = "New Print Job")
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Print Job", fontWeight = FontWeight.Black)
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 3D Clay Hero Showcase Card (Swiggy/Blinkit Banner Style)
            item {
                val (statusText, statusColor) = when {
                    isAdmin -> "ADMIN • FULL STUDIO ACCESS" to Color(0xFF1565C0)
                    viewModel.isExpired -> "ACCESS PLAN EXPIRED" to MaterialTheme.colorScheme.error
                    viewModel.isApproved -> "ACTIVE LICENSE • READY TO PRINT" to Color(0xFF2E7D32)
                    else -> "PENDING APPROVAL" to Color(0xFFEF6C00)
                }

                ClayCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = MaterialTheme.colorScheme.primary,
                    cornerRadius = 26.dp,
                    elevation = 8.dp,
                    onClick = onNewProjectClicked
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Subtle top background gradient
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f)
                                        )
                                    )
                                )
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1.25f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ClayPillBadge(
                                    text = statusText,
                                    accentColor = statusColor
                                )

                                Text(
                                    text = "Instant Passport & Studio Sheets",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 26.sp
                                )

                                Text(
                                    text = "Exact cm sizing, AI suit & background tools, pitch-black cut guides & 300 DPI A4/4×6 export.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Button(
                                    onClick = onNewProjectClicked,
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    modifier = Modifier.height(40.dp)
                                ) {
                                    Text("Start Creating", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // 3D Clay Hero Artwork
                            Box(
                                modifier = Modifier
                                    .size(width = 122.dp, height = 112.dp)
                                    .shadow(8.dp, RoundedCornerShape(22.dp))
                                    .clip(RoundedCornerShape(22.dp))
                                    .border(
                                        width = 2.dp,
                                        brush = Brush.linearGradient(
                                            listOf(
                                                Color.White.copy(alpha = 0.85f),
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                            )
                                        ),
                                        shape = RoundedCornerShape(22.dp)
                                    )
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_clay_studio_hero),
                                    contentDescription = "3D Clay Studio Printer",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }

            // 2. Quick Studio Modes (Swiggy / Blinkit 2x2 3D Clay Bento Grid)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Quick Launch Studio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "View All 5 Modes →",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onNewProjectClicked() }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ClayQuickBentoTile(
                            title = "Passport Photo",
                            subtitle = "3.5 × 4.5 cm • Visa / Govt",
                            badgeText = "POPULAR",
                            accentColor = MaterialTheme.colorScheme.primary,
                            icon = Icons.Default.Person,
                            artworkRes = R.drawable.img_clay_passport,
                            onClick = {
                                viewModel.startNewProject()
                                viewModel.selectMode(ProjectMode.SINGLE)
                            },
                            modifier = Modifier.weight(1f)
                        )

                        ClayQuickBentoTile(
                            title = "Paper Saver",
                            subtitle = "Mix sizes on 1 sheet",
                            badgeText = "SAVER",
                            accentColor = Color(0xFFEF6C00),
                            icon = Icons.Default.GridView,
                            artworkRes = R.drawable.img_clay_paper_saver,
                            onClick = {
                                viewModel.startNewProject()
                                viewModel.selectMode(ProjectMode.BATCH_PAPER_SAVER)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ClayQuickBentoTile(
                            title = "Joint Couple",
                            subtitle = "Side-by-side bank photo",
                            badgeText = "COUPLE",
                            accentColor = Color(0xFF00796B),
                            icon = Icons.Default.People,
                            onClick = {
                                viewModel.startNewProject()
                                viewModel.selectMode(ProjectMode.JOINT)
                            },
                            modifier = Modifier.weight(1f)
                        )

                        ClayQuickBentoTile(
                            title = "ID Card / 2-Client",
                            subtitle = "Front & Back or 2 Persons",
                            badgeText = "STUDIO",
                            accentColor = Color(0xFF6A1B9A),
                            icon = Icons.Default.Badge,
                            onClick = {
                                viewModel.startNewProject()
                                viewModel.selectMode(ProjectMode.ID_CARD)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 3. Recent Print Jobs Section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Print Jobs (${recentProjects.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (recentProjects.isNotEmpty()) {
                        Text(
                            text = "Tap to Reprint",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (recentProjects.isEmpty()) {
                item {
                    ClayCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = MaterialTheme.colorScheme.primary,
                        cornerRadius = 24.dp,
                        elevation = 4.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            ClayArtworkBadge(
                                drawableRes = R.drawable.img_clay_passport,
                                accentColor = MaterialTheme.colorScheme.primary,
                                size = 76.dp,
                                cornerRadius = 24.dp
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "No Print Jobs Saved Yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Pick any Quick Studio Mode above or tap 'New Print Job' to crop photos, add solid or dashed pitch-black borders, and print in 300 DPI.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            Button(
                                onClick = onNewProjectClicked,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.testTag("new_project_empty_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Create First Print Layout", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                items(items = recentProjects, key = { it.id }) { project ->
                    val modeAccent = when (project.mode) {
                        "BATCH_PAPER_SAVER" -> Color(0xFFEF6C00)
                        "JOINT" -> Color(0xFF00796B)
                        "ID_CARD" -> Color(0xFF6A1B9A)
                        "MULTI_PERSON" -> Color(0xFF2E7D32)
                        else -> MaterialTheme.colorScheme.primary
                    }

                    ClayCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onReprintClicked(project) },
                                onLongClick = { projectToDelete = project }
                            )
                            .testTag("project_item_${project.id}"),
                        accentColor = modeAccent,
                        cornerRadius = 20.dp,
                        elevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val uriToShow = project.photoAUri
                            if (uriToShow.isNullOrEmpty()) {
                                ClayIconOrb(
                                    icon = when (project.mode) {
                                        "JOINT" -> Icons.Default.People
                                        "MULTI_PERSON" -> Icons.Default.GroupAdd
                                        "BATCH_PAPER_SAVER" -> Icons.Default.GridView
                                        "ID_CARD" -> Icons.Default.Badge
                                        else -> Icons.Default.Person
                                    },
                                    accentColor = modeAccent,
                                    size = 54.dp,
                                    iconSize = 26.dp,
                                    cornerRadius = 16.dp
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .shadow(4.dp, RoundedCornerShape(16.dp))
                                        .clip(RoundedCornerShape(16.dp))
                                        .border(
                                            1.5.dp,
                                            modeAccent.copy(alpha = 0.45f),
                                            RoundedCornerShape(16.dp)
                                        )
                                        .clickable { photoToPreview = project }
                                ) {
                                    coil.compose.AsyncImage(
                                        model = uriToShow,
                                        contentDescription = "Project Photo",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = project.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    val modeBadgeLabel = when (project.mode) {
                                        "BATCH_PAPER_SAVER" -> "MIXED"
                                        "JOINT" -> "JOINT"
                                        "ID_CARD" -> "ID CARD"
                                        "MULTI_PERSON" -> "2-CLIENT"
                                        else -> "PASSPORT"
                                    }
                                    ClayPillBadge(
                                        text = modeBadgeLabel,
                                        accentColor = modeAccent
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text(
                                        text = "${project.unitWidthCm} × ${project.unitHeightCm} cm",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = modeAccent
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "•",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${project.quantity} Prints",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!project.customerName.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "• ${project.customerName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                                Text(
                                    text = dateFormat.format(Date(project.createdAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                    modifier = Modifier.padding(top = 3.dp)
                                )
                            }

                            IconButton(
                                onClick = { projectToDelete = project },
                                modifier = Modifier.testTag("delete_project_${project.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete Job Record",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirm Delete Dialog
    if (projectToDelete != null) {
        val proj = projectToDelete!!
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("Delete Print Job", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to remove '${proj.name}' from recent print jobs?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProject(proj)
                        projectToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_btn")
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (photoToPreview != null) {
        val proj = photoToPreview!!
        androidx.compose.ui.window.Dialog(onDismissRequest = { photoToPreview = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                coil.compose.AsyncImage(
                    model = proj.photoAUri,
                    contentDescription = "Full Photo Preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .clickable { photoToPreview = null },
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { photoToPreview = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Preview",
                        tint = Color.White
                    )
                }
            }
        }
    }

    if (showSettingsDialog) {
        val googleAccount = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)

        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            icon = {
                ClayIconOrb(
                    icon = Icons.Default.Settings,
                    accentColor = MaterialTheme.colorScheme.primary,
                    size = 44.dp,
                    iconSize = 22.dp,
                    cornerRadius = 14.dp
                )
            },
            title = {
                Text(
                    text = "App Settings & Studio Info",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "DASMO PHOTO PRINT",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Cyber Café & Photo Studio Edition",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val settingsStatusText = when {
                                isAdmin -> "Status: Administrator (Full Access Enabled)"
                                viewModel.isExpired -> "Status: Access Plan Expired"
                                viewModel.isApproved -> "Status: Active Subscription Plan"
                                else -> "Status: Pending Administrator Approval"
                            }
                            Text(
                                text = settingsStatusText,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (viewModel.isApproved || isAdmin) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Google Drive Connection",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (googleAccount != null) "Connected: ${googleAccount.email}" else "Not Connected (Connect in Export Screen)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                Toast.makeText(context, "Checking GitHub for updates...", Toast.LENGTH_SHORT).show()
                                scope.launch {
                                    try {
                                        val info = com.example.util.UpdateChecker.checkForUpdates(context)
                                        updateInfo = info
                                        if (info.hasUpdate) {
                                            showUpdateDialog = true
                                        } else {
                                            Toast.makeText(context, "You are using the latest version (v${info.currentVersion})", Toast.LENGTH_LONG).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Update check failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Check for Updates",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Fetch latest APK release & changelogs from GitHub",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = "Update",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Default Print Specifications",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "• Resolution: 300 DPI High-Quality\n• Standard Sizes: A4, 4×6\", 5×7\", Custom\n• Cut Guides: Solid / Dashed in Pitch Black & Custom Colors",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showSettingsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showUpdateDialog && updateInfo != null) {
        InAppUpdateDialog(
            updateInfo = updateInfo!!,
            onDismiss = { showUpdateDialog = false }
        )
    }
}
