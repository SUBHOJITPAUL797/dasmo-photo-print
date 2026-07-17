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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class AuthViewModel : ViewModel() {
    var isOfflineBlocked by mutableStateOf(false)
        private set
    
    private val db = FirebaseFirestore.getInstance(com.google.firebase.FirebaseApp.getInstance(), "default")
    
    var authState by mutableStateOf<AuthState>(AuthState.Loading)
        private set
        
    var currentUser by mutableStateOf<UserAccount?>(null)
        private set
        
    var allUsers by mutableStateOf<List<UserAccount>>(emptyList())
        private set
        
    private var usersListener: com.google.firebase.firestore.ListenerRegistration? = null

    @SuppressLint("HardwareIds")
    private fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
    }

    private fun getDeviceModel(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}"
    }

    fun checkLogin(context: Context, savedEmail: String?, savedSessionToken: String? = null) {
        if (savedEmail.isNullOrBlank()) {
            authState = AuthState.Unauthenticated
            return
        }
        login(context, savedEmail, savedSessionToken)
    }

    fun login(context: Context, email: String, existingSessionToken: String? = null, onSessionCreated: ((String) -> Unit)? = null) {
        val trimmedEmail = email.trim().lowercase()
        if (trimmedEmail.isEmpty()) return
        
        authState = AuthState.Loading
        val deviceId = getDeviceId(context)
        val deviceModel = getDeviceModel()
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Test network connection to firestore endpoint
                try {
                    val url = java.net.URL("https://firestore.googleapis.com")
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.connectTimeout = 3000
                    connection.readTimeout = 3000
                    connection.requestMethod = "GET"
                    connection.responseCode
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        authState = AuthState.Error("Network Error: Cannot reach Google servers. Check your internet connection. Details: ${e.message}")
                    }
                    return@launch
                }
                
                val docRef = db.collection("users").document(trimmedEmail)
                val snapshot = docRef.get(com.google.firebase.firestore.Source.SERVER).await()
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (snapshot.exists()) {
                        val user = snapshot.toObject(UserAccount::class.java)
                        if (user != null) {
                            val isAdmin = trimmedEmail == "subhojitpaul26042004@gmail.com"
                            if (user.deviceId.isNotEmpty() && user.deviceId != deviceId) {
                                authState = AuthState.Error("Security Alert: Account is already bound to another device (${user.deviceModel}). Access denied from this device.")
                            } else {
                                val newSessionToken = UUID.randomUUID().toString()
                                val updatedUser = user.copy(
                                    deviceId = deviceId,
                                    deviceModel = deviceModel,
                                    currentSessionToken = newSessionToken,
                                    role = if (isAdmin) "admin" else user.role,
                                    status = if (isAdmin) "approved" else user.status
                                )
                                
                                when (updatedUser.status) {
                                    "approved", "pending" -> {
                                        docRef.set(updatedUser)
                                        currentUser = updatedUser
                                        authState = AuthState.Authenticated(updatedUser)
                                        onSessionCreated?.invoke(newSessionToken)
                                    }
                                    "rejected" -> authState = AuthState.Error("Access Revoked by Administrator.")
                                }
                            }
                        } else {
                            authState = AuthState.Error("Invalid user data.")
                        }
                    } else {
                        // Create new account
                        val isAdmin = trimmedEmail == "subhojitpaul26042004@gmail.com"
                        val newSessionToken = UUID.randomUUID().toString()
                        val newUser = UserAccount(
                            email = trimmedEmail,
                            deviceId = deviceId,
                            deviceModel = deviceModel,
                            role = if (isAdmin) "admin" else "user",
                            status = if (isAdmin) "approved" else "pending",
                            currentSessionToken = newSessionToken
                        )
                        docRef.set(newUser)
                        currentUser = newUser
                        authState = AuthState.Authenticated(newUser)
                        onSessionCreated?.invoke(newSessionToken)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val msg = e.message ?: ""
                    val isOffline = msg.contains("offline", ignoreCase = true)
                    val hint = if (isOffline) {
                        "\n\n⚠️ NOTE: This error usually means you have not created/enabled the 'Firestore Database' yet in your Firebase Console for the project 'dasmo-scanner-android'. Please open your Firebase Console, click on 'Firestore Database', and click 'Create Database'!"
                    } else ""
                    authState = AuthState.Error("Database Error: ${e.message}$hint\n\nMake sure your Firebase project is correctly configured and rules are set.")
                }
            }
        }
    }
    
    private var sessionListener: ListenerRegistration? = null

    fun startSessionObserver(email: String, currentToken: String, onSessionInvalidated: () -> Unit) {
        sessionListener?.remove()
        if (currentToken.isBlank() || email.isBlank()) return
        
        sessionListener = db.collection("users").document(email).addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            
            if (snapshot != null && snapshot.exists()) {
                val user = snapshot.toObject(UserAccount::class.java)
                if (user != null) {
                    if (user.currentSessionToken != currentToken) {
                        onSessionInvalidated()
                    } else if (user.status == "rejected") {
                        onSessionInvalidated()
                    } else {
                        currentUser = user
                        authState = AuthState.Authenticated(user)
                    }
                }
            }
        }
    }

    fun stopSessionObserver() {
        sessionListener?.remove()
        sessionListener = null
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
        currentUser = null
        authState = AuthState.Unauthenticated
    }

    // Admin Functions
    fun startListeningUsers(context: Context) {
        usersListener?.remove()
        var isInitialLoad = true
        usersListener = db.collection("users").addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null) {
                val newUsers = snapshot.toObjects(UserAccount::class.java)
                allUsers = newUsers

                // Trigger local notification for new pending access requests after initial load
                if (!isInitialLoad) {
                    for (change in snapshot.documentChanges) {
                        if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                            val user = change.document.toObject(UserAccount::class.java)
                            if (user.status == "pending") {
                                triggerLocalNotification(context, user.email)
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
                    description = "Alerts for new registration and access requests"
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
                .setContentText("User '$email' is waiting for access approval.")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
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
                val snapshot = db.collection("users").get().await()
                allUsers = snapshot.toObjects(UserAccount::class.java)
            } catch (e: Exception) {
                // Ignore for now
            }
        }
    }

    fun updateUserStatus(email: String, newStatus: String) {
        viewModelScope.launch {
            try {
                db.collection("users").document(email).update("status", newStatus).await()
            } catch (e: Exception) {
                // Ignore for now
            }
        }
    }

    fun updateUserRole(email: String, newRole: String) {
        viewModelScope.launch {
            try {
                db.collection("users").document(email).update("role", newRole).await()
            } catch (e: Exception) {
                // Ignore for now
            }
        }
    }

    fun updateUserExpiry(email: String, expiryTimestamp: Long) {
        viewModelScope.launch {
            try {
                db.collection("users").document(email).update("expiryTimestamp", expiryTimestamp).await()
            } catch (e: Exception) {
                // Ignore for now
            }
        }
    }

    fun createUserManually(email: String, role: String, status: String, expiryTimestamp: Long = 0L) {
        viewModelScope.launch {
            try {
                val trimmedEmail = email.trim().lowercase()
                val user = UserAccount(
                    email = trimmedEmail,
                    role = role,
                    status = status,
                    expiryTimestamp = expiryTimestamp
                )
                db.collection("users").document(trimmedEmail).set(user).await()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    fun revokeDevice(email: String) {
        viewModelScope.launch {
            try {
                db.collection("users").document(email).update(
                    "deviceId", "",
                    "deviceModel", "",
                    "status", "pending",
                    "currentSessionToken", ""
                ).await()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopListeningUsers()
    }
}

sealed class AuthState {
    object Loading : AuthState()
    object Unauthenticated : AuthState()
    object PendingApproval : AuthState()
    data class Authenticated(val user: UserAccount) : AuthState()
    data class Error(val message: String) : AuthState()
}
