package com.example.ui.screens

import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.example.R
import com.example.domain.model.UserAccount
import com.example.ui.AuthViewModel
import com.example.util.UpdateChecker
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val email = account?.email
            if (!email.isNullOrBlank()) {
                if (account.idToken != null) {
                    val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(account.idToken, null)
                    com.google.firebase.auth.FirebaseAuth.getInstance().signInWithCredential(credential)
                }
                authViewModel.login(context, email, null) { token ->
                    onLoginSuccess(email, token)
                }
            } else {
                Toast.makeText(context, "No email found in Google Account", Toast.LENGTH_LONG).show()
            }
        } catch (e: ApiException) {
            e.printStackTrace()
            val msg = when (e.statusCode) {
                10 -> "Developer Error (10): SHA-1 fingerprint mismatch."
                12501 -> "Sign-in cancelled."
                else -> "Sign-In Error (${e.statusCode}): ${e.localizedMessage ?: "Unknown error"}"
            }
            if (e.statusCode != 12501) {
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Sign-In Exception: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Shield Security",
                        modifier = Modifier.size(38.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "DASMO PHOTO PRINT",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Sign in with your Google Account. Your device will be securely registered and bound to your account in Firestore.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Admin approval & single-device hardware lock enforced.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        isSigningIn = true
                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestIdToken(context.getString(R.string.default_web_client_id))
                            .requestEmail()
                            .build()
                        val mGoogleSignInClient = GoogleSignIn.getClient(context, gso)
                        launcher.launch(mGoogleSignInClient.signInIntent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = !isSigningIn
                ) {
                    if (isSigningIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Continue with Google", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PendingApprovalScreen(
    user: UserAccount,
    onSignOut: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(28.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.HourglassEmpty,
                        contentDescription = "Pending Approval",
                        modifier = Modifier.size(38.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }

                Text(
                    text = "Awaiting Admin Approval",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Your account request and device registration have been received in Firestore. Please ask Administrator (subhojitpaul26042004@gmail.com) to approve your access.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                // User details card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Account: ${user.email}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Device Model: ${user.deviceModel.ifEmpty { "Android Device" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Device ID: ${user.deviceId.take(12)}...",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(user.deviceId))
                                    Toast.makeText(context, "Device ID copied!", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text("Copy ID", fontSize = 11.sp)
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Listening for admin approval live...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out")
                }
            }
        }
    }
}

@Composable
fun DeviceMismatchScreen(
    registeredDeviceModel: String,
    user: UserAccount,
    onSignOut: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(28.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhonelinkLock,
                        contentDescription = "Device Locked",
                        modifier = Modifier.size(38.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }

                Text(
                    text = "Device Not Authorized",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "This account (${user.email}) is already bound to another physical device:\n\n📱 Registered Device: $registeredDeviceModel\n\nFor security and license protection, one account cannot be shared across multiple devices. Please use your registered phone or ask the administrator to reset your device binding.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onSignOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out", fontWeight = FontWeight.Bold)
                }
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
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = "Error",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = "Authentication Notice",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(onClick = onRetry) {
                Text("Go Back / Retry")
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
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        authViewModel.startListeningUsers(context)
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") } // "All", "Pending", "Approved", "Admins", "Rejected"
    var showAddUserDialog by remember { mutableStateOf(false) }
    var showExpiryDialogForUser by remember { mutableStateOf<UserAccount?>(null) }

    // Dialog state for manually adding user
    var manualEmail by remember { mutableStateOf("") }
    var manualRole by remember { mutableStateOf("user") }
    var manualStatus by remember { mutableStateOf("approved") }

    // Confirmation dialog state
    var pendingAction by remember { mutableStateOf<Triple<UserAccount, String, () -> Unit>?>(null) }

    val allUsers = authViewModel.allUsers

    // Compute stats
    val totalCount = allUsers.size
    val pendingCount = allUsers.count { it.status == "pending" && !it.isApproved && !it.isAdmin }
    val approvedCount = allUsers.count { it.isApproved && !it.isAdmin }
    val revokedCount = allUsers.count { (it.status == "rejected" || it.status == "declined" || (!it.isApproved && it.status != "pending")) && !it.isAdmin }
    val adminCount = allUsers.count { it.isAdmin }

    // Filtered users list
    val filteredUsers = remember(allUsers, searchQuery, selectedFilter) {
        allUsers.filter { user ->
            val matchesSearch = user.email.contains(searchQuery, ignoreCase = true) ||
                    user.deviceModel.contains(searchQuery, ignoreCase = true) ||
                    user.deviceId.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                "All" -> true
                "Pending" -> user.status == "pending" && !user.isApproved && !user.isAdmin
                "Approved" -> user.isApproved && !user.isAdmin
                "Revoked" -> (user.status == "rejected" || user.status == "declined" || (!user.isApproved && user.status != "pending")) && !user.isAdmin
                "Admins" -> user.isAdmin
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
                        Text("subhojitpaul26042004@gmail.com", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
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
            ExtendedFloatingActionButton(
                onClick = {
                    manualEmail = ""
                    manualRole = "user"
                    manualStatus = "approved"
                    showAddUserDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                text = { Text("Pre-Approve User", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
        ) {
            // Stats Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(title = "Total", value = totalCount.toString(), modifier = Modifier.width(85.dp), containerColor = MaterialTheme.colorScheme.surfaceVariant)
                StatCard(title = "Pending", value = pendingCount.toString(), modifier = Modifier.width(85.dp), containerColor = Color(0xFFFFF3E0))
                StatCard(title = "Approved", value = approvedCount.toString(), modifier = Modifier.width(85.dp), containerColor = Color(0xFFE8F5E9))
                StatCard(title = "Revoked", value = revokedCount.toString(), modifier = Modifier.width(85.dp), containerColor = MaterialTheme.colorScheme.errorContainer)
                StatCard(title = "Admins", value = adminCount.toString(), modifier = Modifier.width(85.dp), containerColor = MaterialTheme.colorScheme.primaryContainer)
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search by user email or phone model") },
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
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf(
                    "All" to "All (${totalCount})",
                    "Pending" to "Pending (${pendingCount})",
                    "Approved" to "Approved (${approvedCount})",
                    "Revoked" to "Revoked (${revokedCount})",
                    "Admins" to "Admins (${adminCount})"
                )
                filters.forEach { (key, label) ->
                    val isSelected = selectedFilter == key
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = key },
                        label = { Text(label) },
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
                            imageVector = Icons.Default.PeopleOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No accounts matching '$searchQuery'" else "No accounts in category '$selectedFilter'",
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
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 88.dp, top = 6.dp)
                ) {
                    items(filteredUsers, key = { it.email }) { user ->
                        EnhancedUserAdminCard(
                            user = user,
                            currentUserEmail = authViewModel.currentUser?.email ?: "",
                            onApprove = {
                                authViewModel.approveUser(user.email)
                            },
                            onDecline = {
                                authViewModel.rejectUser(user.email)
                            },
                            onResetDevice = {
                                pendingAction = Triple(
                                    user,
                                    "Unbind hardware device lock for ${user.email}?\n\nThis allows the user to register and bind a new phone/tablet upon approval."
                                ) {
                                    authViewModel.revokeDevice(user.email)
                                }
                            },
                            onDeleteUser = {
                                pendingAction = Triple(
                                    user,
                                    "Permanently delete user record for ${user.email} from Firestore?\n\nThis cannot be undone."
                                ) {
                                    authViewModel.deleteUser(user.email)
                                }
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
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
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
            title = { Text("Pre-Approve Account", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = manualEmail,
                        onValueChange = { manualEmail = it },
                        label = { Text("User Google Email") },
                        placeholder = { Text("e.g. client@gmail.com") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Column {
                        Text("Role:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = manualRole == "user", onClick = { manualRole = "user" })
                                Text("Standard User")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = manualRole == "admin", onClick = { manualRole = "admin" })
                                Text("Admin")
                            }
                        }
                    }

                    Column {
                        Text("Access Status:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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
                    Text("Create & Save")
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
        var selectedPreset by remember { mutableStateOf("30") }
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
                        text = "Assign access plan duration for:\n${user.email}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    val presets = listOf(
                        "1" to "1 Day Access",
                        "7" to "7 Days Access",
                        "30" to "30 Days (1 Month)",
                        "90" to "90 Days (3 Months)",
                        "180" to "180 Days (6 Months)",
                        "365" to "365 Days (1 Year)",
                        "lifetime" to "Lifetime Access (Unlimited)",
                        "custom" to "Custom Days"
                    )

                    presets.forEach { (key, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { selectedPreset = key }
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = selectedPreset == key, onClick = { selectedPreset = key })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    if (selectedPreset == "custom") {
                        OutlinedTextField(
                            value = customDaysInput,
                            onValueChange = { customDaysInput = it.filter { c -> c.isDigit() } },
                            label = { Text("Number of Days") },
                            placeholder = { Text("e.g. 15") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            singleLine = true
                        )
                    }

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
                        "Lifetime (Unlimited access, no expiry)"
                    } else {
                        val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                        formatter.format(Date(computedExpiryMs))
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), shape = RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text("Calculated Expiry Date:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
                    Text("Apply Plan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExpiryDialogForUser = null }) {
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
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, maxLines = 1)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun EnhancedUserAdminCard(
    user: UserAccount,
    currentUserEmail: String,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
    onResetDevice: () -> Unit,
    onDeleteUser: () -> Unit,
    onUpdateExpiry: () -> Unit
) {
    val isSuperAdmin = user.email.trim().lowercase() == "subhojitpaul26042004@gmail.com"
    val isRevoked = !user.isAdmin && (user.status == "rejected" || user.status == "declined" || (!user.isApproved && user.status != "pending"))
    val isPending = !user.isAdmin && user.status == "pending" && !user.isApproved
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSuperAdmin -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                isPending -> Color(0xFFFFF3E0).copy(alpha = 0.8f)
                isRevoked -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                user.isApproved -> MaterialTheme.colorScheme.surface
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        ),
        border = BorderStroke(
            1.dp,
            when {
                isSuperAdmin -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                isPending -> Color(0xFFE65100).copy(alpha = 0.4f)
                isRevoked -> MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                user.isApproved -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Avatar, Email, Role/Status Badges
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val initial = user.email.take(1).uppercase()
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                user.isAdmin -> MaterialTheme.colorScheme.primary
                                user.isApproved -> Color(0xFF2E7D32)
                                isRevoked -> MaterialTheme.colorScheme.error
                                isPending -> Color(0xFFE65100)
                                else -> MaterialTheme.colorScheme.secondaryContainer
                            }
                        )
                ) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.email,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "📱 ${user.deviceModel.ifEmpty { "Model: Unknown" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "🔑 ID: ${user.deviceId.ifEmpty { "None (Unbound)" }}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Badges
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isSuperAdmin) {
                        Badge(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                            Text("SUPER ADMIN", modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), fontWeight = FontWeight.Bold, fontSize = 9.sp)
                        }
                    } else if (user.isApproved) {
                        Badge(containerColor = Color(0xFF2E7D32), contentColor = Color.White) {
                            Text("APPROVED", modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), fontWeight = FontWeight.Bold, fontSize = 9.sp)
                        }
                    } else if (isRevoked) {
                        Badge(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError) {
                            Text("REVOKED / BLOCKED", modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), fontWeight = FontWeight.Bold, fontSize = 9.sp)
                        }
                    } else {
                        Badge(containerColor = Color(0xFFE65100), contentColor = Color.White) {
                            Text("PENDING APPROVAL", modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), fontWeight = FontWeight.Bold, fontSize = 9.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(10.dp))

            // Info details: Plan Expiry
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val expiryText = if (user.expiryTimestamp == 0L) {
                    "Plan: Lifetime (No Expiry)"
                } else {
                    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    "Plan Ends: ${sdf.format(Date(user.expiryTimestamp))}"
                }
                Text(
                    text = expiryText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!isSuperAdmin) {
                    TextButton(
                        onClick = onUpdateExpiry,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit Plan", fontSize = 11.sp)
                    }
                }
            }

            if (!isSuperAdmin) {
                Spacer(modifier = Modifier.height(10.dp))

                // Action Button Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when {
                        isPending -> {
                            Button(
                                onClick = onApprove,
                                modifier = Modifier.weight(1f).height(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Approve", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = onDecline,
                                modifier = Modifier.weight(1f).height(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Decline", fontSize = 12.sp)
                            }
                            IconButton(
                                onClick = onDeleteUser,
                                modifier = Modifier.size(38.dp),
                                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Permanently")
                            }
                        }
                        isRevoked -> {
                            Button(
                                onClick = onApprove,
                                modifier = Modifier.weight(1f).height(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Re-Approve", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = onDeleteUser,
                                modifier = Modifier.weight(1f).height(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete", fontSize = 12.sp)
                            }
                        }
                        else -> {
                            // Active approved user
                            OutlinedButton(
                                onClick = onDecline,
                                modifier = Modifier.weight(1f).height(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Revoke", fontSize = 12.sp)
                            }

                            // Reset Device Button
                            OutlinedButton(
                                onClick = onResetDevice,
                                modifier = Modifier.height(38.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.PhonelinkErase, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset Device", fontSize = 11.sp)
                            }

                            // Delete User Button
                            IconButton(
                                onClick = onDeleteUser,
                                modifier = Modifier.size(38.dp),
                                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}


