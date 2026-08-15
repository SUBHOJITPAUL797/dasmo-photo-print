package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.UserAccount
import com.example.ui.AuthViewModel
import com.example.util.UpdateChecker
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onLoginSuccess: (String, String) -> Unit
) {
    val context = LocalContext.current
    var isSigningIn by remember { mutableStateOf(false) }
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isSigningIn = false
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val email = account?.email
                if (!email.isNullOrBlank()) {
                    authViewModel.login(context, email, null) { token -> onLoginSuccess(email, token) }
                }
            } catch (e: ApiException) {
                e.printStackTrace()
            }
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Security",
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Secure Device Login",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Please continue with your Google account. Your account will be securely bound to this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = { 
                        isSigningIn = true
                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestEmail()
                            .build()
                        val mGoogleSignInClient = GoogleSignIn.getClient(context, gso)
                        launcher.launch(mGoogleSignInClient.signInIntent)
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !isSigningIn
                ) {
                    if (isSigningIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Continue with Google")
                    }
                }
            }
        }
    }
}

@Composable
fun PendingApprovalScreen(
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp).verticalScroll(rememberScrollState())
        ) {
            Icon(
                imageVector = Icons.Default.HourglassEmpty,
                contentDescription = "Pending",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = "Pending Admin Approval",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Your device and account have been registered. Please wait for the administrator to grant you access.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Button(onClick = onRetry) {
                Text("Check Status")
            }
        }
    }
}

