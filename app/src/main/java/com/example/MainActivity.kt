package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.model.ProjectMode
import com.example.ui.AuthState
import com.example.ui.AuthViewModel
import com.example.ui.ProjectViewModel
import com.example.ui.ProjectViewModelFactory
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
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
                val sharedPrefs = remember { context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE) }
                var activeArea by remember { mutableStateOf(AuthenticatedAreaRoute.STUDIO_WIZARD) }
                var updatePromptState by remember { mutableStateOf<AppUpdatePromptState>(AppUpdatePromptState.Hidden) }

                val performFullSignOut: () -> Unit = {
                    sharedPrefs.edit().clear().apply()
                    activeArea = AuthenticatedAreaRoute.STUDIO_WIZARD
                    viewModel.navigateToStep(com.example.domain.model.WizardStep.HOME)
                    authViewModel.logout(context)
                }

                LaunchedEffect(Unit) {
                    val savedEmail = sharedPrefs.getString("email", null)
                    val savedSession = sharedPrefs.getString("session_token", null)
                    authViewModel.monitorNetwork(context)
                    authViewModel.checkLogin(context, savedEmail, savedSession)

                    // Run the update checker
                    try {
                        val info = UpdateChecker.checkForUpdates(context)
                        if (info.hasUpdate) {
                            updatePromptState = AppUpdatePromptState.Visible(info)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Real-time session verification (Cost-efficient Snapshot Listener)
                LaunchedEffect(authViewModel.authState) {
                    if (authViewModel.authState is AuthState.Authenticated) {
                        val state = authViewModel.authState as AuthState.Authenticated
                        val activeToken = state.user.currentSessionToken.ifEmpty {
                            sharedPrefs.getString("session_token", null) ?: ""
                        }
                        if (activeToken.isNotEmpty()) {
                            sharedPrefs.edit()
                                .putString("email", state.user.email)
                                .putString("session_token", activeToken)
                                .apply()
                            authViewModel.startSessionObserver(state.user.email, activeToken) {
                                performFullSignOut()
                            }
                        }
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
                                        text = "Verifying Security & License...",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
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
                                onCheckStatusNow = {
                                    authViewModel.login(context, currentAuthState.user.email) { token ->
                                        sharedPrefs.edit()
                                            .putString("email", currentAuthState.user.email)
                                            .putString("session_token", token)
                                            .apply()
                                    }
                                },
                                onSignOut = performFullSignOut
                            )
                        }

                        is AuthState.AccessRevoked -> {
                            AccessRevokedScreen(
                                user = currentAuthState.user,
                                onCheckStatusNow = {
                                    authViewModel.login(context, currentAuthState.user.email) { token ->
                                        sharedPrefs.edit()
                                            .putString("email", currentAuthState.user.email)
                                            .putString("session_token", token)
                                            .apply()
                                    }
                                },
                                onSignOut = performFullSignOut
                            )
                        }

                        is AuthState.PlanExpired -> {
                            PlanExpiredScreen(
                                user = currentAuthState.user,
                                onCheckStatusNow = {
                                    authViewModel.login(context, currentAuthState.user.email) { token ->
                                        sharedPrefs.edit()
                                            .putString("email", currentAuthState.user.email)
                                            .putString("session_token", token)
                                            .apply()
                                    }
                                },
                                onSignOut = performFullSignOut
                            )
                        }

                        is AuthState.DeviceMismatch -> {
                            DeviceMismatchScreen(
                                registeredDeviceModel = currentAuthState.registeredDeviceModel,
                                currentDeviceId = currentAuthState.currentDeviceId,
                                user = currentAuthState.user,
                                onCheckStatusNow = {
                                    authViewModel.login(context, currentAuthState.user.email) { token ->
                                        sharedPrefs.edit()
                                            .putString("email", currentAuthState.user.email)
                                            .putString("session_token", token)
                                            .apply()
                                    }
                                },
                                onSignOut = performFullSignOut
                            )
                        }

                        is AuthState.Error -> {
                            AuthErrorScreen(
                                message = currentAuthState.message,
                                onRetry = performFullSignOut
                            )
                        }

                        is AuthState.Authenticated -> {
                            val user = currentAuthState.user
                            val lifecycleStatus = user.resolveLifecycleStatus(
                                nowMs = System.currentTimeMillis(),
                                superAdminEmail = AuthViewModel.SUPER_ADMIN_EMAIL
                            )

                            // Double runtime security gate using explicit AccountLifecycleStatus state machine
                            when (lifecycleStatus) {
                                com.example.domain.model.AccountLifecycleStatus.REVOKED -> {
                                    AccessRevokedScreen(
                                        user = user,
                                        onCheckStatusNow = { authViewModel.login(context, user.email) },
                                        onSignOut = performFullSignOut
                                    )
                                }

                                com.example.domain.model.AccountLifecycleStatus.PENDING -> {
                                    PendingApprovalScreen(
                                        user = user,
                                        onCheckStatusNow = { authViewModel.login(context, user.email) },
                                        onSignOut = performFullSignOut
                                    )
                                }

                                com.example.domain.model.AccountLifecycleStatus.EXPIRED -> {
                                    PlanExpiredScreen(
                                        user = user,
                                        onCheckStatusNow = { authViewModel.login(context, user.email) },
                                        onSignOut = performFullSignOut
                                    )
                                }

                                com.example.domain.model.AccountLifecycleStatus.SUPER_ADMIN,
                                com.example.domain.model.AccountLifecycleStatus.ADMIN,
                                com.example.domain.model.AccountLifecycleStatus.ACTIVE -> {
                                    viewModel.isApproved = true
                                    viewModel.isExpired = false

                                    when (activeArea) {
                                        AuthenticatedAreaRoute.ADMIN_DASHBOARD -> {
                                            if (user.isAdmin) {
                                                androidx.activity.compose.BackHandler {
                                                    activeArea = AuthenticatedAreaRoute.STUDIO_WIZARD
                                                }
                                                AdminDashboardScreen(
                                                    authViewModel = authViewModel,
                                                    onBack = { activeArea = AuthenticatedAreaRoute.STUDIO_WIZARD }
                                                )
                                            } else {
                                                activeArea = AuthenticatedAreaRoute.STUDIO_WIZARD
                                            }
                                        }
                                        AuthenticatedAreaRoute.STUDIO_WIZARD -> {
                                            Box(modifier = Modifier.fillMaxSize()) {
                                                MainContent(
                                                    viewModel = viewModel,
                                                    isAdmin = user.isAdmin,
                                                    onAdminClicked = { activeArea = AuthenticatedAreaRoute.ADMIN_DASHBOARD },
                                                    onLogoutClicked = performFullSignOut
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                when (val prompt = updatePromptState) {
                    is AppUpdatePromptState.Hidden -> Unit
                    is AppUpdatePromptState.Visible -> {
                        InAppUpdateDialog(
                            updateInfo = prompt.info,
                            onDismiss = { updatePromptState = AppUpdatePromptState.Hidden }
                        )
                    }
                }
            }
        }
    }
}

private enum class AuthenticatedAreaRoute {
    STUDIO_WIZARD,
    ADMIN_DASHBOARD
}

private sealed class AppUpdatePromptState {
    object Hidden : AppUpdatePromptState()
    data class Visible(val info: UpdateChecker.UpdateInfo) : AppUpdatePromptState()
}

@Composable
fun MainContent(
    viewModel: ProjectViewModel,
    isAdmin: Boolean = false,
    onAdminClicked: () -> Unit = {},
    onLogoutClicked: () -> Unit = {}
) {
    val context = LocalContext.current

    // Handle system back gesture across all wizard steps via explicit state transition
    androidx.activity.compose.BackHandler(enabled = viewModel.wizardStep != com.example.domain.model.WizardStep.HOME) {
        viewModel.navigateBack()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        // Exhaustive WizardStep router state machine
        when (viewModel.wizardStep) {
            com.example.domain.model.WizardStep.HOME -> {
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
            com.example.domain.model.WizardStep.MODE_SELECT -> {
                ModeSelectScreen(
                    viewModel = viewModel,
                    onBackClicked = {
                        viewModel.navigateBack()
                    }
                )
            }
            com.example.domain.model.WizardStep.SIZE_INPUT -> {
                SizeInputScreen(
                    viewModel = viewModel,
                    onBackClicked = {
                        viewModel.navigateBack()
                    },
                    onNextClicked = {
                        if (viewModel.computeCurrentLayout()) {
                            viewModel.navigateToStep(com.example.domain.model.WizardStep.PHOTO_A)
                        }
                    }
                )
            }
            com.example.domain.model.WizardStep.PHOTO_A -> {
                PhotoSelectionScreen(
                    viewModel = viewModel,
                    isPhotoA = true,
                    onBackClicked = {
                        viewModel.navigateBack()
                    }
                )
            }
            com.example.domain.model.WizardStep.PHOTO_B -> {
                PhotoSelectionScreen(
                    viewModel = viewModel,
                    isPhotoA = false,
                    onBackClicked = {
                        viewModel.navigateBack()
                    }
                )
            }
            com.example.domain.model.WizardStep.JOINT_COMPOSE -> {
                JointComposeScreen(
                    viewModel = viewModel,
                    onBackClicked = {
                        viewModel.navigateBack()
                    },
                    onNextClicked = {
                        viewModel.onJointCompositeApproved()
                    }
                )
            }
            com.example.domain.model.WizardStep.PREVIEW -> {
                PreviewScreen(
                    viewModel = viewModel,
                    onBackClicked = {
                        viewModel.navigateBack()
                    },
                    onApproved = {
                        viewModel.navigateToStep(com.example.domain.model.WizardStep.EXPORT)
                    }
                )
            }
            com.example.domain.model.WizardStep.EXPORT -> {
                ExportScreen(
                    viewModel = viewModel,
                    onBackClicked = {
                        viewModel.navigateBack()
                    },
                    onFinishClicked = {
                        viewModel.navigateToStep(com.example.domain.model.WizardStep.HOME)
                    }
                )
            }
        }
    }
}
