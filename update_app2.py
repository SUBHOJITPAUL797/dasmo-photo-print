import re

with open('/app/applet/app/src/main/java/com/example/PassportPhotoApp.kt', 'r') as f:
    content = f.read()

# Replace the onCreate block to just be super.onCreate()
old_on_create = '''    override fun onCreate() {
        super.onCreate()
        if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
            // Ensure we use the correct Project ID and API Key from Secrets
            // Otherwise, Firestore will reject the connection and appear "offline"
            val projectId = BuildConfig.FIREBASE_PROJECT_ID
            val appId = BuildConfig.FIREBASE_APP_ID
            val apiKey = BuildConfig.FIREBASE_API_KEY
            
            val options = com.google.firebase.FirebaseOptions.Builder()
                .setProjectId(projectId)
                .setApplicationId(appId)
                .setApiKey(apiKey)
                .build()
            com.google.firebase.FirebaseApp.initializeApp(this, options)
        }
    }'''

new_on_create = '''    override fun onCreate() {
        super.onCreate()
        // Firebase will auto-initialize from resources in strings.xml
    }'''

content = content.replace(old_on_create, new_on_create)

with open('/app/applet/app/src/main/java/com/example/PassportPhotoApp.kt', 'w') as f:
    f.write(content)
