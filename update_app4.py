import re

with open('/app/applet/app/src/main/java/com/example/PassportPhotoApp.kt', 'r') as f:
    content = f.read()

new_content = content.replace('try {', '''try {
            com.google.firebase.firestore.FirebaseFirestore.getInstance().clearPersistence()
''')

with open('/app/applet/app/src/main/java/com/example/PassportPhotoApp.kt', 'w') as f:
    f.write(new_content)
