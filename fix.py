with open('/app/applet/app/src/main/java/com/example/ui/screens/HomeScreen.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if line.strip() == "horizontalArrangement = Arrangement.SpaceBetween," or line.strip() == "modifier = Modifier.fillMaxWidth(),":
        # we will skip these if they are inside the FAB or inside the list item
        # wait, we can just look for the text DASMO PHOTO PRINT and replace the header
        pass

# simpler approach: just find and replace using simple strings
content = "".join(lines)
content = content.replace("                Row(\n                        modifier = Modifier.fillMaxWidth(),\n                        horizontalArrangement = Arrangement.SpaceBetween,\n                    modifier = Modifier.padding(horizontal = 16.dp),\n                    verticalAlignment = Alignment.CenterVertically\n                ) {", 
"                Row(\n                    modifier = Modifier.padding(horizontal = 16.dp),\n                    verticalAlignment = Alignment.CenterVertically\n                ) {")

content = content.replace("                                    Row(\n                        modifier = Modifier.fillMaxWidth(),\n                        horizontalArrangement = Arrangement.SpaceBetween,\n                                        verticalAlignment = Alignment.CenterVertically,\n                                        modifier = Modifier.padding(top = 4.dp)\n                                    ) {", 
"                                    Row(\n                                        verticalAlignment = Alignment.CenterVertically,\n                                        modifier = Modifier.padding(top = 4.dp)\n                                    ) {")

import re
header_search = r'                    Row\(\n                        modifier = Modifier\.fillMaxWidth\(\),\n                        horizontalArrangement = Arrangement\.SpaceBetween,\n                        verticalAlignment = Alignment\.CenterVertically\n                    \) \{\n                        Box\(\n                            modifier = Modifier\n                                \.size\(48\.dp\)\n                                \.clip\(CircleShape\)\n                                \.background\(MaterialTheme\.colorScheme\.primary\),\n                            contentAlignment = Alignment\.Center\n                        \) \{\n                            Icon\(\n                                imageVector = Icons\.Default\.Print,\n                                contentDescription = null,\n                                tint = MaterialTheme\.colorScheme\.onPrimary,\n                                modifier = Modifier\.size\(24\.dp\)\n                            \)\n                        \}\n                        Spacer\(modifier = Modifier\.width\(16\.dp\)\)\n                        Column \{\n                            Text\(\n                                text = "DASMO PHOTO PRINT",\n                                style = MaterialTheme\.typography\.headlineSmall,\n                                fontWeight = FontWeight\.Black,\n                                color = MaterialTheme\.colorScheme\.onSurface\n                            \)\n                            Text\(\n                                text = "Create print-ready layouts of passport, visa, and custom photo dimensions on A4 paper with precision\.",\n                                style = MaterialTheme\.typography\.bodyMedium,\n                                color = MaterialTheme\.colorScheme\.onSurfaceVariant\n                            \)\n                        \}\n                    \}'

header_replace = '''                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Print,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "DASMO",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "PHOTO PRINT",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row {
                            if (isAdmin) {
                                IconButton(onClick = onAdminClicked) {
                                    Icon(Icons.Default.AdminPanelSettings, contentDescription = "Admin", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            IconButton(onClick = onLogoutClicked) {
                                Icon(Icons.Default.Logout, contentDescription = "Logout", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }'''

content = re.sub(header_search, header_replace, content)

with open('/app/applet/app/src/main/java/com/example/ui/screens/HomeScreen.kt', 'w') as f:
    f.write(content)

