package com.example

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.model.Project
import com.example.domain.model.ProjectMode
import com.example.ui.AuthViewModel
import com.example.ui.AuthState
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.ProjectViewModel
import com.example.ui.ProjectViewModelFactory
import com.example.ui.screens.*
import com.example.util.UpdateChecker

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: ProjectViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request Push Notification permission for Android 13+ (API 33+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val permissionCheck = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            if (permissionCheck != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        // Core dynamic injection of Room services
        val app = application as PassportPhotoApp
        val factory = ProjectViewModelFactory(app.repository)
        viewModel = ViewModelProvider(this, factory)[ProjectViewModel::class.java]

        setContent {
            MyApplicationTheme {
                val authViewModel: AuthViewModel = viewModel()
                val context = LocalContext.current
                val sharedPrefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                var showAdminDashboard by remember { mutableStateOf(false) }

                var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
                var showUpdateDialog by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    val savedEmail = sharedPrefs.getString("email", null)
                    val savedSession = sharedPrefs.getString("session_token", null)
                    authViewModel.monitorNetwork(context)
                    authViewModel.checkLogin(context, savedEmail, savedSession)

                    // Run the update checker
                    try {
                        val info = UpdateChecker.checkForUpdates(context)
                        if (info.hasUpdate) {
                            updateInfo = info
                            showUpdateDialog = true
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                // Real-time session verification (Cost-efficient Snapshot Listener)
                LaunchedEffect(authViewModel.authState) {
                    if (authViewModel.authState is AuthState.Authenticated) {
                        val state = authViewModel.authState as AuthState.Authenticated
                        val savedSession = sharedPrefs.getString("session_token", null)
                        
                        if (savedSession != null) {
                            authViewModel.startSessionObserver(state.user.email, savedSession) {
                                sharedPrefs.edit().remove("email").remove("session_token").apply()
                                authViewModel.logout()
                            }
                        }
                    } else {
                        authViewModel.stopSessionObserver()
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (val state = authViewModel.authState) {
                        is AuthState.Loading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        is AuthState.Unauthenticated -> {
                            LoginScreen(
                                authViewModel = authViewModel,
                                onLoginSuccess = { email, sessionToken ->
                                    sharedPrefs.edit()
                                        .putString("email", email)
                                        .putString("session_token", sessionToken)
                                        .apply()
                                }
                            )
                        }
                        is AuthState.PendingApproval -> {
                            PendingApprovalScreen(
                                onRetry = {
                                    val savedEmail = sharedPrefs.getString("email", null)
                                    authViewModel.checkLogin(context, savedEmail)
                                }
                            )
                        }
                        is AuthState.Error -> {
                            AuthErrorScreen(
                                message = state.message,
                                onRetry = {
                                    val savedEmail = sharedPrefs.getString("email", null)
                                    authViewModel.checkLogin(context, savedEmail)
                                }
                            )
                        }
                        is AuthState.Authenticated -> {
                            val user = state.user
                            val currentTime = System.currentTimeMillis()
                            val isExpired = user.expiryTimestamp > 0L && currentTime > user.expiryTimestamp
                            viewModel.isExpired = isExpired
                            viewModel.expiryTimeMs = user.expiryTimestamp
                            viewModel.isApproved = (user.status == "approved" || user.role == "admin") && !isExpired
                            
                            // Real-time listener and FCM subscription for admins to catch new users instantly
                            if (user.role == "admin") {
                                LaunchedEffect(Unit) {
                                    authViewModel.startListeningUsers(context)
                                    try {
                                        com.google.firebase.messaging.FirebaseMessaging.getInstance()
                                            .subscribeToTopic("admin_alerts")
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }

                            var dismissedEmails by remember { mutableStateOf(setOf<String>()) }
                            val pendingUsers = remember(authViewModel.allUsers, dismissedEmails) {
                                authViewModel.allUsers.filter { it.status == "pending" && it.email !in dismissedEmails }
                            }

                            Box(modifier = Modifier.fillMaxSize()) {
                                if (authViewModel.isOfflineBlocked) {
                                    OfflineBlockingScreen()
                                } else if (showAdminDashboard && state.user.role == "admin") {
                                    AdminDashboardScreen(
                                        authViewModel = authViewModel,
                                        onBack = { showAdminDashboard = false }
                                    )
                                } else {
                                    MainContent(
                                        viewModel = viewModel, 
                                        isAdmin = state.user.role == "admin",
                                        onAdminClicked = { showAdminDashboard = true },
                                        onLogoutClicked = {
                                            sharedPrefs.edit().remove("email").apply()
                                            authViewModel.logout()
                                        }
                                    )
                                }

                                // In-App Notification Overlay Alert Banner for Admin
                                if (user.role == "admin" && pendingUsers.isNotEmpty() && !showAdminDashboard) {
                                    val topPending = pendingUsers.first()
                                    AnimatedVisibility(
                                        visible = true,
                                        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                                        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .padding(top = 48.dp, start = 16.dp, end = 16.dp)
                                    ) {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            ),
                                            shape = RoundedCornerShape(16.dp),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Notifications,
                                                        contentDescription = "Notification",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "New Access Request",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    IconButton(
                                                        onClick = { dismissedEmails = dismissedEmails + topPending.email },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = "Dismiss",
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                                
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "User with email '${topPending.email}' has registered and is waiting for your access approval.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.End,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    TextButton(
                                                        onClick = { showAdminDashboard = true }
                                                    ) {
                                                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Manage Users", fontWeight = FontWeight.Bold)
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Button(
                                                        onClick = {
                                                            authViewModel.updateUserStatus(topPending.email, "approved")
                                                        },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = MaterialTheme.colorScheme.primary
                                                        ),
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                                    ) {
                                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Approve Now", fontWeight = FontWeight.Black)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (showUpdateDialog && updateInfo != null) {
                    val info = updateInfo!!
                    AlertDialog(
                        onDismissRequest = { showUpdateDialog = false },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = "Update Available",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        },
                        title = {
                            Text(
                                text = "New Update Available!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "A new version of DASMO PHOTO PRINT is available on GitHub.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Current: v${info.currentVersion}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Latest: ${info.latestVersion}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (info.releaseNotes.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Release Notes:",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 120.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        )
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .padding(8.dp)
                                                .verticalScroll(rememberScrollState())
                                        ) {
                                            Text(
                                                text = info.releaseNotes,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    UpdateChecker.openUrl(context, info.downloadUrl)
                                    showUpdateDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("Update Now", fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showUpdateDialog = false }
                            ) {
                                Text("Later")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MainContent(
    viewModel: ProjectViewModel,
    isAdmin: Boolean = false,
    onAdminClicked: () -> Unit = {},
    onLogoutClicked: () -> Unit = {}
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        // Router state switcher
        when (viewModel.currentStep) {
            0 -> {
                HomeScreen(
                    viewModel = viewModel,
                    onNewProjectClicked = {
                        viewModel.startNewProject()
                    },
                    onReprintClicked = { project ->
                        viewModel.loadProjectFromHistory(project, context)
                    },
                    isAdmin = isAdmin,
                    onAdminClicked = onAdminClicked,
                    onLogoutClicked = onLogoutClicked
                )
            }
            1 -> {
                ModeSelectScreen(
                    viewModel = viewModel,
                    onBackClicked = {
                        viewModel.currentStep = 0
                    }
                )
            }
            2 -> {
                SizeInputScreen(
                    viewModel = viewModel,
                    onBackClicked = {
                        viewModel.currentStep = 1
                    },
                    onNextClicked = {
                        val success = viewModel.computeCurrentLayout()
                        if (success) {
                            // If dimensions are valid, proceed to crop stage 1
                            viewModel.currentStep = 3
                        }
                    }
                )
            }
            3 -> {
                PhotoSelectionScreen(
                    viewModel = viewModel,
                    isPhotoA = true,
                    onBackClicked = {
                        viewModel.currentStep = 2
                    }
                )
            }
            4 -> {
                PhotoSelectionScreen(
                    viewModel = viewModel,
                    isPhotoA = false,
                    onBackClicked = {
                        viewModel.currentStep = 3
                    }
                )
            }
            5 -> {
                JointComposeScreen(
                    viewModel = viewModel,
                    onBackClicked = {
                        viewModel.currentStep = 4
                    },
                    onNextClicked = {
                        viewModel.onJointCompositeApproved()
                    }
                )
            }
            6 -> {
                PreviewScreen(
                    viewModel = viewModel,
                    onBackClicked = {
                        if (viewModel.mode == ProjectMode.JOINT) {
                            viewModel.currentStep = 5
                        } else {
                            viewModel.currentStep = 3
                        }
                    },
                    onApproved = {
                        viewModel.currentStep = 7
                    }
                )
            }
            7 -> {
                ExportScreen(
                    viewModel = viewModel,
                    onBackClicked = {
                        viewModel.currentStep = 6
                    },
                    onFinishClicked = {
                        viewModel.currentStep = 0
                    }
                )
            }
        }
    }
}
