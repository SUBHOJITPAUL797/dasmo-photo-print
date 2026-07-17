package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.ProjectViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JointComposeScreen(
    viewModel: ProjectViewModel,
    onBackClicked: () -> Unit,
    onNextClicked: () -> Unit
) {
    val scrollState = rememberScrollState()

    // Trigger composition regeneration when ratio or divider settings change
    LaunchedEffect(viewModel.jointSplitRatio, viewModel.jointDividerLinesEnabled, viewModel.jointDividerColor) {
        viewModel.generateJointComposite()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compose Joint Photo") },
                navigationIcon = {
                    IconButton(onClick = onBackClicked, modifier = Modifier.testTag("joint_back_btn")) {
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(50.dp)
                        .testTag("joint_next_btn")
                ) {
                    Text("Approve & Compose Layout", style = MaterialTheme.typography.titleMedium)
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Step marker indicator
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Step 5 of 5",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                LinearProgressIndicator(
                    progress = { 1.0f },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = "Balance & Combine Side-by-Side",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // Dynamic Composition Preview Box
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .testTag("joint_composite_preview_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val combinedBmp = viewModel.finalUnitBitmap
                    if (combinedBmp != null) {
                        Image(
                            bitmap = combinedBmp.asImageBitmap(),
                            contentDescription = "Joint photo live composite",
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(combinedBmp.width.toFloat() / combinedBmp.height.toFloat())
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        CircularProgressIndicator()
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Split width balancer slider
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Split Balance Ratio: ${(viewModel.jointSplitRatio * 100).toInt()}% / ${((1f - viewModel.jointSplitRatio) * 100).toInt()}%",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Adjust the divider to shift how much horizontal spacing is assigned to Person 1 vs Person 2.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Left Heavy", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("joint_split_label_left"))
                        Slider(
                            value = viewModel.jointSplitRatio,
                            onValueChange = { viewModel.jointSplitRatio = it },
                            onValueChangeFinished = { viewModel.pushHistoryState() },
                            valueRange = 0.3f..0.7f,
                            modifier = Modifier.weight(1f).testTag("joint_split_slider")
                        )
                        Text("Right Heavy", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("joint_split_label_right"))
                    }
                }
            }

            // Divider Lines Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Render Divider Line", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("Draw a thin solid divider line separating the two photos.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = viewModel.jointDividerLinesEnabled,
                            onCheckedChange = { 
                                viewModel.jointDividerLinesEnabled = it 
                                viewModel.pushHistoryState()
                            },
                            modifier = Modifier.testTag("joint_divider_switch")
                        )
                    }

                    if (viewModel.jointDividerLinesEnabled) {
                        // Divider colors configuration option
                        Column {
                            Text("Divider Color:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val colorOptions = listOf(
                                    Pair("Black", 0xFF000000.toInt()),
                                    Pair("Dark Gray", 0xFF666666.toInt()),
                                    Pair("White", 0xFFFFFFFF.toInt())
                                )

                                colorOptions.forEach { opt ->
                                    val isSelected = viewModel.jointDividerColor == opt.second
                                    InputChip(
                                        selected = isSelected,
                                        onClick = { 
                                            viewModel.jointDividerColor = opt.second 
                                            viewModel.pushHistoryState()
                                        },
                                        label = { Text(opt.first) },
                                        modifier = Modifier.weight(1f).testTag("joint_divider_color_${opt.first.replace(" ", "_")}")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
