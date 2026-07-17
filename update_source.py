import re

with open('/app/applet/app/src/main/java/com/example/ui/AuthViewModel.kt', 'r') as f:
    content = f.read()

content = content.replace('val snapshot = docRef.get().await()', 'val snapshot = docRef.get(com.google.firebase.firestore.Source.SERVER).await()')

with open('/app/applet/app/src/main/java/com/example/ui/AuthViewModel.kt', 'w') as f:
    f.write(content)
