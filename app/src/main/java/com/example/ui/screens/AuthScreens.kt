package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.UserAccount
import com.example.ui.AuthViewModel
import com.example.ui.components.ClayArtworkBadge
import com.example.ui.components.ClayCard
import com.example.ui.components.ClayIconOrb
import com.example.ui.components.ClayPillBadge
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onLoginSuccess: (String, String) -> Unit
) {
    val context = LocalContext.current
    var isSigningIn by remember { mutableStateOf(false) }
    val deviceModel = remember { authViewModel.getDeviceModel() }
    val deviceId = remember { authViewModel.getDeviceId(context) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val email = account?.email
            if (!email.isNullOrBlank()) {
                val idToken = account.idToken
                if (idToken != null) {
                    val credential = GoogleAuthProvider.getCredential(idToken, null)
                    FirebaseAuth.getInstance().signInWithCredential(credential)
                        .addOnCompleteListener {
                            isSigningIn = false
                            authViewModel.login(context, email, null) { token ->
                                onLoginSuccess(email, token)
                            }
                        }
                } else {
                    isSigningIn = false
                    authViewModel.login(context, email, null) { token ->
                        onLoginSuccess(email, token)
                    }
                }
            } else {
                isSigningIn = false
                Toast.makeText(context, "No email found in selected Google Account", Toast.LENGTH_LONG).show()
            }
        } catch (e: ApiException) {
            isSigningIn = false
            e.printStackTrace()
            val msg = when (e.statusCode) {
                10 -> "Configuration Notice (10): SHA-1 certificate mismatch."
                12501 -> "Sign-in cancelled."
                else -> "Sign-In Error (${e.statusCode}): ${e.localizedMessage ?: "Unknown error"}"
            }
            if (e.statusCode != 12501) {
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            isSigningIn = false
            e.printStackTrace()
            Toast.makeText(context, "Sign-In Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        ClayCard(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth(),
            accentColor = MaterialTheme.colorScheme.primary,
            cornerRadius = 28.dp,
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(28.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 3D Clay Security Shield Badge
                ClayArtworkBadge(
                    drawableRes = R.drawable.img_clay_security_shield,
                    accentColor = MaterialTheme.colorScheme.primary,
                    size = 86.dp,
                    cornerRadius = 26.dp
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "DASMO PHOTO PRINT",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "SECURED CYBER CAFÉ & STUDIO SUITE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = "Sign in with your authorized Google Account. Access to the studio is strictly protected by Administrator Approval and Single-Device Hardware Binding.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                // Hardware Telemetry Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PhonelinkLock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Hardware Security Verification",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Device:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = deviceModel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Hardware ID:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = deviceId.take(14).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        isSigningIn = true
                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestIdToken(context.getString(R.string.default_web_client_id))
                            .requestEmail()
                            .build()
                        val client = GoogleSignIn.getClient(context, gso)
                        // Sign out cached selector first so user can always choose their Google account cleanly
                        client.signOut().addOnCompleteListener {
                            launcher.launch(client.signInIntent)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("google_sign_in_btn"),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !isSigningIn
                ) {
                    if (isSigningIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Verifying Account...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Sign In with Google",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "Unapproved accounts are held at the security gate until approved by the Administrator.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun PendingApprovalScreen(
    user: UserAccount,
    onCheckStatusNow: () -> Unit = {},
    onSignOut: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    SecurityGateTemplate(
        badgeIcon = Icons.Default.HourglassTop,
        badgeContainerColor = Color(0xFFEF6C00).copy(alpha = 0.15f),
        badgeIconTint = Color(0xFFEF6C00),
        statusPillText = "ACCESS LOCKED • PENDING ADMIN APPROVAL",
        statusPillColor = Color(0xFFEF6C00),
        title = "Awaiting Administrator Approval",
        description = "Your account and physical device have been registered in the security database. No one can enter DASMO PHOTO PRINT until the Administrator approves your account.",
        user = user,
        showLiveListeningIndicator = true,
        liveIndicatorText = "Listening live — screen will unlock automatically upon approval",
        onCopyDeviceId = {
            clipboardManager.setText(AnnotatedString(user.deviceId))
            Toast.makeText(context, "Device ID copied to clipboard!", Toast.LENGTH_SHORT).show()
        },
        onShareRequestToAdmin = {
            val msg = buildString {
                append("*DASMO PHOTO PRINT — Access Approval Request*\n\n")
                append("• *Email:* ${user.email}\n")
                append("• *Device Model:* ${user.deviceModel.ifEmpty { "Android Device" }}\n")
                append("• *Device ID:* ${user.deviceId}\n\n")
                append("Please approve my account in the Admin Control Panel.")
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, msg)
            }
            context.startActivity(Intent.createChooser(intent, "Send Approval Request to Admin"))
        },
        onCheckStatusNow = onCheckStatusNow,
        onSignOut = onSignOut
    )
}

@Composable
fun AccessRevokedScreen(
    user: UserAccount,
    onCheckStatusNow: () -> Unit = {},
    onSignOut: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    SecurityGateTemplate(
        badgeIcon = Icons.Default.Block,
        badgeContainerColor = MaterialTheme.colorScheme.errorContainer,
        badgeIconTint = MaterialTheme.colorScheme.error,
        statusPillText = "ACCESS REVOKED BY ADMINISTRATOR",
        statusPillColor = MaterialTheme.colorScheme.error,
        title = "Account Access Restricted",
        description = "Your access to DASMO PHOTO PRINT has been revoked or declined by the Administrator (${AuthViewModel.SUPER_ADMIN_EMAIL}). Please contact the administrator to restore your license.",
        user = user,
        showLiveListeningIndicator = true,
        liveIndicatorText = "Standing by for administrator re-authorization...",
        onCopyDeviceId = {
            clipboardManager.setText(AnnotatedString(user.deviceId))
            Toast.makeText(context, "Device ID copied!", Toast.LENGTH_SHORT).show()
        },
        onShareRequestToAdmin = {
            val msg = "*DASMO PHOTO PRINT — Access Reactivation Request*\n\n• *Email:* ${user.email}\n• *Device:* ${user.deviceModel}\n• *Device ID:* ${user.deviceId}\n\nPlease review and reactivate my access."
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, msg)
            }
            context.startActivity(Intent.createChooser(intent, "Contact Admin"))
        },
        onCheckStatusNow = onCheckStatusNow,
        onSignOut = onSignOut
    )
}

@Composable
fun PlanExpiredScreen(
    user: UserAccount,
    onCheckStatusNow: () -> Unit = {},
    onSignOut: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val formattedExpiry = remember(user.expiryTimestamp) {
        if (user.expiryTimestamp > 0L) {
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(user.expiryTimestamp))
        } else "Expired"
    }

    SecurityGateTemplate(
        badgeIcon = Icons.Default.EventBusy,
        badgeContainerColor = MaterialTheme.colorScheme.errorContainer,
        badgeIconTint = MaterialTheme.colorScheme.error,
        statusPillText = "SUBSCRIPTION PLAN EXPIRED",
        statusPillColor = MaterialTheme.colorScheme.error,
        title = "Access Plan Expired",
        description = "Your subscription plan ended on $formattedExpiry. Please contact the Administrator (${AuthViewModel.SUPER_ADMIN_EMAIL}) to renew your plan duration.",
        user = user,
        showLiveListeningIndicator = true,
        liveIndicatorText = "Listening live — app will unlock as soon as your plan is renewed",
        onCopyDeviceId = {
            clipboardManager.setText(AnnotatedString(user.deviceId))
            Toast.makeText(context, "Device ID copied!", Toast.LENGTH_SHORT).show()
        },
        onShareRequestToAdmin = {
            val msg = "*DASMO PHOTO PRINT — Plan Renewal Request*\n\n• *Email:* ${user.email}\n• *Expired On:* $formattedExpiry\n• *Device:* ${user.deviceModel}\n\nPlease renew my subscription plan."
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, msg)
            }
            context.startActivity(Intent.createChooser(intent, "Request Plan Renewal"))
        },
        onCheckStatusNow = onCheckStatusNow,
        onSignOut = onSignOut
    )
}

@Composable
private fun SecurityGateTemplate(
    badgeIcon: ImageVector,
    badgeContainerColor: Color,
    badgeIconTint: Color,
    statusPillText: String,
    statusPillColor: Color,
    title: String,
    description: String,
    user: UserAccount,
    showLiveListeningIndicator: Boolean,
    liveIndicatorText: String,
    onCopyDeviceId: () -> Unit,
    onShareRequestToAdmin: () -> Unit,
    onCheckStatusNow: () -> Unit,
    onSignOut: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        statusPillColor.copy(alpha = 0.10f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        ClayCard(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth(),
            accentColor = statusPillColor,
            cornerRadius = 28.dp,
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ClayIconOrb(
                    icon = badgeIcon,
                    accentColor = badgeIconTint,
                    size = 72.dp,
                    iconSize = 36.dp,
                    cornerRadius = 24.dp
                )

                Surface(
                    color = statusPillColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, statusPillColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = statusPillText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = statusPillColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                // Registered Account & Hardware Details Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Google Account",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = user.email,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Device Model",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = user.deviceModel.ifEmpty { "Android Device" },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Hardware Device ID",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = user.deviceId.ifEmpty { "Unbound" },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            FilledTonalButton(
                                onClick = onCopyDeviceId,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy ID", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (showLiveListeningIndicator) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = liveIndicatorText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Primary Actions
                Button(
                    onClick = onShareRequestToAdmin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E7D32),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send Approval Details to Admin", fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = onCheckStatusNow,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Check Now", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sign Out", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceMismatchScreen(
    registeredDeviceModel: String,
    currentDeviceId: String = "",
    user: UserAccount,
    onCheckStatusNow: () -> Unit = {},
    onSignOut: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
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
                        contentDescription = "Hardware Lock",
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "SINGLE-DEVICE HARDWARE LOCK ACTIVE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = "Device Not Authorized",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "This account (${user.email}) is already locked to another physical device. Sharing one license across multiple phones is blocked.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Registered Device:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(registeredDeviceModel, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Registered ID:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                user.deviceId.take(14),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (currentDeviceId.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("This Phone's ID:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        currentDeviceId,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(currentDeviceId))
                                        Toast.makeText(context, "Current Device ID copied!", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text("Copy", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "If you changed your phone, ask the Administrator to tap 'Reset Device' on your account in the Admin Control Panel.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = onCheckStatusNow,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Check Again", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onSignOut,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sign Out", fontWeight = FontWeight.Bold)
                    }
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
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 440.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .padding(28.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Security Notice",
                        modifier = Modifier.size(34.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    text = "Security Verification Notice",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Return to Sign In", fontWeight = FontWeight.Bold)
                }
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

    // Show toast whenever an admin operation completes
    val statusMsg = authViewModel.adminStatusMessage
    LaunchedEffect(statusMsg) {
        if (!statusMsg.isNullOrBlank()) {
            Toast.makeText(context, statusMsg, Toast.LENGTH_SHORT).show()
            authViewModel.clearAdminStatusMessage()
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") } // "All", "Pending", "Active", "Expired", "Revoked", "Admins"
    var showAddUserDialog by remember { mutableStateOf(false) }
    var showExpiryDialogForUser by remember { mutableStateOf<UserAccount?>(null) }

    // Dialog state for manually pre-approving / adding user
    var manualEmail by remember { mutableStateOf("") }
    var manualRole by remember { mutableStateOf("user") }
    var manualStatus by remember { mutableStateOf("approved") }
    var manualPlanDays by remember { mutableStateOf("30") }

    // Confirmation dialog state
    var pendingAction by remember { mutableStateOf<Triple<String, String, () -> Unit>?>(null) }

    val allUsers = authViewModel.allUsers
    val now = System.currentTimeMillis()

    // Compute accurate real-time metrics
    val totalCount = allUsers.size
    val pendingCount = allUsers.count { !it.isAdmin && !it.isApproved && it.status == "pending" }
    val expiredCount = allUsers.count { !it.isAdmin && it.isApproved && it.expiryTimestamp in 1..<now }
    val activeCount = allUsers.count { !it.isAdmin && it.isApproved && (it.expiryTimestamp == 0L || it.expiryTimestamp >= now) }
    val revokedCount = allUsers.count { !it.isAdmin && (it.status == "rejected" || (!it.isApproved && it.status != "pending")) }
    val adminCount = allUsers.count { it.isAdmin }

    // Filtered users list
    val filteredUsers = remember(allUsers, searchQuery, selectedFilter, now) {
        allUsers.filter { user ->
            val matchesSearch = searchQuery.isBlank() ||
                    user.email.contains(searchQuery, ignoreCase = true) ||
                    user.deviceModel.contains(searchQuery, ignoreCase = true) ||
                    user.deviceId.contains(searchQuery, ignoreCase = true)

            val isUserExpired = !user.isAdmin && user.isApproved && user.expiryTimestamp in 1..<now
            val isUserActive = !user.isAdmin && user.isApproved && !isUserExpired
            val isUserPending = !user.isAdmin && !user.isApproved && user.status == "pending"
            val isUserRevoked = !user.isAdmin && (user.status == "rejected" || (!user.isApproved && user.status != "pending"))

            val matchesFilter = when (selectedFilter) {
                "All" -> true
                "Pending" -> isUserPending
                "Active" -> isUserActive
                "Expired" -> isUserExpired
                "Revoked" -> isUserRevoked
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ClayArtworkBadge(
                            drawableRes = R.drawable.img_clay_security_shield,
                            accentColor = MaterialTheme.colorScheme.primary,
                            size = 38.dp,
                            cornerRadius = 12.dp
                        )
                        Column {
                            Text(
                                text = "Admin Security & License Center",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "${AuthViewModel.SUPER_ADMIN_EMAIL} • Live Sync",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("admin_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { authViewModel.fetchAllUsers() },
                        modifier = Modifier.testTag("admin_refresh_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh User Directory")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    manualEmail = ""
                    manualRole = "user"
                    manualStatus = "approved"
                    manualPlanDays = "30"
                    showAddUserDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                text = { Text("Add / Pre-Approve", fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("admin_add_user_fab")
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 1. Interactive KPI Summary Row (Tap any card to filter!)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AdminKpiCard(
                    title = "Total",
                    value = totalCount.toString(),
                    accentColor = MaterialTheme.colorScheme.primary,
                    icon = Icons.Default.Group,
                    isSelected = selectedFilter == "All",
                    onClick = { selectedFilter = "All" }
                )
                AdminKpiCard(
                    title = "Pending",
                    value = pendingCount.toString(),
                    accentColor = Color(0xFFEF6C00),
                    icon = Icons.Default.HourglassTop,
                    isSelected = selectedFilter == "Pending",
                    onClick = { selectedFilter = "Pending" }
                )
                AdminKpiCard(
                    title = "Active",
                    value = activeCount.toString(),
                    accentColor = Color(0xFF2E7D32),
                    icon = Icons.Default.CheckCircle,
                    isSelected = selectedFilter == "Active",
                    onClick = { selectedFilter = "Active" }
                )
                AdminKpiCard(
                    title = "Expired",
                    value = expiredCount.toString(),
                    accentColor = Color(0xFFD84315),
                    icon = Icons.Default.EventBusy,
                    isSelected = selectedFilter == "Expired",
                    onClick = { selectedFilter = "Expired" }
                )
                AdminKpiCard(
                    title = "Revoked",
                    value = revokedCount.toString(),
                    accentColor = MaterialTheme.colorScheme.error,
                    icon = Icons.Default.Block,
                    isSelected = selectedFilter == "Revoked",
                    onClick = { selectedFilter = "Revoked" }
                )
                AdminKpiCard(
                    title = "Admins",
                    value = adminCount.toString(),
                    accentColor = Color(0xFF1565C0),
                    icon = Icons.Default.AdminPanelSettings,
                    isSelected = selectedFilter == "Admins",
                    onClick = { selectedFilter = "Admins" }
                )
            }

            // 2. Priority Pending Requests Banner (when users are waiting for approval)
            AnimatedVisibility(visible = pendingCount > 0) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFEF6C00).copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color(0xFFEF6C00).copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = Color(0xFFEF6C00),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "$pendingCount Account(s) Awaiting Approval",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFEF6C00)
                                )
                                Text(
                                    text = "Blocked at security gate until you approve",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (selectedFilter != "Pending") {
                                TextButton(onClick = { selectedFilter = "Pending" }) {
                                    Text("View", fontWeight = FontWeight.Bold, color = Color(0xFFEF6C00))
                                }
                            }
                            Button(
                                onClick = {
                                    pendingAction = Triple(
                                        "Approve All Pending Accounts",
                                        "Grant access to all $pendingCount pending account(s)?"
                                    ) {
                                        authViewModel.approveAllPendingUsers()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2E7D32),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Approve All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 3. Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search email, phone model, or hardware ID...") },
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
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            // 4. Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf(
                    "All" to "All ($totalCount)",
                    "Pending" to "Pending ($pendingCount)",
                    "Active" to "Active ($activeCount)",
                    "Expired" to "Expired ($expiredCount)",
                    "Revoked" to "Revoked ($revokedCount)",
                    "Admins" to "Admins ($adminCount)"
                )
                filters.forEach { (key, label) ->
                    val isSelected = selectedFilter == key
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = key },
                        label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            // 5. User Directory List
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
                            imageVector = Icons.Default.PersonSearch,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No accounts matching \"$searchQuery\"" else "No accounts in \"$selectedFilter\"",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
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
                    contentPadding = PaddingValues(bottom = 96.dp, top = 8.dp)
                ) {
                    items(filteredUsers, key = { it.email }) { user ->
                        EnhancedUserAdminCard(
                            user = user,
                            onApprove30Days = {
                                authViewModel.approveUser(user.email, durationDays = 30)
                            },
                            onApproveLifetime = {
                                authViewModel.approveUser(user.email, durationDays = 0)
                            },
                            onDeclineOrRevoke = {
                                pendingAction = Triple(
                                    "Revoke Account Access",
                                    "Immediately lock and revoke access for ${user.email}? If they are currently using the app, their session will terminate immediately."
                                ) {
                                    authViewModel.rejectUser(user.email)
                                }
                            },
                            onResetDevice = {
                                pendingAction = Triple(
                                    "Reset Hardware Device Binding",
                                    "Clear the locked hardware ID (${user.deviceModel.ifEmpty { "Device" }}) for ${user.email}?\n\nTheir next sign-in will bind their new phone automatically."
                                ) {
                                    authViewModel.revokeDevice(user.email, keepApproved = user.isApproved)
                                }
                            },
                            onToggleAdminRole = {
                                val nextRole = if (user.isAdmin) "user" else "admin"
                                pendingAction = Triple(
                                    "Change Account Role",
                                    "Change ${user.email} role to ${nextRole.uppercase()}?"
                                ) {
                                    authViewModel.updateUserRole(user.email, nextRole)
                                }
                            },
                            onDeleteUser = {
                                pendingAction = Triple(
                                    "Delete Account Record",
                                    "Permanently delete ${user.email} from Firestore? They will be signed out immediately."
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
    pendingAction?.let { (title, message, action) ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            icon = { Icon(Icons.Default.SecurityUpdateWarning, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = { Text(message) },
            confirmButton = {
                Button(
                    onClick = {
                        action()
                        pendingAction = null
                    }
                ) {
                    Text("Confirm", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add / Pre-Approve User Dialog
    if (showAddUserDialog) {
        AlertDialog(
            onDismissRequest = { showAddUserDialog = false },
            icon = { Icon(Icons.Default.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Add / Pre-Approve Account", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = manualEmail,
                        onValueChange = { manualEmail = it },
                        label = { Text("Google Account Email") },
                        placeholder = { Text("operator@gmail.com") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Account Role", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = manualRole == "user",
                                onClick = { manualRole = "user" },
                                label = { Text("Standard User") }
                            )
                            FilterChip(
                                selected = manualRole == "admin",
                                onClick = { manualRole = "admin" },
                                label = { Text("Admin") }
                            )
                        }
                    }

                    if (manualRole != "admin") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Initial Approval Status", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = manualStatus == "approved",
                                    onClick = { manualStatus = "approved" },
                                    label = { Text("Approved Immediately") }
                                )
                                FilterChip(
                                    selected = manualStatus == "pending",
                                    onClick = { manualStatus = "pending" },
                                    label = { Text("Pending Gate") }
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Subscription Plan Duration", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val planOptions = listOf(
                                    "7" to "7 Days",
                                    "30" to "30 Days",
                                    "90" to "90 Days",
                                    "365" to "1 Year",
                                    "0" to "Lifetime"
                                )
                                planOptions.forEach { (days, label) ->
                                    FilterChip(
                                        selected = manualPlanDays == days,
                                        onClick = { manualPlanDays = days },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (manualEmail.isNotBlank()) {
                            val days = manualPlanDays.toLongOrNull() ?: 0L
                            val expiry = if (days <= 0L || manualRole == "admin") {
                                0L
                            } else {
                                System.currentTimeMillis() + days * 24L * 60L * 60L * 1000L
                            }
                            authViewModel.createUserManually(
                                email = manualEmail,
                                role = manualRole,
                                status = manualStatus,
                                expiryTimestamp = expiry
                            )
                            showAddUserDialog = false
                        }
                    },
                    enabled = manualEmail.contains("@")
                ) {
                    Text("Save Account", fontWeight = FontWeight.Bold)
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
            icon = { Icon(Icons.Default.EventAvailable, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Manage Access Plan Duration", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = user.email,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    val presets = listOf(
                        "1" to "1 Day Trial Pass",
                        "7" to "7 Days Weekly Plan",
                        "30" to "30 Days Monthly Plan",
                        "90" to "90 Days Quarterly Plan",
                        "180" to "180 Days Half-Yearly Plan",
                        "365" to "365 Days Annual Plan",
                        "lifetime" to "Lifetime Access (No Expiry)",
                        "custom" to "Custom Number of Days"
                    )

                    presets.forEach { (key, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedPreset = key }
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            RadioButton(selected = selectedPreset == key, onClick = { selectedPreset = key })
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    if (selectedPreset == "custom") {
                        OutlinedTextField(
                            value = customDaysInput,
                            onValueChange = { customDaysInput = it.filter { c -> c.isDigit() } },
                            label = { Text("Enter Number of Days") },
                            placeholder = { Text("e.g. 45") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    val computedExpiryMs = remember(selectedPreset, customDaysInput) {
                        when (selectedPreset) {
                            "lifetime" -> 0L
                            "custom" -> {
                                val days = (customDaysInput.toIntOrNull() ?: 1).coerceAtLeast(1)
                                System.currentTimeMillis() + days * 24L * 60L * 60L * 1000L
                            }
                            else -> {
                                val days = selectedPreset.toIntOrNull() ?: 30
                                System.currentTimeMillis() + days * 24L * 60L * 60L * 1000L
                            }
                        }
                    }

                    val previewText = if (computedExpiryMs == 0L) {
                        "Unlimited Lifetime Access"
                    } else {
                        val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                        "Valid until ${formatter.format(Date(computedExpiryMs))}"
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "New Plan Summary",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = previewText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val expiryMs = when (selectedPreset) {
                            "lifetime" -> 0L
                            "custom" -> {
                                val days = (customDaysInput.toIntOrNull() ?: 1).coerceAtLeast(1)
                                System.currentTimeMillis() + days * 24L * 60L * 60L * 1000L
                            }
                            else -> {
                                val days = selectedPreset.toIntOrNull() ?: 30
                                System.currentTimeMillis() + days * 24L * 60L * 60L * 1000L
                            }
                        }
                        authViewModel.updateUserExpiry(user.email, expiryMs)
                        showExpiryDialogForUser = null
                    }
                ) {
                    Text("Save & Activate Plan", fontWeight = FontWeight.Bold)
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
private fun AdminKpiCard(
    title: String,
    value: String,
    accentColor: Color,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    ClayCard(
        modifier = Modifier.width(108.dp),
        accentColor = accentColor,
        containerColor = if (isSelected) accentColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        cornerRadius = 18.dp,
        elevation = if (isSelected) 6.dp else 3.dp,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                ClayIconOrb(
                    icon = icon,
                    accentColor = accentColor,
                    size = 24.dp,
                    iconSize = 13.dp,
                    cornerRadius = 8.dp
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun EnhancedUserAdminCard(
    user: UserAccount,
    onApprove30Days: () -> Unit,
    onApproveLifetime: () -> Unit,
    onDeclineOrRevoke: () -> Unit,
    onResetDevice: () -> Unit,
    onToggleAdminRole: () -> Unit,
    onDeleteUser: () -> Unit,
    onUpdateExpiry: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val now = System.currentTimeMillis()

    val isSuperAdmin = user.email.trim().lowercase() == AuthViewModel.SUPER_ADMIN_EMAIL
    val isExpired = !user.isAdmin && user.isApproved && user.expiryTimestamp in 1..<now
    val isRevoked = !user.isAdmin && (user.status == "rejected" || (!user.isApproved && user.status != "pending"))
    val isPending = !user.isAdmin && !user.isApproved && user.status == "pending"

    val (statusLabel, statusColor) = when {
        isSuperAdmin -> "SUPER ADMIN" to MaterialTheme.colorScheme.primary
        user.isAdmin -> "ADMIN" to Color(0xFF1565C0)
        isPending -> "PENDING APPROVAL" to Color(0xFFEF6C00)
        isRevoked -> "REVOKED / LOCKED" to MaterialTheme.colorScheme.error
        isExpired -> "PLAN EXPIRED" to Color(0xFFD84315)
        else -> "ACTIVE • APPROVED" to Color(0xFF2E7D32)
    }

    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        accentColor = statusColor,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        cornerRadius = 20.dp,
        elevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Row: 3D Clay Avatar Orb, Email + Copy, Status Badge
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ClayIconOrb(
                    icon = when {
                        isSuperAdmin || user.isAdmin -> Icons.Default.AdminPanelSettings
                        isPending -> Icons.Default.HourglassTop
                        isRevoked -> Icons.Default.Block
                        isExpired -> Icons.Default.EventBusy
                        else -> Icons.Default.VerifiedUser
                    },
                    accentColor = statusColor,
                    size = 46.dp,
                    iconSize = 22.dp,
                    cornerRadius = 15.dp
                )

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = user.email,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Email",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(15.dp)
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(user.email))
                                    Toast.makeText(context, "Email copied!", Toast.LENGTH_SHORT).show()
                                }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Status Pill
                    Surface(
                        color = statusColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Hardware & Telemetry Info Box
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Smartphone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = user.deviceModel.ifEmpty { "No Device Bound Yet" },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        val isBound = user.deviceId.isNotEmpty()
                        Text(
                            text = if (isBound) "🔒 Hardware Bound" else "🔓 Unbound",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isBound) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (user.deviceId.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ID: ${user.deviceId}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "Copy ID",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable {
                                        clipboardManager.setText(AnnotatedString(user.deviceId))
                                        Toast.makeText(context, "Device ID copied!", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(start = 8.dp)
                            )
                        }
                    }

                    // Plan Expiry & Remaining Days
                    val planInfoText = remember(user.expiryTimestamp, user.isAdmin, now) {
                        if (user.isAdmin || user.expiryTimestamp == 0L) {
                            "Plan: Unlimited Lifetime Access"
                        } else {
                            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                            val diffMs = user.expiryTimestamp - now
                            if (diffMs <= 0L) {
                                val daysAgo = TimeUnit.MILLISECONDS.toDays(-diffMs)
                                "Expired ${if (daysAgo > 0) "${daysAgo}d ago" else "today"} (${sdf.format(Date(user.expiryTimestamp))})"
                            } else {
                                val daysLeft = TimeUnit.MILLISECONDS.toDays(diffMs) + 1
                                "$daysLeft day(s) remaining • Ends ${sdf.format(Date(user.expiryTimestamp))}"
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = if (isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = planInfoText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (!isSuperAdmin) {
                            Text(
                                text = "Change Plan",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { onUpdateExpiry() }
                                    .padding(start = 8.dp)
                            )
                        }
                    }
                }
            }

            // Admin Controls Row (For all non-Super-Admin accounts)
            if (!isSuperAdmin) {
                Spacer(modifier = Modifier.height(12.dp))

                when {
                    isPending -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = onApprove30Days,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2E7D32),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Approve (30d)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            FilledTonalButton(
                                onClick = onApproveLifetime,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.AllInclusive, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Lifetime", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = onDeclineOrRevoke,
                                modifier = Modifier.height(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Text("Decline", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            IconButton(
                                onClick = onDeleteUser,
                                modifier = Modifier.size(38.dp),
                                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Account")
                            }
                        }
                    }

                    isRevoked || isExpired -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = onApprove30Days,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2E7D32),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Approve 30d", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            FilledTonalButton(
                                onClick = onUpdateExpiry,
                                modifier = Modifier.height(40.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.EditCalendar, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Custom Plan", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            IconButton(
                                onClick = onDeleteUser,
                                modifier = Modifier.size(38.dp),
                                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Account")
                            }
                        }
                    }

                    else -> {
                        // Active Approved User Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = onResetDevice,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.PhonelinkErase, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset Device", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = onToggleAdminRole,
                                modifier = Modifier.height(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Text(
                                    text = if (user.isAdmin) "Make User" else "Make Admin",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                )
                            }

                            OutlinedButton(
                                onClick = onDeclineOrRevoke,
                                modifier = Modifier.height(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Text("Revoke", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            IconButton(
                                onClick = onDeleteUser,
                                modifier = Modifier.size(36.dp),
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
