import re

with open('/app/applet/app/src/main/java/com/example/ui/AuthViewModel.kt', 'r') as f:
    content = f.read()

# I will just write a new simple catch block using raw strings in kotlin
new_catch_1 = '''                val mainThread = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    authState = AuthState.Error("Network ping failed: ${e.message}")
                }'''

content = re.sub(r'                val mainThread = kotlinx.coroutines.withContext.*?\}', new_catch_1, content, flags=re.DOTALL)

new_catch_2 = '''                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    authState = AuthState.Error("Firestore error: ${e.message} \\n ${stackTrace.take(300).replace("\\n", " ")}")
                }'''

content = re.sub(r'                kotlinx.coroutines.withContext\(kotlinx\.coroutines\.Dispatchers\.Main\) \{\n                    authState = AuthState\.Error\("Firestore error:.*?\n                \}', new_catch_2, content, flags=re.DOTALL)

with open('/app/applet/app/src/main/java/com/example/ui/AuthViewModel.kt', 'w') as f:
    f.write(content)
