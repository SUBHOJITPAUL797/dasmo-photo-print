import re

with open('/app/applet/app/src/main/java/com/example/ui/AuthViewModel.kt', 'r') as f:
    content = f.read()

new_login = '''    fun login(context: Context, email: String, existingSessionToken: String? = null, onSessionCreated: ((String) -> Unit)? = null) {
        val trimmedEmail = email.trim().lowercase()
        if (trimmedEmail.isEmpty()) return
        
        authState = AuthState.Loading
        val deviceId = getDeviceId(context)
        val deviceModel = getDeviceModel()
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Test network first
                val url = java.net.URL("https://firestore.googleapis.com")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.requestMethod = "GET"
                val code = connection.responseCode
                android.util.Log.d("AuthViewModel", "Ping to firestore.googleapis.com code: $code")
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    authState = AuthState.Error("Network ping failed: ${e.message}")
                }
                return@launch
            }
            
            try {
                val docRef = db.collection("users").document(trimmedEmail)
                val snapshot = docRef.get(com.google.firebase.firestore.Source.SERVER).await()
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (snapshot.exists()) {
                        val user = snapshot.toObject(UserAccount::class.java)
                        if (user != null) {
                            if (user.deviceId.isNotEmpty() && user.deviceId != deviceId) {
                                authState = AuthState.Error("Security Alert: Account is already bound to another device.")
                            } else {
                                val newSessionToken = UUID.randomUUID().toString()
                                val updatedUser = user.copy(
                                    deviceId = deviceId,
                                    deviceModel = deviceModel,
                                    currentSessionToken = newSessionToken
                                )
                                
                                when (user.status) {
                                    "approved" -> {
                                        docRef.set(updatedUser)
                                        currentUser = updatedUser
                                        authState = AuthState.Authenticated(updatedUser)
                                        onSessionCreated?.invoke(newSessionToken)
                                    }
                                    "rejected" -> authState = AuthState.Error("Access Revoked by Administrator.")
                                    else -> {
                                        docRef.set(updatedUser)
                                        authState = AuthState.PendingApproval
                                    }
                                }
                            }
                        } else {
                            authState = AuthState.Error("Invalid user data.")
                        }
                    } else {
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
                        if (isAdmin) {
                            authState = AuthState.Authenticated(newUser)
                            onSessionCreated?.invoke(newSessionToken)
                        } else {
                            authState = AuthState.PendingApproval
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val stackTrace = android.util.Log.getStackTraceString(e)
                val shortTrace = stackTrace.take(300).replace("\\n", " ")
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    authState = AuthState.Error("Firestore error: ${e.message} || Trace: $shortTrace")
                }
            }
        }
    }'''

content = re.sub(r'    fun login\(.*?    \}\n', new_login + "\n", content, flags=re.DOTALL)

with open('/app/applet/app/src/main/java/com/example/ui/AuthViewModel.kt', 'w') as f:
    f.write(content)
