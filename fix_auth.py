import re

with open('/app/applet/app/src/main/java/com/example/domain/model/UserAccount.kt', 'r') as f:
    content = f.read()

new_model = '''package com.example.domain.model

data class UserAccount(
    val email: String = "",
    val deviceId: String = "",
    val deviceModel: String = "",
    val role: String = "user", // "admin" or "user"
    val status: String = "pending", // "pending", "approved", "rejected"
    val currentSessionToken: String = "" // For single-session enforcement
)'''

with open('/app/applet/app/src/main/java/com/example/domain/model/UserAccount.kt', 'w') as f:
    f.write(new_model)

with open('/app/applet/app/src/main/java/com/example/ui/AuthViewModel.kt', 'r') as f:
    content = f.read()

# Update AuthViewModel to handle deviceModel and Session Token
# We will just write a new AuthViewModel entirely to be safe and thorough.
new_auth_vm = '''package com.example.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.UserAccount
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class AuthViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    
    var authState by mutableStateOf<AuthState>(AuthState.Loading)
        private set
        
    var currentUser by mutableStateOf<UserAccount?>(null)
        private set
        
    var allUsers by mutableStateOf<List<UserAccount>>(emptyList())
        private set
        
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
        
        // Use the saved session token to verify active session
        login(context, savedEmail, savedSessionToken)
    }

    fun login(context: Context, email: String, existingSessionToken: String? = null, onSessionCreated: ((String) -> Unit)? = null) {
        val trimmedEmail = email.trim().lowercase()
        if (trimmedEmail.isEmpty()) return
        
        authState = AuthState.Loading
        val deviceId = getDeviceId(context)
        val deviceModel = getDeviceModel()
        
        viewModelScope.launch {
            try {
                val docRef = db.collection("users").document(trimmedEmail)
                val snapshot = docRef.get().await()
                
                if (snapshot.exists()) {
                    val user = snapshot.toObject(UserAccount::class.java)
                    if (user != null) {
                        if (user.deviceId.isNotEmpty() && user.deviceId != deviceId) {
                            authState = AuthState.Error("Security Alert: Account is already bound to another device (${user.deviceModel}). Access denied from this device.")
                        } else {
                            // Single Session Enforcement check
                            // If they are providing an existing token, check it. If not, generate a new one.
                            // However, if we're doing a fresh login (no existing session token), we take over the session.
                            val newSessionToken = UUID.randomUUID().toString()
                            val updatedUser = user.copy(
                                deviceId = deviceId, 
                                deviceModel = deviceModel,
                                currentSessionToken = newSessionToken
                            )
                            
                            when (user.status) {
                                "approved" -> {
                                    // Update session token in DB to invalidate other active sessions
                                    docRef.set(updatedUser).await()
                                    currentUser = updatedUser
                                    authState = AuthState.Authenticated(updatedUser)
                                    onSessionCreated?.invoke(newSessionToken)
                                }
                                "rejected" -> authState = AuthState.Error("Access Revoked by Administrator.")
                                else -> {
                                    // Make sure device info is saved even if pending
                                    docRef.set(updatedUser).await()
                                    authState = AuthState.PendingApproval
                                }
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
                    docRef.set(newUser).await()
                    currentUser = newUser
                    if (isAdmin) {
                        authState = AuthState.Authenticated(newUser)
                        onSessionCreated?.invoke(newSessionToken)
                    } else {
                        authState = AuthState.PendingApproval
                    }
                }
            } catch (e: Exception) {
                authState = AuthState.Error("Network error: ${e.message}")
            }
        }
    }
    
    fun verifySessionContinuity(email: String, currentSessionToken: String?, onSessionInvalidated: () -> Unit) {
        if (currentSessionToken.isNullOrBlank() || email.isBlank()) return
        
        viewModelScope.launch {
            try {
                val snapshot = db.collection("users").document(email).get().await()
                val user = snapshot.toObject(UserAccount::class.java)
                if (user != null && user.currentSessionToken != currentSessionToken) {
                    // Session token mismatch! Another login occurred.
                    onSessionInvalidated()
                } else if (user != null && user.status != "approved" && user.role != "admin") {
                     // Status changed to rejected/pending while logged in
                     onSessionInvalidated()
                }
            } catch (e: Exception) {
                // Ignore temporary network errors for continuity checks
            }
        }
    }
    
    fun logout() {
        currentUser = null
        authState = AuthState.Unauthenticated
    }

    // Admin Functions
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
                fetchAllUsers() // refresh list
            } catch (e: Exception) {
                // Ignore for now
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
                fetchAllUsers()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}

sealed class AuthState {
    object Loading : AuthState()
    object Unauthenticated : AuthState()
    object PendingApproval : AuthState()
    data class Authenticated(val user: UserAccount) : AuthState()
    data class Error(val message: String) : AuthState()
}
'''

with open('/app/applet/app/src/main/java/com/example/ui/AuthViewModel.kt', 'w') as f:
    f.write(new_auth_vm)

with open('/app/applet/app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    main_content = f.read()

# Update MainActivity to handle session token
main_content = main_content.replace('''                LaunchedEffect(Unit) {
                    val savedEmail = sharedPrefs.getString("email", null)
                    authViewModel.checkLogin(context, savedEmail)
                }''', '''                LaunchedEffect(Unit) {
                    val savedEmail = sharedPrefs.getString("email", null)
                    val savedSession = sharedPrefs.getString("session_token", null)
                    authViewModel.checkLogin(context, savedEmail, savedSession)
                }
                
                // Periodic session verification (e.g. if someone logs in elsewhere)
                LaunchedEffect(authViewModel.authState) {
                    if (authViewModel.authState is AuthState.Authenticated) {
                        val state = authViewModel.authState as AuthState.Authenticated
                        val savedSession = sharedPrefs.getString("session_token", null)
                        
                        // Small periodic check loop
                        while (true) {
                            kotlinx.coroutines.delay(10000) // Check every 10 seconds
                            authViewModel.verifySessionContinuity(state.user.email, savedSession) {
                                // Session invalidated! Log them out.
                                sharedPrefs.edit().remove("email").remove("session_token").apply()
                                authViewModel.logout()
                            }
                        }
                    }
                }''')

main_content = main_content.replace('''                            LoginScreen(
                                authViewModel = authViewModel,
                                onLoginSuccess = { email ->
                                    sharedPrefs.edit().putString("email", email).apply()
                                }
                            )''', '''                            LoginScreen(
                                authViewModel = authViewModel,
                                onLoginSuccess = { email, sessionToken ->
                                    sharedPrefs.edit()
                                        .putString("email", email)
                                        .putString("session_token", sessionToken)
                                        .apply()
                                }
                            )''')

with open('/app/applet/app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(main_content)
    
