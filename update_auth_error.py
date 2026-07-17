import re

with open('/app/applet/app/src/main/java/com/example/ui/AuthViewModel.kt', 'r') as f:
    content = f.read()

# Replace the network error catch block
old_catch = '''            } catch (e: Exception) {
                authState = AuthState.Error("Network error: ${e.message}")
            }'''
            
new_catch = '''            } catch (e: Exception) {
                e.printStackTrace()
                val stackTrace = android.util.Log.getStackTraceString(e)
                authState = AuthState.Error("Network error: ${e.message}\\n\\nDetails:\\n${stackTrace.take(500)}")
            }'''

content = content.replace(old_catch, new_catch)

with open('/app/applet/app/src/main/java/com/example/ui/AuthViewModel.kt', 'w') as f:
    f.write(content)
