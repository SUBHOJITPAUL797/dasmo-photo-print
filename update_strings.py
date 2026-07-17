import re

with open('/app/applet/app/src/main/res/values/strings.xml', 'r') as f:
    content = f.read()

new_content = content.replace('</resources>', '''    <string name="google_app_id" translatable="false">1:490633569828:android:a0715d9497435b5e86dab1</string>
    <string name="google_api_key" translatable="false">AIzaSyDjVqas7AANFiZLpVUUuxqBXAPwRIdAQzM</string>
    <string name="project_id" translatable="false">dasmo-scanner-android</string>
    <string name="gcm_defaultSenderId" translatable="false">490633569828</string>
    <string name="firebase_database_url" translatable="false">https://dasmo-scanner-android.firebaseio.com</string>
    <string name="google_storage_bucket" translatable="false">dasmo-scanner-android.firebasestorage.app</string>
</resources>''')

with open('/app/applet/app/src/main/res/values/strings.xml', 'w') as f:
    f.write(new_content)