@Composable
fun AuthErrorScreen(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp).verticalScroll(rememberScrollState())
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = "Error",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = "Connection Failed",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Button(onClick = onRetry) {
                Text("Go Back")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    authViewModel: AuthViewModel,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        authViewModel.startListeningUsers(context)
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") } // "All", "Pending", "Approved", "Rejected", "Admins"
    var showAddUserDialog by remember { mutableStateOf(false) }
    var showExpiryDialogForUser by remember { mutableStateOf<UserAccount?>(null) }
    
    // In-app update config states
    var showUpdateConfigDialog by remember { mutableStateOf(false) }
    var configOwner by remember { mutableStateOf(UpdateChecker.getGithubOwner(context)) }
    var configRepo by remember { mutableStateOf(UpdateChecker.getGithubRepo(context)) }
    
    // Dialog state for manually adding user
    var manualEmail by remember { mutableStateOf("") }
    var manualRole by remember { mutableStateOf("user") }
    var manualStatus by remember { mutableStateOf("approved") }
    
    // Confirmation dialog state
    var pendingAction by remember { mutableStateOf<Triple<UserAccount, String, () -> Unit>?>(null) } // (User, ActionText, OnConfirm)

    val allUsers = authViewModel.allUsers
    
    // Compute stats
    val totalCount = allUsers.size
    val pendingCount = allUsers.count { it.status == "pending" }
    val approvedCount = allUsers.count { it.status == "approved" }

    // Filtered users list
    val filteredUsers = remember(allUsers, searchQuery, selectedFilter) {
        allUsers.filter { user ->
            val matchesSearch = user.email.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                "All" -> true
                "Pending" -> user.status == "pending"
                "Approved" -> user.status == "approved"
                "Rejected" -> user.status == "rejected"
                "Admins" -> user.role == "admin"
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Admin Control Panel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("DASMO Cyber Cafe Edition", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showUpdateConfigDialog = true }) {
                        Icon(Icons.Default.SystemUpdate, "Update Source Settings")
                    }
                    IconButton(onClick = { authViewModel.fetchAllUsers() }) {
                        Icon(Icons.Default.Refresh, "Refresh List")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    manualEmail = ""
                    manualRole = "user"
                    manualStatus = "approved"
                    showAddUserDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = "Add User")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pre-Approve Account", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            // Stats Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(title = "Total Users", value = totalCount.toString(), modifier = Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.secondaryContainer)
                StatCard(title = "Pending", value = pendingCount.toString(), modifier = Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                StatCard(title = "Approved", value = approvedCount.toString(), modifier = Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.primaryContainer)
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search by user email") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf("All", "Pending", "Approved", "Rejected", "Admins")
                filters.forEach { filter ->
                    val isSelected = selectedFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            if (filteredUsers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No users matching '$searchQuery'" else "No users in this category",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredUsers) { user ->
                        EnhancedUserAdminCard(
                            user = user,
                            currentUserEmail = authViewModel.currentUser?.email ?: "",
                            onStatusChange = { newStatus ->
                                val actionText = when(newStatus) {
                                    "approved" -> "approve access for"
                                    "rejected" -> "revoke and reject access for"
                                    else -> "set status to $newStatus for"
                                }
                                pendingAction = Triple(user, "Are you sure you want to $actionText ${user.email}?", {
                                    authViewModel.updateUserStatus(user.email, newStatus)
                                })
                            },
                            onRoleChange = { newRole ->
                                pendingAction = Triple(user, "Are you sure you want to change ${user.email}'s role to ${newRole.uppercase()}?", {
                                    authViewModel.updateUserRole(user.email, newRole)
                                })
                            },
                            onResetDevice = {
                                pendingAction = Triple(user, "Are you sure you want to UNBIND the device for ${user.email}? This will allow them to login from a new device.", {
                                    authViewModel.revokeDevice(user.email)
                                })
                            },
                            onDeleteUser = {
                                pendingAction = Triple(user, "Are you sure you want to PERMANENTLY DELETE the user ${user.email}? This action cannot be undone.", {
                                    authViewModel.deleteUser(user.email)
                                })
                            },
                            onUpdateExpiry = {
                                showExpiryDialogForUser = user
                            }
                        )
                    }
                }
            }
        }
    }

    // Confirmation Dialog
    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = "Warning", tint = MaterialTheme.colorScheme.error) },
            title = { Text("Confirm Action", fontWeight = FontWeight.Bold) },
            text = { Text(action.second) },
            confirmButton = {
                Button(
                    onClick = {
                        action.third()
                        pendingAction = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add User Dialog
    if (showAddUserDialog) {
        AlertDialog(
            onDismissRequest = { showAddUserDialog = false },
            title = { Text("Pre-Approve New Account", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = manualEmail,
                        onValueChange = { manualEmail = it },
                        label = { Text("User Email Address") },
                        placeholder = { Text("e.g. employee@gmail.com") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Column {
                        Text("Select Role:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = manualRole == "user", onClick = { manualRole = "user" })
                                Text("User")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = manualRole == "admin", onClick = { manualRole = "admin" })
                                Text("Admin")
                            }
                        }
                    }

                    Column {
                        Text("Initial Status:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = manualStatus == "approved", onClick = { manualStatus = "approved" })
                                Text("Approved")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = manualStatus == "pending", onClick = { manualStatus = "pending" })
                                Text("Pending")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (manualEmail.isNotBlank()) {
                            authViewModel.createUserManually(manualEmail, manualRole, manualStatus)
                            showAddUserDialog = false
                        }
                    },
                    enabled = manualEmail.isNotBlank()
                ) {
                    Text("Create Account")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddUserDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Expiry Plan Duration Dialog
    showExpiryDialogForUser?.let { user ->
        var selectedPreset by remember { mutableStateOf("30") } // "1", "7", "30", "90", "180", "365", "custom", "lifetime"
        var customDaysInput by remember { mutableStateOf("30") }
        
        AlertDialog(
            onDismissRequest = { showExpiryDialogForUser = null },
            title = { Text("Set Plan Access Duration", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Assign or renew access plan for:\n${user.email}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    
                    Text("Select Access Plan Duration:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    
                    val presets = listOf(
                        "1" to "1 Day Access",
                        "7" to "7 Days Access",
                        "30" to "30 Days (1 Month)",
                        "90" to "90 Days (3 Months)",
                        "180" to "180 Days (6 Months)",
                        "365" to "365 Days (1 Year)",
                        "lifetime" to "Lifetime Access (Unlimited)",
                        "custom" to "Custom Days Limit"
                    )
                    
                    presets.forEach { (key, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { selectedPreset = key }
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedPreset == key,
                                onClick = { selectedPreset = key }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    
                    if (selectedPreset == "custom") {
                        OutlinedTextField(
                            value = customDaysInput,
                            onValueChange = { customDaysInput = it.filter { char -> char.isDigit() } },
                            label = { Text("Number of Days") },
                            placeholder = { Text("e.g. 15") },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            singleLine = true
                        )
                    }
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    
                    // Compute and preview calculated expiration date
                    val computedExpiryMs = remember(selectedPreset, customDaysInput) {
                        when (selectedPreset) {
                            "lifetime" -> 0L
                            "custom" -> {
                                val days = customDaysInput.toIntOrNull() ?: 1
                                System.currentTimeMillis() + days * 24 * 60 * 60 * 1000L
                            }
                            else -> {
                                val days = selectedPreset.toIntOrNull() ?: 30
                                System.currentTimeMillis() + days * 24 * 60 * 60 * 1000L
                            }
                        }
                    }
                    
                    val previewText = if (computedExpiryMs == 0L) {
                        "Lifetime (Unlimited access, no expiry date)"
                    } else {
                        val formatter = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
                        formatter.format(java.util.Date(computedExpiryMs))
                    }
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), shape = RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text("New Expiry Date Preview:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(previewText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val expiryMs = when (selectedPreset) {
                            "lifetime" -> 0L
                            "custom" -> {
                                val days = customDaysInput.toIntOrNull() ?: 1
                                System.currentTimeMillis() + days * 24 * 60 * 60 * 1000L
                            }
                            else -> {
                                val days = selectedPreset.toIntOrNull() ?: 30
                                System.currentTimeMillis() + days * 24 * 60 * 60 * 1000L
                            }
                        }
                        authViewModel.updateUserExpiry(user.email, expiryMs)
                        showExpiryDialogForUser = null
                    }
                ) {
                    Text("Apply Access Plan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExpiryDialogForUser = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Update Source Configuration Dialog
    if (showUpdateConfigDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateConfigDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("In-App Update Source", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Specify your GitHub repository details where you publish release APKs. The app checks this repository's latest release version against the installed app version.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = configOwner,
                        onValueChange = { configOwner = it },
                        label = { Text("GitHub Owner / Username") },
                        placeholder = { Text("e.g. subhojitpaul26042004") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = configRepo,
                        onValueChange = { configRepo = it },
                        label = { Text("GitHub Repository Name") },
                        placeholder = { Text("e.g. dasmo-photo-print") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    var testResult by remember { mutableStateOf("") }
                    var testing by remember { mutableStateOf(false) }
                    val scope = rememberCoroutineScope()
                    
                    Button(
                        onClick = {
                            testing = true
                            testResult = "Checking..."
                            UpdateChecker.saveGithubConfig(context, configOwner, configRepo)
                            scope.launch {
                                val info = UpdateChecker.checkForUpdates(context)
                                testing = false
                                testResult = if (info.hasUpdate) {
                                    "Newer version found: ${info.latestVersion} (Current: ${info.currentVersion})"
                                } else {
                                    "App is up to date! Latest: ${info.latestVersion} (Current: ${info.currentVersion})"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.align(Alignment.End),
                        enabled = !testing
                    ) {
                        Text("Test Connection")
                    }
                    if (testResult.isNotEmpty()) {
                        Text(
                            text = testResult,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (testResult.startsWith("Newer")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        UpdateChecker.saveGithubConfig(context, configOwner, configRepo)
                        showUpdateConfigDialog = false
                    }
                ) {
                    Text("Save Configuration")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateConfigDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    containerColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, maxLines = 1)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun EnhancedUserAdminCard(
    user: UserAccount,
    currentUserEmail: String,
    onStatusChange: (String) -> Unit,
    onRoleChange: (String) -> Unit,
    onResetDevice: () -> Unit,
    onDeleteUser: () -> Unit,
    onUpdateExpiry: () -> Unit
) {
    val isSelf = user.email.trim().lowercase() == currentUserEmail.trim().lowercase()
    val isSuperAdmin = user.email.trim().lowercase() == "subhojitpaul26042004@gmail.com"
    val isActionRestricted = isSelf || isSuperAdmin

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when(user.status) {
                "approved" -> MaterialTheme.colorScheme.surface
                "pending" -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                "rejected" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (user.status == "pending") 2.dp else 1.dp,
            color = when(user.status) {
                "pending" -> MaterialTheme.colorScheme.tertiary
                "approved" -> MaterialTheme.colorScheme.outlineVariant
                "rejected" -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Email and Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.email,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                val badgeColor = when(user.status) {
                    "approved" -> androidx.compose.ui.graphics.Color(0xFF2E7D32)
                    "rejected" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.tertiary
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = badgeColor),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = user.status.uppercase(),
                        color = androidx.compose.ui.graphics.Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            // Details: Role and Device ID
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    LabelValue(label = "SYSTEM ROLE", value = user.role.uppercase())
                }
                Column(modifier = Modifier.weight(1.5f)) {
                    val deviceText = if (user.deviceId.isNotEmpty()) {
                        "${user.deviceModel} (${user.deviceId.take(6)})"
                    } else {
                        "No device registered"
                    }
                    LabelValue(label = "DEVICE BOUND", value = deviceText)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Access Plan Expiry row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val expiryText = if (user.expiryTimestamp == 0L) {
                        "Lifetime (Unlimited Access)"
                    } else {
                        val formatter = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
                        val dateStr = formatter.format(java.util.Date(user.expiryTimestamp))
                        val timeLeftMs = user.expiryTimestamp - System.currentTimeMillis()
                        if (timeLeftMs > 0) {
                            val daysLeft = timeLeftMs / (1000 * 60 * 60 * 24)
                            val hoursLeft = (timeLeftMs / (1000 * 60 * 60)) % 24
                            val timeString = if (daysLeft > 0) "$daysLeft days left" else "$hoursLeft hrs left"
                            "$dateStr ($timeString)"
                        } else {
                            "EXPIRED ($dateStr)"
                        }
                    }
                    LabelValue(label = "ACCESS PLAN EXPIRE TIME", value = expiryText)
                }
                
                IconButton(
                    onClick = onUpdateExpiry,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Expiry Duration",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (isActionRestricted) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isSelf) "Logged-in Admin (Protected)" else "Primary Super Admin (Protected)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))

                // Actions: Quick toggle/manage access
                Text(
                    "ADMINISTRATION ACTIONS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Access Approval Toggle
                    if (user.status != "approved") {
                        Button(
                            onClick = { onStatusChange("approved") },
                            modifier = Modifier.weight(1.3f),
                            colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF2E7D32)),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Approve Access", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { onStatusChange("rejected") },
                            modifier = Modifier.weight(1.3f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Revoke Access", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Role Toggler
                    val targetRole = if (user.role == "admin") "user" else "admin"
                    OutlinedButton(
                        onClick = { onRoleChange(targetRole) },
                        modifier = Modifier.weight(1.1f),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (user.role == "admin") "Demote" else "Make Admin",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Unbind Device Action
                if (user.deviceId.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onResetDevice,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Unbind & Reset Device Bind", fontWeight = FontWeight.SemiBold)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDeleteUser,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete User Account", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun LabelValue(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun OfflineBlockingScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp).verticalScroll(rememberScrollState())
        ) {
            Icon(
                imageVector = Icons.Default.WifiOff,
                contentDescription = "Offline",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = "Internet Connection Required",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = "To ensure secure session continuity and prevent unauthorized access, an active internet connection is required. Reconnecting...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            CircularProgressIndicator(
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}
