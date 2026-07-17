import re

with open('/app/applet/app/src/main/java/com/example/ui/screens/AuthScreens.kt', 'r') as f:
    content = f.read()

content = content.replace('text = "Access Denied"', 'text = "Connection Failed"')

with open('/app/applet/app/src/main/java/com/example/ui/screens/AuthScreens.kt', 'w') as f:
    f.write(content)
