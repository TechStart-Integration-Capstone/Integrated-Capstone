import os, zipfile, shutil

ORIG_JAR = r"c:\Users\JFV83814\.gemini\antigravity-ide\scratch\FSE-Capstone\microservices\notification-service\target\notification-service.jar.bak"
TARGET_JAR = r"c:\Users\JFV83814\.gemini\antigravity-ide\scratch\FSE-Capstone\microservices\notification-service\target\notification-service.jar"
COMPILED_DIR = r"c:\Users\JFV83814\.gemini\antigravity-ide\scratch\FSE-Capstone\scratch\tmp_jar\BOOT-INF\classes"

# Find all compiled class files for NotificationDispatcher
class_files = {}
for root, dirs, files in os.walk(COMPILED_DIR):
    for f in files:
        if f.startswith("NotificationDispatcher"):
            full_path = os.path.join(root, f)
            zip_entry = "BOOT-INF/classes/" + os.path.relpath(full_path, COMPILED_DIR).replace("\\", "/")
            class_files[zip_entry] = full_path

print("Class files to patch:")
for k, v in class_files.items():
    print(f"  {k} -> {v}")

tmp_new_jar = TARGET_JAR + ".tmp"
with zipfile.ZipFile(ORIG_JAR, 'r') as zin, zipfile.ZipFile(tmp_new_jar, 'w') as zout:
    for item in zin.infolist():
        if item.filename in class_files:
            continue
        zout.writestr(item, zin.read(item.filename))
    
    # Write the new compiled classes
    for zip_entry, local_file in class_files.items():
        with open(local_file, 'rb') as f:
            zout.writestr(zip_entry, f.read())

shutil.move(tmp_new_jar, TARGET_JAR)
print("Jar successfully patched with updated NotificationDispatcher classes!")
