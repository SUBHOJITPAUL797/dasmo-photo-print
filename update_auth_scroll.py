import re

with open('/app/applet/app/src/main/java/com/example/ui/screens/AuthScreens.kt', 'r') as f:
    content = f.read()

# Make the error column scrollable
old_col = '''        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {'''
        
new_col = '''        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp).verticalScroll(rememberScrollState())
        ) {'''

content = content.replace(old_col, new_col)
content = content.replace('import androidx.compose.foundation.layout.*', 'import androidx.compose.foundation.layout.*\nimport androidx.compose.foundation.verticalScroll\nimport androidx.compose.foundation.rememberScrollState')

with open('/app/applet/app/src/main/java/com/example/ui/screens/AuthScreens.kt', 'w') as f:
    f.write(content)
