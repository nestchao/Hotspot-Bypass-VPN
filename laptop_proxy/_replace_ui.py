
import pathlib

PATH = pathlib.Path(r"C:\Users\user\AndroidStudioProjects\HotspotBypassVPN\HotspotBypassVPN\laptop_proxy\main.py")
content = PATH.read_text("utf-8")
lines = content.split("\n")

# Find boundaries
app_start = None
main_end = None
for i, line in enumerate(lines):
    if line.startswith("class App:") and app_start is None:
        app_start = i
    if app_start is not None and "root.mainloop()" in line:
        main_end = i
        break

print(f"Replacing lines {app_start+1} through {main_end+1}")

# Read new code from separate file
new_code_path = pathlib.Path(r"C:\Users\user\AndroidStudioProjects\HotspotBypassVPN\HotspotBypassVPN\laptop_proxy\_new_ui_code.py")
new_code = new_code_path.read_text("utf-8")

before = "\n".join(lines[:app_start])
after = "\n".join(lines[main_end+1:])

new_content = before + "\n" + new_code + "\n" + after
PATH.write_text(new_content, "utf-8")
print("Done!")
