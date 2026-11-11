package com.example.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.ProjectMode
import com.example.ui.ProjectViewModel
import com.example.ui.components.ClayArtworkBadge
import com.example.ui.components.ClayCard
import com.example.ui.components.ClayIconOrb
import com.example.ui.components.ClayPillBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSelectScreen(
    viewModel: ProjectViewModel,
    onBackClicked: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Choose Studio Job Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Select the print layout workflow for this customer",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClicked,
                        modifier = Modifier.testTag("mode_select_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Wizard Step Progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Step 1 of 5",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(10.dp))
                LinearProgressIndicator(
                    progress = { 0.2f },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Mode 1: Single Passport Photo (with 3D Clay Passport Artwork)
            ClayJobModeCard(
                title = "Single Passport Photo",
                badgeText = "MOST POPULAR",
                description = "Standard passport, visa, PAN card, or stamp-size grid for one person (e.g., 3.5 × 4.5 cm).",
                icon = Icons.Default.Person,
                artworkRes = R.drawable.img_clay_passport,
                accentColor = MaterialTheme.colorScheme.primary,
                testTag = "mode_single_card",
                onClick = { viewModel.selectMode(ProjectMode.SINGLE) }
            )

            // Mode 2: Mixed-Size Batch (Paper Saver) (with 3D Clay Paper Saver Artwork)
            ClayJobModeCard(
                title = "Mixed-Size Batch (Paper Saver)",
                badgeText = "PAPER SAVER",
                description = "Combine multiple photo sizes (Passport + Stamp + Postcard) onto one A4 or 4×6\" sheet.",
                icon = Icons.Default.GridView,
                artworkRes = R.drawable.img_clay_paper_saver,
                accentColor = Color(0xFFEF6C00),
                testTag = "mode_batch_card",
                onClick = { viewModel.selectMode(ProjectMode.BATCH_PAPER_SAVER) }
            )

            // Mode 3: Joint Couple Photo
            ClayJobModeCard(
                title = "Joint Couple Photo",
                badgeText = "COUPLE / BANK",
                description = "Combine two individual portraits side-by-side into a single joint frame for bank or post office accounts.",
                icon = Icons.Default.People,
                accentColor = Color(0xFF00796B),
                testTag = "mode_joint_card",
                onClick = { viewModel.selectMode(ProjectMode.JOINT) }
            )

            // Mode 4: ID Card (Front & Back)
            ClayJobModeCard(
                title = "ID Card (Front & Back)",
                badgeText = "GOVT PVC / A4",
                description = "Align Front and Back scans of Aadhaar, PAN, Voter ID, or Driving License at exact CR-80 card dimensions (8.5 × 5.4 cm).",
                icon = Icons.Default.Badge,
                accentColor = Color(0xFF6A1B9A),
                testTag = "mode_id_card",
                onClick = { viewModel.selectMode(ProjectMode.ID_CARD) }
            )

            // Mode 5: 2-Customer Split Sheet
            ClayJobModeCard(
                title = "2-Customer Split Sheet",
                badgeText = "2 CUSTOMERS",
                description = "Print passport photos for two separate customers on the same sheet to save 50% glossy paper.",
                icon = Icons.Default.GroupAdd,
                accentColor = Color(0xFF2E7D32),
                testTag = "mode_multi_person_card",
                onClick = { viewModel.selectMode(ProjectMode.MULTI_PERSON) }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ClayJobModeCard(
    title: String,
    badgeText: String,
    description: String,
    icon: ImageVector,
    @DrawableRes artworkRes: Int? = null,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    ClayCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        accentColor = accentColor,
        cornerRadius = 24.dp,
        elevation = 5.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (artworkRes != null) {
                ClayArtworkBadge(
                    drawableRes = artworkRes,
                    accentColor = accentColor,
                    size = 62.dp,
                    cornerRadius = 20.dp
                )
            } else {
                ClayIconOrb(
                    icon = icon,
                    accentColor = accentColor,
                    size = 60.dp,
                    iconSize = 28.dp,
                    cornerRadius = 20.dp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ClayPillBadge(
                        text = badgeText,
                        accentColor = accentColor
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = accentColor
            )
        }
    }
}
