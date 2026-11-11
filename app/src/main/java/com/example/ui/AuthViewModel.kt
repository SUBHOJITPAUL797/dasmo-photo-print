package com.example.ui

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.domain.model.UserAccount
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

sealed class AuthState {
    object Loading : AuthState()
    object Unauthenticated : AuthState()
    data class PendingApproval(val user: UserAccount) : AuthState()
    data class AccessRevoked(val user: UserAccount) : AuthState()
    data class PlanExpired(val user: UserAccount) : AuthState()
    data class DeviceMismatch(
        val registeredDeviceModel: String,
        val currentDeviceId: String,
        val user: UserAccount
    ) : AuthState()
    data class Authenticated(val user: UserAccount) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel : ViewModel() {
    companion object {
        const val SUPER_ADMIN_EMAIL = "subhojitpaul26042004@gmail.com"
        private const val COLLECTION_USERS = "dasmo_photo_print_users"
    }

    var isOfflineBlocked by mutableStateOf(false)
        private set

    private val db = FirebaseFirestore.getInstance()

    var authState by mutableStateOf<AuthState>(AuthState.Loading)
        private set

    var currentUser by mutableStateOf<UserAccount?>(null)
        private set

    var allUsers by mutableStateOf<List<UserAccount>>(emptyList())
        private set

    var adminStatusMessage by mutableStateOf<String?>(null)

    private var usersListener: ListenerRegistration? = null
    private var userDocListener: ListenerRegistration? = null
    private var sessionListener: ListenerRegistration? = null

    @SuppressLint("HardwareIds")
    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
    }

    fun getDeviceModel(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
    }

    fun clearAdminStatusMessage() {
        adminStatusMessage = null
    }

    fun checkLogin(context: Context, savedEmail: String?, savedSessionToken: String? = null) {
        if (savedEmail.isNullOrBlank()) {
            authState = AuthState.Unauthenticated
            return
        }
        login(context, savedEmail, savedSessionToken)
    }

    private fun parseUserFromSnapshot(
        email: String,
        snapshot: com.google.firebase.firestore.DocumentSnapshot
    ): UserAccount {
        val normalizedEmail = email.trim().lowercase()
        val isSuperAdmin = normalizedEmail == SUPER_ADMIN_EMAIL

        val rawStatus = (snapshot.getString("status") ?: snapshot.getString("dasmo_status") ?: "pending").lowercase()
        val rawApprovedFlag = snapshot.getBoolean("isApproved") ?: snapshot.getBoolean("dasmo_isApproved") ?: false
        val rawRole = (snapshot.getString("role") ?: snapshot.getString("dasmo_role") ?: if (isSuperAdmin) "admin" else "user").lowercase()
        val rawDeviceId = snapshot.getString("deviceId") ?: snapshot.getString("dasmo_deviceId") ?: ""
        val rawDeviceModel = snapshot.getString("deviceModel") ?: ""
        val rawSession = snapshot.getString("currentSessionToken") ?: ""
        val rawExpiry = snapshot.getLong("expiryTimestamp") ?: 0L
        val rawAdmin = isSuperAdmin || rawRole == "admin" || (snapshot.getBoolean("isAdmin") ?: false) || (snapshot.getBoolean("dasmo_isAdmin") ?: false)
        val rawRegTime = snapshot.getLong("registrationTimestamp") ?: System.currentTimeMillis()
        val rawLastActive = snapshot.getLong("lastActiveTimestamp") ?: System.currentTimeMillis()
        val rawAppSource = snapshot.getString("appSource") ?: "DASMO Photo Print"

        // Strict approval evaluation:
        // Super admin / admin is always approved.
        // Standard user is ONLY approved if status is strictly "approved" AND isApproved flag is true,
        // and never if status is "pending", "rejected", "revoked", or "declined".
        val isRevokedStatus = rawStatus == "rejected" || rawStatus == "revoked" || rawStatus == "declined"
        val isStrictApproved = if (rawAdmin) {
            true
        } else if (isRevokedStatus || rawStatus == "pending") {
            false
        } else {
            rawApprovedFlag && rawStatus == "approved"
        }

        val normalizedStatus = when {
            rawAdmin -> "approved"
            isRevokedStatus -> "rejected"
            isStrictApproved -> "approved"
            else -> "pending"
        }

        return UserAccount(
            email = normalizedEmail,
            deviceId = rawDeviceId,
            deviceModel = rawDeviceModel,
            isApproved = isStrictApproved,
            isAdmin = rawAdmin,
            role = if (rawAdmin) "admin" else "user",
            status = normalizedStatus,
            currentSessionToken = rawSession,
            expiryTimestamp = if (rawAdmin) 0L else rawExpiry,
            registrationTimestamp = rawRegTime,
            lastActiveTimestamp = rawLastActive,
            appSource = rawAppSource
        )
    }

