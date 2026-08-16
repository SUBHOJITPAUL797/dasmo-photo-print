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
                    when (val currentAuthState = authViewModel.authState) {
                        is AuthState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = "Connecting to Secure Services...",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                        is AuthState.Unauthenticated -> {
                            LoginScreen(
                                authViewModel = authViewModel,
                                onLoginSuccess = { email, token ->
                                    sharedPrefs.edit()
                                        .putString("email", email)
                                        .putString("session_token", token)
                                        .apply()
                                }
                            )
                        }
                        is AuthState.PendingApproval -> {
                            PendingApprovalScreen(
                                user = currentAuthState.user,
                                onSignOut = {
                                    sharedPrefs.edit().clear().apply()
                                    authViewModel.logout()
                                }
                            )
                        }
                        is AuthState.DeviceMismatch -> {
                            DeviceMismatchScreen(
                                registeredDeviceModel = currentAuthState.registeredDeviceModel,
                                user = currentAuthState.user,
                                onSignOut = {
                                    sharedPrefs.edit().clear().apply()
                                    authViewModel.logout()
                                }
                            )
                        }
                        is AuthState.Error -> {
                            AuthErrorScreen(
                                message = currentAuthState.message,
                                onRetry = {
                                    sharedPrefs.edit().clear().apply()
                                    authViewModel.logout()
                                }
                            )
                        }
                        is AuthState.Authenticated -> {
                            val user = currentAuthState.user
                            viewModel.isApproved = user.isApproved
                            viewModel.isExpired = user.expiryTimestamp > 0L && System.currentTimeMillis() > user.expiryTimestamp

                            if (showAdminDashboard && user.isAdmin) {
                                AdminDashboardScreen(
                                    authViewModel = authViewModel,
                                    onBack = { showAdminDashboard = false }
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    MainContent(
                                        viewModel = viewModel,
                                        isAdmin = user.isAdmin,
                                        onAdminClicked = { showAdminDashboard = true },
                                        onLogoutClicked = {
                                            sharedPrefs.edit().clear().apply()
                                            authViewModel.logout()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (showUpdateDialog && updateInfo != null) {
                    com.example.ui.screens.InAppUpdateDialog(
                        updateInfo = updateInfo!!,
                        onDismiss = { showUpdateDialog = false }
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
                        } else if (viewModel.mode == ProjectMode.MULTI_PERSON || viewModel.mode == ProjectMode.ID_CARD) {
                            viewModel.currentStep = 4
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
