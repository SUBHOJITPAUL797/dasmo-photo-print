import re

with open('/app/applet/app/src/main/java/com/example/ui/screens/HomeScreen.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
skip = False
for i, line in enumerate(lines):
    if line.strip() == "modifier = Modifier.fillMaxWidth(),":
        if i + 1 < len(lines) and lines[i+1].strip() == "horizontalArrangement = Arrangement.SpaceBetween,":
            # check if line 93 (header) -> keep it
            if "Row(" in lines[i-1]:
                # let's look at what is after
                if i+3 < len(lines) and "verticalAlignment = Alignment.CenterVertically" in lines[i+3] and "padding" not in lines[i+3] and "modifier =" not in lines[i+4]:
                    new_lines.append(line)
                    continue
            
            # otherwise skip this and the next line
            skip = True
            continue
    if skip and line.strip() == "horizontalArrangement = Arrangement.SpaceBetween,":
        skip = False
        continue
    new_lines.append(line)

with open('/app/applet/app/src/main/java/com/example/ui/screens/HomeScreen.kt', 'w') as f:
    f.writelines(new_lines)

