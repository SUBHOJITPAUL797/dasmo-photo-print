import re

with open('/app/applet/app/src/main/java/com/example/PassportPhotoApp.kt', 'r') as f:
    content = f.read()

old_init = '''            val options = com.google.firebase.FirebaseOptions.Builder()
                .setProjectId("gen-lang-client-0829145210")
                .setApplicationId("1:95522304988:web:fe8b95cf9cb0969200d519")
                .setApiKey("AIzaSyCz7sN_v7unPt7LLhoqH4b2NhxSXK-kZfU")
                .build()'''

new_init = '''            // Ensure we use the correct Project ID and API Key from Secrets
            // Otherwise, Firestore will reject the connection and appear "offline"
            val projectId = BuildConfig.FIREBASE_PROJECT_ID
            val appId = BuildConfig.FIREBASE_APP_ID
            val apiKey = BuildConfig.FIREBASE_API_KEY
            
            val options = com.google.firebase.FirebaseOptions.Builder()
                .setProjectId(projectId)
                .setApplicationId(appId)
                .setApiKey(apiKey)
                .build()'''

content = content.replace(old_init, new_init)

with open('/app/applet/app/src/main/java/com/example/PassportPhotoApp.kt', 'w') as f:
    f.write(content)
