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
import com.example.domain.model.UserAccount
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
    data class DeviceMismatch(val registeredDeviceModel: String, val user: UserAccount) : AuthState()
    data class Authenticated(val user: UserAccount) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel : ViewModel() {
    var isOfflineBlocked by mutableStateOf(false)
        private set

    private val db = FirebaseFirestore.getInstance()

    var authState by mutableStateOf<AuthState>(AuthState.Loading)
        private set

    var currentUser by mutableStateOf<UserAccount?>(null)
        private set

    var allUsers by mutableStateOf<List<UserAccount>>(emptyList())
        private set

    private var usersListener: ListenerRegistration? = null
    private var userDocListener: ListenerRegistration? = null
    private var sessionListener: ListenerRegistration? = null

    @SuppressLint("HardwareIds")
    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
    }

    fun getDeviceModel(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}"
    }

    fun checkLogin(context: Context, savedEmail: String?, savedSessionToken: String? = null) {
        if (savedEmail.isNullOrBlank()) {
            authState = AuthState.Unauthenticated
            return
        }
        login(context, savedEmail, savedSessionToken)
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
        val isSuperAdmin = trimmedEmail == "subhojitpaul26042004@gmail.com"

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val docRef = db.collection("dasmo_photo_print_users").document(trimmedEmail)
                val snapshot = docRef.get().await()

                if (snapshot != null && snapshot.exists()) {
                    val rawApproved = snapshot.getBoolean("isApproved") ?: snapshot.getBoolean("dasmo_isApproved") ?: false
                    val rawStatus = snapshot.getString("status") ?: snapshot.getString("dasmo_status") ?: "pending"
                    val rawRole = snapshot.getString("role") ?: snapshot.getString("dasmo_role") ?: if (isSuperAdmin) "admin" else "user"
                    val rawDeviceId = snapshot.getString("deviceId") ?: snapshot.getString("dasmo_deviceId") ?: ""
                    val rawDeviceModel = snapshot.getString("deviceModel") ?: ""
                    val rawSession = snapshot.getString("currentSessionToken") ?: ""
                    val rawExpiry = snapshot.getLong("expiryTimestamp") ?: 0L
                    val rawAdmin = isSuperAdmin || rawRole == "admin" || (snapshot.getBoolean("isAdmin") ?: false) || (snapshot.getBoolean("dasmo_isAdmin") ?: false)
                    val rawRegTime = snapshot.getLong("registrationTimestamp") ?: System.currentTimeMillis()

                    val isUserApproved = rawApproved || rawStatus == "approved" || rawAdmin

                    var user = UserAccount(
                        email = trimmedEmail,
                        deviceId = rawDeviceId,
                        deviceModel = rawDeviceModel,
                        isApproved = isUserApproved,
                        isAdmin = rawAdmin,
                        role = if (rawAdmin) "admin" else rawRole,
                        status = if (rawAdmin) "approved" else rawStatus,
                        currentSessionToken = rawSession,
                        expiryTimestamp = rawExpiry,
                        registrationTimestamp = rawRegTime,
                        lastActiveTimestamp = System.currentTimeMillis(),
                        appSource = "DASMO Photo Print"
                    )

                    withContext(Dispatchers.Main) {
                        // 1. Strict Hardware Device Binding Check (One Account Per Physical Device)
                        if (user.deviceId.isNotEmpty() && user.deviceId != deviceId) {
                            authState = AuthState.DeviceMismatch(
                                registeredDeviceModel = user.deviceModel.ifEmpty { "Another Device" },
                                user = user
                            )
                            startUserDocObserver(trimmedEmail, deviceId, onSessionCreated)
                            return@withContext
                        }

                        // If not yet bound to a device (first login or after admin unbinds), bind this physical device:
                        if (user.deviceId.isEmpty()) {
                            docRef.update(
                                mapOf(
                                    "deviceId" to deviceId,
                                    "dasmo_deviceId" to deviceId,
                                    "deviceModel" to deviceModel,
                                    "lastActiveTimestamp" to System.currentTimeMillis()
                                )
                            )
                            user = user.copy(deviceId = deviceId, deviceModel = deviceModel)
                        }

                        if (isSuperAdmin) {
                            val newSessionToken = UUID.randomUUID().toString()
                            user = user.copy(
                                isApproved = true,
                                isAdmin = true,
                                role = "admin",
                                status = "approved",
                                currentSessionToken = newSessionToken,
                                lastActiveTimestamp = System.currentTimeMillis()
                            )
                            docRef.set(user, SetOptions.merge())
                            currentUser = user
                            authState = AuthState.Authenticated(user)
                            onSessionCreated?.invoke(newSessionToken)
                            startListeningUsers(context)
                            startUserDocObserver(trimmedEmail, deviceId, onSessionCreated)
                        } else {
                            // 2. Admin Approval Verification
                            if (!user.isApproved || user.status != "approved") {
                                authState = AuthState.PendingApproval(user)
                                startUserDocObserver(trimmedEmail, deviceId, onSessionCreated)
                                return@withContext
                            }

                            // 3. Subscription / Access Plan Duration Check
                            if (user.expiryTimestamp > 0L && System.currentTimeMillis() > user.expiryTimestamp) {
                                authState = AuthState.Error(
                                    "Access Plan Expired: Your subscription ended on ${formatTimestamp(user.expiryTimestamp)}. Please contact administrator to renew."
                                )
                                return@withContext
                            }

                            // 4. Approved and Authorized: Grant Access!
                            val newSessionToken = UUID.randomUUID().toString()
                            user = user.copy(
                                currentSessionToken = newSessionToken,
                                lastActiveTimestamp = System.currentTimeMillis()
                            )
                            docRef.update(
                                mapOf(
                                    "currentSessionToken" to newSessionToken,
                                    "lastActiveTimestamp" to System.currentTimeMillis(),
                                    "appSource" to "DASMO Photo Print"
                                )
                            )
                            currentUser = user
                            authState = AuthState.Authenticated(user)
                            onSessionCreated?.invoke(newSessionToken)
                            startUserDocObserver(trimmedEmail, deviceId, onSessionCreated)
                        }
                    }
                } else {
                    // New user registering for the very first time
                    withContext(Dispatchers.Main) {
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
                            registrationTimestamp = System.currentTimeMillis(),
                            lastActiveTimestamp = System.currentTimeMillis(),
                            appSource = "DASMO Photo Print"
                        )
                        docRef.set(newUser)

                        if (isSuperAdmin) {
                            currentUser = newUser
                            authState = AuthState.Authenticated(newUser)
                            onSessionCreated?.invoke(newSessionToken)
                            startListeningUsers(context)
                            startUserDocObserver(trimmedEmail, deviceId, onSessionCreated)
                        } else {
                            authState = AuthState.PendingApproval(newUser)
                            startUserDocObserver(trimmedEmail, deviceId, onSessionCreated)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    val msg = e.localizedMessage ?: e.message ?: "Authentication failed."
                    authState = AuthState.Error("Database Connection Error: $msg\n\nPlease check your internet connection and Firestore setup.")
                }
            }
        }
    }

    /**
     * Real-time listener for standard user's document.
     * When the admin approves/revokes/resets in the Admin Panel, the client automatically reacts in real-time!
     */
    private fun startUserDocObserver(
        email: String,
        currentDeviceId: String,
        onSessionCreated: ((String) -> Unit)?
    ) {
        userDocListener?.remove()
        userDocListener = db.collection("dasmo_photo_print_users").document(email).addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null && snapshot.exists()) {
                val isSuperAdmin = email == "subhojitpaul26042004@gmail.com"
                val rawApproved = snapshot.getBoolean("isApproved") ?: snapshot.getBoolean("dasmo_isApproved") ?: false
                val rawStatus = snapshot.getString("status") ?: snapshot.getString("dasmo_status") ?: "pending"
                val rawRole = snapshot.getString("role") ?: snapshot.getString("dasmo_role") ?: if (isSuperAdmin) "admin" else "user"
                val rawDeviceId = snapshot.getString("deviceId") ?: snapshot.getString("dasmo_deviceId") ?: ""
                val rawDeviceModel = snapshot.getString("deviceModel") ?: ""
                val rawSession = snapshot.getString("currentSessionToken") ?: ""
                val rawExpiry = snapshot.getLong("expiryTimestamp") ?: 0L
                val rawAdmin = isSuperAdmin || rawRole == "admin" || (snapshot.getBoolean("isAdmin") ?: false) || (snapshot.getBoolean("dasmo_isAdmin") ?: false)
                val rawRegTime = snapshot.getLong("registrationTimestamp") ?: System.currentTimeMillis()

                val isUserApproved = rawApproved || rawStatus == "approved" || rawAdmin

                val user = UserAccount(
                    email = email,
                    deviceId = rawDeviceId,
                    deviceModel = rawDeviceModel,
                    isApproved = isUserApproved,
                    isAdmin = rawAdmin,
                    role = if (rawAdmin) "admin" else rawRole,
                    status = if (rawAdmin) "approved" else rawStatus,
                    currentSessionToken = rawSession,
                    expiryTimestamp = rawExpiry,
                    registrationTimestamp = rawRegTime,
                    lastActiveTimestamp = System.currentTimeMillis(),
                    appSource = "DASMO Photo Print"
                )

                // 1. Strict Hardware Device Binding Check (One Account Per Physical Device)
                if (user.deviceId.isNotEmpty() && user.deviceId != currentDeviceId) {
                    authState = AuthState.DeviceMismatch(
                        registeredDeviceModel = user.deviceModel.ifEmpty { "Another Device" },
                        user = user
                    )
                    return@addSnapshotListener
                }

                if (rawAdmin) {
                    currentUser = user
                    if (authState !is AuthState.Authenticated) {
                        authState = AuthState.Authenticated(user)
                    }
                    return@addSnapshotListener
                }

                if (!user.isApproved || user.status != "approved") {
                    authState = AuthState.PendingApproval(user)
                } else if (user.expiryTimestamp > 0L && System.currentTimeMillis() > user.expiryTimestamp) {
                    authState = AuthState.Error("Subscription Plan Expired. Contact administrator to renew.")
                } else {
                    currentUser = user
                    if (authState !is AuthState.Authenticated) {
                        val sessionToken = UUID.randomUUID().toString()
                        db.collection("dasmo_photo_print_users").document(email).update(
                            "currentSessionToken", sessionToken,
                            "lastActiveTimestamp", System.currentTimeMillis()
                        )
                        authState = AuthState.Authenticated(user.copy(currentSessionToken = sessionToken))
                        onSessionCreated?.invoke(sessionToken)
                    }
                }
            }
        }
    }

    fun startSessionObserver(email: String, currentToken: String, onSessionInvalidated: () -> Unit) {
        sessionListener?.remove()
        if (currentToken.isBlank() || email.isBlank()) return

        sessionListener = db.collection("dasmo_photo_print_users").document(email).addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null && snapshot.exists()) {
                val serverSession = snapshot.getString("currentSessionToken")
                val isApproved = (snapshot.getBoolean("isApproved") ?: false) || snapshot.getString("status") == "approved"
                val isAdmin = email == "subhojitpaul26042004@gmail.com" || snapshot.getString("role") == "admin"

                if (!isAdmin) {
                    if (!isApproved) {
                        onSessionInvalidated()
                    } else if (serverSession != null && serverSession.isNotEmpty() && serverSession != currentToken) {
                        onSessionInvalidated()
                    }
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

    fun logout() {
        stopSessionObserver()
        currentUser = null
        authState = AuthState.Unauthenticated
    }

    // ==========================================
    // 👑 ADMIN DASHBOARD REAL-TIME FUNCTIONS
    // ==========================================

    fun startListeningUsers(context: Context) {
        usersListener?.remove()
        var isInitialLoad = true
        usersListener = db.collection("dasmo_photo_print_users").addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null) {
                val list = snapshot.documents.mapNotNull { doc ->
                    val email = doc.getString("email")?.takeIf { it.isNotBlank() } ?: doc.id
                    val isSuperAdmin = email == "subhojitpaul26042004@gmail.com"
                    val isApprovedVal = doc.getBoolean("dasmo_isApproved") ?: doc.getBoolean("isApproved") ?: false
                    val statusVal = doc.getString("dasmo_status") ?: doc.getString("status") ?: "pending"
                    val roleVal = doc.getString("dasmo_role") ?: doc.getString("role") ?: if (isSuperAdmin) "admin" else "user"
                    val deviceIdVal = doc.getString("dasmo_deviceId") ?: doc.getString("deviceId") ?: ""
                    val deviceModelVal = doc.getString("deviceModel") ?: ""
                    val sessionVal = doc.getString("currentSessionToken") ?: ""
                    val expiryVal = doc.getLong("expiryTimestamp") ?: 0L
                    val regVal = doc.getLong("registrationTimestamp") ?: 0L
                    val lastActiveVal = doc.getLong("lastActiveTimestamp") ?: 0L
                    val isAdminVal = isSuperAdmin || roleVal == "admin" || (doc.getBoolean("isAdmin") ?: false) || (doc.getBoolean("dasmo_isAdmin") ?: false)

                    UserAccount(
                        email = email,
                        deviceId = deviceIdVal,
                        deviceModel = deviceModelVal,
                        isApproved = isApprovedVal || statusVal == "approved" || isAdminVal,
                        isAdmin = isAdminVal,
                        role = if (isAdminVal) "admin" else roleVal,
                        status = if (isAdminVal) "approved" else statusVal,
                        currentSessionToken = sessionVal,
                        expiryTimestamp = expiryVal,
                        registrationTimestamp = regVal,
                        lastActiveTimestamp = lastActiveVal,
                        appSource = doc.getString("appSource") ?: "DASMO App"
                    )
                }

                allUsers = list

                // Trigger local notification for new pending access requests after initial load
                if (!isInitialLoad) {
                    for (change in snapshot.documentChanges) {
                        if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                            val email = change.document.id
                            val status = change.document.getString("status") ?: "pending"
                            if (status == "pending" && email != "subhojitpaul26042004@gmail.com") {
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
                .setContentText("User '$email' requested access.")
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
                val snapshot = db.collection("dasmo_photo_print_users").get().await()
                allUsers = snapshot.documents.mapNotNull { doc ->
                    val email = doc.getString("email")?.takeIf { it.isNotBlank() } ?: doc.id
                    val isSuperAdmin = email == "subhojitpaul26042004@gmail.com"
                    val isApprovedVal = doc.getBoolean("dasmo_isApproved") ?: doc.getBoolean("isApproved") ?: false
                    val statusVal = doc.getString("dasmo_status") ?: doc.getString("status") ?: "pending"
                    val roleVal = doc.getString("dasmo_role") ?: doc.getString("role") ?: if (isSuperAdmin) "admin" else "user"
                    val deviceIdVal = doc.getString("dasmo_deviceId") ?: doc.getString("deviceId") ?: ""
                    val deviceModelVal = doc.getString("deviceModel") ?: ""
                    val sessionVal = doc.getString("currentSessionToken") ?: ""
                    val expiryVal = doc.getLong("expiryTimestamp") ?: 0L
                    val regVal = doc.getLong("registrationTimestamp") ?: 0L
                    val lastActiveVal = doc.getLong("lastActiveTimestamp") ?: 0L
                    val isAdminVal = isSuperAdmin || roleVal == "admin" || (doc.getBoolean("isAdmin") ?: false)

                    UserAccount(
                        email = email,
                        deviceId = deviceIdVal,
                        deviceModel = deviceModelVal,
                        isApproved = isApprovedVal || statusVal == "approved" || isAdminVal,
                        isAdmin = isAdminVal,
                        role = if (isAdminVal) "admin" else roleVal,
                        status = if (isAdminVal) "approved" else statusVal,
                        currentSessionToken = sessionVal,
                        expiryTimestamp = expiryVal,
                        registrationTimestamp = regVal,
                        lastActiveTimestamp = lastActiveVal
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun approveUser(email: String) {
        viewModelScope.launch {
            try {
                val trimmed = email.trim().lowercase()
                db.collection("dasmo_photo_print_users").document(trimmed).update(
                    mapOf(
                        "isApproved" to true,
                        "dasmo_isApproved" to true,
                        "status" to "approved",
                        "dasmo_status" to "approved"
                    )
                ).await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun rejectUser(email: String) {
        viewModelScope.launch {
            try {
                val trimmed = email.trim().lowercase()
                db.collection("dasmo_photo_print_users").document(trimmed).update(
                    mapOf(
                        "isApproved" to false,
                        "dasmo_isApproved" to false,
                        "status" to "rejected",
                        "dasmo_status" to "rejected"
                    )
                ).await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleUserApproval(email: String, currentStatus: Boolean) {
        if (currentStatus) rejectUser(email) else approveUser(email)
    }

    fun updateUserRole(email: String, newRole: String) {
        viewModelScope.launch {
            try {
                val isAdmin = newRole == "admin"
                db.collection("dasmo_photo_print_users").document(email.trim().lowercase()).update(
                    mapOf(
                        "role" to newRole,
                        "dasmo_role" to newRole,
                        "isAdmin" to isAdmin,
                        "dasmo_isAdmin" to isAdmin
                    )
                ).await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateUserExpiry(email: String, expiryTimestamp: Long) {
        viewModelScope.launch {
            try {
                db.collection("dasmo_photo_print_users").document(email.trim().lowercase()).update("expiryTimestamp", expiryTimestamp).await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun createUserManually(email: String, role: String, status: String, expiryTimestamp: Long = 0L) {
        viewModelScope.launch {
            try {
                val trimmedEmail = email.trim().lowercase()
                val isApproved = status == "approved"
                val isAdmin = role == "admin"
                val user = mapOf(
                    "email" to trimmedEmail,
                    "role" to role,
                    "dasmo_role" to role,
                    "status" to status,
                    "dasmo_status" to status,
                    "isApproved" to isApproved,
                    "dasmo_isApproved" to isApproved,
                    "isAdmin" to isAdmin,
                    "dasmo_isAdmin" to isAdmin,
                    "expiryTimestamp" to expiryTimestamp,
                    "registrationTimestamp" to System.currentTimeMillis(),
                    "appSource" to "Admin Pre-approval"
                )
                db.collection("dasmo_photo_print_users").document(trimmedEmail).set(user, SetOptions.merge()).await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Unbinds the registered device from this account.
     * Use when a user legitimately changes phones.
     */
    fun revokeDevice(email: String) {
        viewModelScope.launch {
            try {
                val trimmed = email.trim().lowercase()
                db.collection("dasmo_photo_print_users").document(trimmed).update(
                    mapOf(
                        "deviceId" to "",
                        "dasmo_deviceId" to "",
                        "deviceModel" to "",
                        "status" to "pending",
                        "dasmo_status" to "pending",
                        "isApproved" to false,
                        "dasmo_isApproved" to false,
                        "currentSessionToken" to ""
                    )
                ).await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteUser(email: String) {
        viewModelScope.launch {
            try {
                db.collection("dasmo_photo_print_users").document(email.trim().lowercase()).delete().await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun formatTimestamp(timeMs: Long): String {
        return java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(timeMs))
    }

    override fun onCleared() {
        super.onCleared()
        stopListeningUsers()
        stopSessionObserver()
    }
}