    fun login(
        context: Context,
        email: String,
        existingSessionToken: String? = null,
        onSessionCreated: ((String) -> Unit)? = null
    ) {
        val trimmedEmail = email.trim().lowercase()
        if (trimmedEmail.isEmpty()) {
            authState = AuthState.Unauthenticated
            return
        }

        authState = AuthState.Loading
        val deviceId = getDeviceId(context)
        val deviceModel = getDeviceModel()
        val isSuperAdmin = trimmedEmail == SUPER_ADMIN_EMAIL

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val docRef = db.collection(COLLECTION_USERS).document(trimmedEmail)
                val snapshot = docRef.get().await()

                if (snapshot != null && snapshot.exists()) {
                    var user = parseUserFromSnapshot(trimmedEmail, snapshot)

                    // 1. Strict Hardware Device Binding Check (One Account Per Physical Device)
                    if (!user.isAdmin && user.deviceId.isNotEmpty() && user.deviceId != deviceId) {
                        withContext(Dispatchers.Main) {
                            currentUser = user
                            authState = AuthState.DeviceMismatch(
                                registeredDeviceModel = user.deviceModel.ifEmpty { "Registered Android Device" },
                                currentDeviceId = deviceId,
                                user = user
                            )
                            startUserDocObserver(context, trimmedEmail, deviceId, onSessionCreated)
                        }
                        return@launch
                    }

                    // Bind physical device if not yet bound
                    if (user.deviceId.isEmpty() || (user.isAdmin && user.deviceId != deviceId)) {
                        try {
                            docRef.update(
                                mapOf(
                                    "deviceId" to deviceId,
                                    "dasmo_deviceId" to deviceId,
                                    "deviceModel" to deviceModel,
                                    "lastActiveTimestamp" to System.currentTimeMillis()
                                )
                            ).await()
                            user = user.copy(deviceId = deviceId, deviceModel = deviceModel)
                        } catch (_: Exception) {
                            // Continue if update is blocked while pending
                        }
                    }

                    if (user.isAdmin) {
                        val newSessionToken = existingSessionToken ?: UUID.randomUUID().toString()
                        user = user.copy(
                            isApproved = true,
                            isAdmin = true,
                            role = "admin",
                            status = "approved",
                            currentSessionToken = newSessionToken,
                            lastActiveTimestamp = System.currentTimeMillis()
                        )
                        try {
                            docRef.set(user, SetOptions.merge()).await()
                        } catch (_: Exception) {}

                        withContext(Dispatchers.Main) {
                            currentUser = user
                            authState = AuthState.Authenticated(user)
                            onSessionCreated?.invoke(newSessionToken)
                            startListeningUsers(context)
                            startUserDocObserver(context, trimmedEmail, deviceId, onSessionCreated)
                        }
                    } else {
                        // 2. Check if Revoked / Rejected by Admin
                        if (user.status == "rejected") {
                            withContext(Dispatchers.Main) {
                                currentUser = user
                                authState = AuthState.AccessRevoked(user)
                                startUserDocObserver(context, trimmedEmail, deviceId, onSessionCreated)
                            }
                            return@launch
                        }

                        // 3. Strict Admin Approval Verification — NO ONE enters without approval
                        if (!user.isApproved || user.status != "approved") {
                            withContext(Dispatchers.Main) {
                                currentUser = user
                                authState = AuthState.PendingApproval(user)
                                startUserDocObserver(context, trimmedEmail, deviceId, onSessionCreated)
                            }
                            return@launch
                        }

                        // 4. Subscription / Access Plan Duration Check
                        val now = System.currentTimeMillis()
                        if (user.expiryTimestamp > 0L && now > user.expiryTimestamp) {
                            withContext(Dispatchers.Main) {
                                currentUser = user
                                authState = AuthState.PlanExpired(user)
                                startUserDocObserver(context, trimmedEmail, deviceId, onSessionCreated)
                            }
                            return@launch
                        }

                        // 5. Fully Approved & Valid Plan -> Grant Authenticated Session
                        val newSessionToken = existingSessionToken ?: UUID.randomUUID().toString()
                        user = user.copy(
                            currentSessionToken = newSessionToken,
                            lastActiveTimestamp = now
                        )
                        try {
                            docRef.update(
                                mapOf(
                                    "currentSessionToken" to newSessionToken,
                                    "lastActiveTimestamp" to now,
                                    "appSource" to "DASMO Photo Print"
                                )
                            ).await()
                        } catch (_: Exception) {}

                        withContext(Dispatchers.Main) {
                            currentUser = user
                            authState = AuthState.Authenticated(user)
                            onSessionCreated?.invoke(newSessionToken)
                            startUserDocObserver(context, trimmedEmail, deviceId, onSessionCreated)
                        }
                    }
                } else {
                    // First-time user registration in Firestore
                    val now = System.currentTimeMillis()
                    val newSessionToken = UUID.randomUUID().toString()
                    val newUser = UserAccount(
                        email = trimmedEmail,
                        deviceId = deviceId,
                        deviceModel = deviceModel,
                        isApproved = isSuperAdmin,
                        isAdmin = isSuperAdmin,
                        role = if (isSuperAdmin) "admin" else "user",
                        status = if (isSuperAdmin) "approved" else "pending",
                        currentSessionToken = newSessionToken,
                        expiryTimestamp = 0L,
                        registrationTimestamp = now,
                        lastActiveTimestamp = now,
                        appSource = "DASMO Photo Print"
                    )
                    docRef.set(newUser).await()

                    withContext(Dispatchers.Main) {
                        currentUser = newUser
                        if (isSuperAdmin) {
                            authState = AuthState.Authenticated(newUser)
                            onSessionCreated?.invoke(newSessionToken)
                            startListeningUsers(context)
                        } else {
                            authState = AuthState.PendingApproval(newUser)
                        }
                        startUserDocObserver(context, trimmedEmail, deviceId, onSessionCreated)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    val msg = e.localizedMessage ?: e.message ?: "Authentication failed."
                    authState = AuthState.Error(
                        "Unable to verify account security status: $msg\n\nPlease check your internet connection and try again."
                    )
                }
            }
        }
    }

    /**
     * Real-time listener for the signed-in user's Firestore document.
     * Immediately unlocks the app when approved by Admin, or immediately locks the app
     * if Admin revokes access, changes expiry, resets device, or deletes the record.
     */
    private fun startUserDocObserver(
        context: Context,
        email: String,
        currentDeviceId: String,
        onSessionCreated: ((String) -> Unit)?
    ) {
        userDocListener?.remove()
        val normalizedEmail = email.trim().lowercase()
        userDocListener = db.collection(COLLECTION_USERS).document(normalizedEmail)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val isSuperAdmin = normalizedEmail == SUPER_ADMIN_EMAIL

                if (snapshot != null && !snapshot.exists()) {
                    if (!isSuperAdmin) {
                        // User record was deleted by Admin -> immediately lock out
                        currentUser = null
                        authState = AuthState.Unauthenticated
                    }
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val user = parseUserFromSnapshot(normalizedEmail, snapshot)

                    // 1. Strict Hardware Device Binding Check
                    if (!user.isAdmin && user.deviceId.isNotEmpty() && user.deviceId != currentDeviceId) {
                        currentUser = user
                        authState = AuthState.DeviceMismatch(
                            registeredDeviceModel = user.deviceModel.ifEmpty { "Registered Android Device" },
                            currentDeviceId = currentDeviceId,
                            user = user
                        )
                        return@addSnapshotListener
                    }

                    // 2. Admin / Super Admin bypass
                    if (user.isAdmin) {
                        currentUser = user
                        if (authState !is AuthState.Authenticated) {
                            val token = user.currentSessionToken.ifEmpty { UUID.randomUUID().toString() }
                            onSessionCreated?.invoke(token)
                        }
                        authState = AuthState.Authenticated(user)
                        return@addSnapshotListener
                    }

                    // 3. Revoked / Blocked Check
                    if (user.status == "rejected") {
                        currentUser = user
                        authState = AuthState.AccessRevoked(user)
                        return@addSnapshotListener
                    }

                    // 4. Pending Approval Check
                    if (!user.isApproved || user.status != "approved") {
                        currentUser = user
                        authState = AuthState.PendingApproval(user)
                        return@addSnapshotListener
                    }

                    // 5. Subscription Expiry Check
                    val now = System.currentTimeMillis()
                    if (user.expiryTimestamp > 0L && now > user.expiryTimestamp) {
                        currentUser = user
                        authState = AuthState.PlanExpired(user)
                        return@addSnapshotListener
                    }

                    // 6. Approved & Active!
                    val wasNotAuthenticated = authState !is AuthState.Authenticated
                    currentUser = user
                    authState = AuthState.Authenticated(user)
                    if (wasNotAuthenticated) {
                        val token = user.currentSessionToken.ifEmpty { UUID.randomUUID().toString() }
                        onSessionCreated?.invoke(token)
                        // Also bind device ID if it was cleared during a device reset
                        if (user.deviceId.isEmpty()) {
                            val model = getDeviceModel()
                            db.collection(COLLECTION_USERS).document(normalizedEmail).update(
                                mapOf(
                                    "deviceId" to currentDeviceId,
                                    "dasmo_deviceId" to currentDeviceId,
                                    "deviceModel" to model,
                                    "currentSessionToken" to token,
                                    "lastActiveTimestamp" to now
                                )
                            )
                        }
                    }
                }
            }
    }

    fun startSessionObserver(email: String, currentToken: String, onSessionInvalidated: () -> Unit) {
        sessionListener?.remove()
        if (currentToken.isBlank() || email.isBlank()) return
        val normalizedEmail = email.trim().lowercase()

        sessionListener = db.collection(COLLECTION_USERS).document(normalizedEmail)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val isAdmin = normalizedEmail == SUPER_ADMIN_EMAIL ||
                        snapshot?.getString("role") == "admin" ||
                        (snapshot?.getBoolean("isAdmin") ?: false)

                if (snapshot != null && !snapshot.exists() && !isAdmin) {
                    onSessionInvalidated()
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists() && !isAdmin) {
                    val user = parseUserFromSnapshot(normalizedEmail, snapshot)
                    val serverSession = user.currentSessionToken
                    if (!user.isApproved || user.status != "approved") {
                        // Let startUserDocObserver transition to PendingApproval or AccessRevoked
                        return@addSnapshotListener
                    }
                    if (serverSession.isNotEmpty() && serverSession != currentToken) {
                        onSessionInvalidated()
                    }
                }
            }
    }

    fun stopSessionObserver() {
        sessionListener?.remove()
        sessionListener = null
        userDocListener?.remove()
        userDocListener = null
    }

    fun monitorNetwork(context: Context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        isOfflineBlocked = activeNetwork == null || caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true

        connectivityManager.registerNetworkCallback(networkRequest, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isOfflineBlocked = false
            }
            override fun onLost(network: Network) {
                isOfflineBlocked = true
            }
        })
    }

    fun logout(context: Context? = null) {
        stopSessionObserver()
        stopListeningUsers()
        currentUser = null
        try {
            FirebaseAuth.getInstance().signOut()
        } catch (_: Exception) {}
        if (context != null) {
            try {
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(context.getString(R.string.default_web_client_id))
                    .requestEmail()
                    .build()
                GoogleSignIn.getClient(context, gso).signOut()
            } catch (_: Exception) {}
        }
        authState = AuthState.Unauthenticated
    }

    // ==========================================
    // ADMIN DASHBOARD REAL-TIME FUNCTIONS
    // ==========================================

    private fun isCurrentUserAuthorizedAdmin(): Boolean {
        val u = currentUser ?: return false
        return u.email.trim().lowercase() == SUPER_ADMIN_EMAIL || u.isAdmin
    }

    fun startListeningUsers(context: Context) {
        usersListener?.remove()
        var isInitialLoad = true
        usersListener = db.collection(COLLECTION_USERS).addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null) {
                val list = snapshot.documents.mapNotNull { doc ->
                    val email = doc.getString("email")?.takeIf { it.isNotBlank() } ?: doc.id
                    parseUserFromSnapshot(email, doc)
                }.sortedWith(
                    compareBy<UserAccount> {
                        when {
                            it.status == "pending" && !it.isApproved && !it.isAdmin -> 0
                            it.isAdmin -> 1
                            it.isApproved -> 2
                            else -> 3
                        }
                    }.thenByDescending { it.registrationTimestamp }
                )

                allUsers = list

                // Trigger local notification for new pending access requests after initial load
                if (!isInitialLoad) {
                    for (change in snapshot.documentChanges) {
                        if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                            val email = change.document.id
                            val status = change.document.getString("status") ?: "pending"
                            if (status == "pending" && email.lowercase() != SUPER_ADMIN_EMAIL) {
                                triggerLocalNotification(context, email)
                            }
                        }
                    }
                }
                isInitialLoad = false
            }
        }
    }

    private fun triggerLocalNotification(context: Context, email: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channelId = "dasmo_admin_channel"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    "DASMO Admin Alerts",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts for new user access requests"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = android.content.Intent(context, com.example.MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt(),
                intent,
                android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("New Access Request")
                .setContentText("User '$email' is awaiting approval.")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            notificationManager.notify(email.hashCode(), builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopListeningUsers() {
        usersListener?.remove()
        usersListener = null
    }

    fun fetchAllUsers() {
        viewModelScope.launch {
            try {
                val snapshot = db.collection(COLLECTION_USERS).get().await()
                allUsers = snapshot.documents.mapNotNull { doc ->
                    val email = doc.getString("email")?.takeIf { it.isNotBlank() } ?: doc.id
                    parseUserFromSnapshot(email, doc)
                }.sortedWith(
                    compareBy<UserAccount> {
                        when {
                            it.status == "pending" && !it.isApproved && !it.isAdmin -> 0
                            it.isAdmin -> 1
                            it.isApproved -> 2
                            else -> 3
                        }
                    }.thenByDescending { it.registrationTimestamp }
                )
                adminStatusMessage = "User directory refreshed (${allUsers.size} accounts)"
            } catch (e: Exception) {
                e.printStackTrace()
                adminStatusMessage = "Failed to refresh users: ${e.localizedMessage}"
            }
        }
    }

    fun approveUser(email: String, durationDays: Int? = null) {
        viewModelScope.launch {
            if (!isCurrentUserAuthorizedAdmin()) return@launch
            try {
                val trimmed = email.trim().lowercase()
                val updates = mutableMapOf<String, Any>(
                    "isApproved" to true,
                    "dasmo_isApproved" to true,
                    "status" to "approved",
                    "dasmo_status" to "approved"
                )
                if (durationDays != null) {
                    val expiry = if (durationDays <= 0) 0L else System.currentTimeMillis() + durationDays * 24L * 60L * 60L * 1000L
                    updates["expiryTimestamp"] = expiry
                }
                db.collection(COLLECTION_USERS).document(trimmed).update(updates).await()
                adminStatusMessage = "Approved access for $trimmed"
            } catch (e: Exception) {
                e.printStackTrace()
                adminStatusMessage = "Error approving $email: ${e.localizedMessage}"
            }
        }
    }

    fun approveAllPendingUsers() {
        viewModelScope.launch {
            if (!isCurrentUserAuthorizedAdmin()) return@launch
            try {
                val pendingList = allUsers.filter { !it.isAdmin && !it.isApproved && it.status == "pending" }
                if (pendingList.isEmpty()) return@launch
                for (u in pendingList) {
                    db.collection(COLLECTION_USERS).document(u.email.trim().lowercase()).update(
                        mapOf(
                            "isApproved" to true,
                            "dasmo_isApproved" to true,
                            "status" to "approved",
                            "dasmo_status" to "approved"
                        )
                    ).await()
                }
                adminStatusMessage = "Approved ${pendingList.size} pending account(s)"
            } catch (e: Exception) {
                e.printStackTrace()
                adminStatusMessage = "Batch approval error: ${e.localizedMessage}"
            }
        }
    }

    fun rejectUser(email: String) {
        viewModelScope.launch {
            if (!isCurrentUserAuthorizedAdmin()) return@launch
            try {
                val trimmed = email.trim().lowercase()
                if (trimmed == SUPER_ADMIN_EMAIL) return@launch
                db.collection(COLLECTION_USERS).document(trimmed).update(
                    mapOf(
                        "isApproved" to false,
                        "dasmo_isApproved" to false,
                        "status" to "rejected",
                        "dasmo_status" to "rejected",
                        "currentSessionToken" to ""
                    )
                ).await()
                adminStatusMessage = "Revoked access for $trimmed"
            } catch (e: Exception) {
                e.printStackTrace()
                adminStatusMessage = "Error revoking $email: ${e.localizedMessage}"
            }
        }
    }

    fun updateUserRole(email: String, newRole: String) {
        viewModelScope.launch {
            if (!isCurrentUserAuthorizedAdmin()) return@launch
            try {
                val trimmed = email.trim().lowercase()
                if (trimmed == SUPER_ADMIN_EMAIL) return@launch
                val isAdmin = newRole == "admin"
                val updates = mutableMapOf<String, Any>(
                    "role" to newRole,
                    "dasmo_role" to newRole,
                    "isAdmin" to isAdmin,
                    "dasmo_isAdmin" to isAdmin
                )
                if (isAdmin) {
                    updates["isApproved"] = true
                    updates["dasmo_isApproved"] = true
                    updates["status"] = "approved"
                    updates["dasmo_status"] = "approved"
                }
                db.collection(COLLECTION_USERS).document(trimmed).update(updates).await()
                adminStatusMessage = "Updated role for $trimmed to ${newRole.uppercase()}"
            } catch (e: Exception) {
                e.printStackTrace()
                adminStatusMessage = "Error updating role: ${e.localizedMessage}"
            }
        }
    }

    fun updateUserExpiry(email: String, expiryTimestamp: Long) {
        viewModelScope.launch {
            if (!isCurrentUserAuthorizedAdmin()) return@launch
            try {
                val trimmed = email.trim().lowercase()
                // Also ensure user is marked approved when assigning a valid future or lifetime plan
                val isFutureOrLifetime = expiryTimestamp == 0L || expiryTimestamp > System.currentTimeMillis()
                val updates = mutableMapOf<String, Any>(
                    "expiryTimestamp" to expiryTimestamp
                )
                if (isFutureOrLifetime) {
                    updates["isApproved"] = true
                    updates["dasmo_isApproved"] = true
                    updates["status"] = "approved"
                    updates["dasmo_status"] = "approved"
                }
                db.collection(COLLECTION_USERS).document(trimmed).update(updates).await()
                adminStatusMessage = "Updated access plan for $trimmed"
            } catch (e: Exception) {
                e.printStackTrace()
                adminStatusMessage = "Error updating plan: ${e.localizedMessage}"
            }
        }
    }

    fun createUserManually(email: String, role: String, status: String, expiryTimestamp: Long = 0L) {
        viewModelScope.launch {
            if (!isCurrentUserAuthorizedAdmin()) return@launch
            try {
                val trimmedEmail = email.trim().lowercase()
                if (trimmedEmail.isEmpty()) return@launch
                val isAdmin = role == "admin" || trimmedEmail == SUPER_ADMIN_EMAIL
                val isApproved = status == "approved" || isAdmin
                val finalStatus = if (isApproved) "approved" else "pending"
                val user = mapOf(
                    "email" to trimmedEmail,
                    "role" to if (isAdmin) "admin" else "user",
                    "dasmo_role" to if (isAdmin) "admin" else "user",
                    "status" to finalStatus,
                    "dasmo_status" to finalStatus,
                    "isApproved" to isApproved,
                    "dasmo_isApproved" to isApproved,
                    "isAdmin" to isAdmin,
                    "dasmo_isAdmin" to isAdmin,
                    "expiryTimestamp" to if (isAdmin) 0L else expiryTimestamp,
                    "registrationTimestamp" to System.currentTimeMillis(),
                    "lastActiveTimestamp" to System.currentTimeMillis(),
                    "appSource" to "Admin Pre-Approval"
                )
                db.collection(COLLECTION_USERS).document(trimmedEmail).set(user, SetOptions.merge()).await()
                adminStatusMessage = "Account $trimmedEmail saved ($finalStatus)"
            } catch (e: Exception) {
                e.printStackTrace()
                adminStatusMessage = "Failed to create account: ${e.localizedMessage}"
            }
        }
    }

    /**
     * Unbinds the registered hardware device from this account while keeping or resetting their approval.
     */
    fun revokeDevice(email: String, keepApproved: Boolean = true) {
        viewModelScope.launch {
            if (!isCurrentUserAuthorizedAdmin()) return@launch
            try {
                val trimmed = email.trim().lowercase()
                val statusVal = if (keepApproved) "approved" else "pending"
                db.collection(COLLECTION_USERS).document(trimmed).update(
                    mapOf(
                        "deviceId" to "",
                        "dasmo_deviceId" to "",
                        "deviceModel" to "",
                        "status" to statusVal,
                        "dasmo_status" to statusVal,
                        "isApproved" to keepApproved,
                        "dasmo_isApproved" to keepApproved,
                        "currentSessionToken" to ""
                    )
                ).await()
                adminStatusMessage = "Hardware device lock cleared for $trimmed"
            } catch (e: Exception) {
                e.printStackTrace()
                adminStatusMessage = "Failed to reset device: ${e.localizedMessage}"
            }
        }
    }

    fun deleteUser(email: String) {
        viewModelScope.launch {
            if (!isCurrentUserAuthorizedAdmin()) return@launch
            try {
                val trimmed = email.trim().lowercase()
                if (trimmed == SUPER_ADMIN_EMAIL) return@launch
                db.collection(COLLECTION_USERS).document(trimmed).delete().await()
                adminStatusMessage = "Deleted account $trimmed"
            } catch (e: Exception) {
                e.printStackTrace()
                adminStatusMessage = "Failed to delete account: ${e.localizedMessage}"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopListeningUsers()
        stopSessionObserver()
    }
}
