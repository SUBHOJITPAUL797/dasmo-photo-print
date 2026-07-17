import re

with open('/app/applet/app/src/main/java/com/example/ui/AuthViewModel.kt', 'r') as f:
    content = f.read()

# Add isOfflineBlocked state
if 'var isOfflineBlocked' not in content:
    content = content.replace('class AuthViewModel : ViewModel() {', '''class AuthViewModel : ViewModel() {
    var isOfflineBlocked by mutableStateOf(false)
        private set
''')

# Update verifySessionContinuity to use Source.SERVER and update isOfflineBlocked
old_verify = '''    fun verifySessionContinuity(email: String, currentSessionToken: String?, onSessionInvalidated: () -> Unit) {
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
    }'''

new_verify = '''    fun verifySessionContinuity(email: String, currentSessionToken: String?, onSessionInvalidated: () -> Unit) {
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
    }'''

content = content.replace(old_verify, new_verify)

with open('/app/applet/app/src/main/java/com/example/ui/AuthViewModel.kt', 'w') as f:
    f.write(content)
