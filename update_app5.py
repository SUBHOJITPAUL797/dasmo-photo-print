import re

with open('/app/applet/app/src/main/java/com/example/PassportPhotoApp.kt', 'r') as f:
    content = f.read()

new_on_create = '''    override fun onCreate() {
        super.onCreate()
        
        try {
            // Must set settings before any other Firestore usage
            val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(com.google.firebase.firestore.MemoryCacheSettings.newBuilder().build())
                .build()
            com.google.firebase.firestore.FirebaseFirestore.getInstance().firestoreSettings = settings
            
            // Clear persistence to wipe out any bad cached project IDs
            com.google.firebase.firestore.FirebaseFirestore.getInstance().clearPersistence()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }'''

# Replace everything from override fun onCreate() to the end of the method
content = re.sub(r'override fun onCreate\(\) \{.*?\n    \}', new_on_create, content, flags=re.DOTALL)

with open('/app/applet/app/src/main/java/com/example/PassportPhotoApp.kt', 'w') as f:
    f.write(content)
