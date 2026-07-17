import re

# 1. Update AndroidManifest.xml
manifest_path = '/app/applet/app/src/main/AndroidManifest.xml'
with open(manifest_path, 'r') as f:
    manifest = f.read()
if 'android.permission.ACCESS_NETWORK_STATE' not in manifest:
    manifest = manifest.replace(
        '<uses-permission android:name="android.permission.INTERNET" />',
        '<uses-permission android:name="android.permission.INTERNET" />\n    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />'
    )
    with open(manifest_path, 'w') as f:
        f.write(manifest)

# 2. Update AuthViewModel.kt
viewmodel_path = '/app/applet/app/src/main/java/com/example/ui/AuthViewModel.kt'
with open(viewmodel_path, 'r') as f:
    vm_content = f.read()

imports = """import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.google.firebase.firestore.ListenerRegistration
"""
if 'import android.net.ConnectivityManager' not in vm_content:
    vm_content = vm_content.replace('import android.os.Build', imports + 'import android.os.Build')

old_verify = """    fun verifySessionContinuity(email: String, currentSessionToken: String?, onSessionInvalidated: () -> Unit) {
        if (currentSessionToken.isNullOrBlank() || email.isBlank()) return
        
        viewModelScope.launch {
            try {
                // CRITICAL: Use Source.SERVER to bypass local cache and enforce real-time check
                val snapshot = db.collection("users").document(email).get(com.google.firebase.firestore.Source.SERVER).await()
                val user = snapshot.toObject(UserAccount::class.java)
                if (user != null && user.currentSessionToken != currentSessionToken) {
                    // Session token mismatch! Another login occurred.
                    onSessionInvalidated()
                } else if (user != null && user.status != "approved" && user.role != "admin") {
                     // Status changed to rejected/pending while logged in
                     onSessionInvalidated()
                } else {
                     // Successfully verified with server
                     isOfflineBlocked = false
                }
            } catch (e: Exception) {
                // Network error, offline, or server unreachable
                isOfflineBlocked = true
            }
        }
    }"""

new_methods = """    private var sessionListener: ListenerRegistration? = null

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
                    } else if (user.status != "approved" && user.role != "admin") {
                        onSessionInvalidated()
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
    }"""

if 'verifySessionContinuity' in vm_content:
    vm_content = vm_content.replace(old_verify, new_methods)
    with open(viewmodel_path, 'w') as f:
        f.write(vm_content)

# 3. Update MainActivity.kt
main_path = '/app/applet/app/src/main/java/com/example/MainActivity.kt'
with open(main_path, 'r') as f:
    main_content = f.read()

if 'authViewModel.monitorNetwork(context)' not in main_content:
    main_content = main_content.replace(
        'authViewModel.checkLogin(context, savedEmail, savedSession)',
        'authViewModel.monitorNetwork(context)\n                    authViewModel.checkLogin(context, savedEmail, savedSession)'
    )

old_loop = """                // Periodic session verification (e.g. if someone logs in elsewhere)
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
                }"""

new_loop = """                // Real-time session verification (Cost-efficient Snapshot Listener)
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
                }"""

if 'while (true)' in main_content:
    main_content = main_content.replace(old_loop, new_loop)
    with open(main_path, 'w') as f:
        f.write(main_content)

