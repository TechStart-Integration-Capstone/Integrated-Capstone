import os, sys, zipfile, subprocess, shutil

JAR_PATH = r"c:\Users\JFV83814\.gemini\antigravity-ide\scratch\FSE-Capstone\microservices\notification-service\target\notification-service.jar"
SRC_FILE = r"c:\Users\JFV83814\.gemini\antigravity-ide\scratch\FSE-Capstone\microservices\notification-service\src\main\java\com\bank\notification\service\NotificationDispatcher.java"
TMP_DIR = r"c:\Users\JFV83814\.gemini\antigravity-ide\scratch\FSE-Capstone\scratch\tmp_jar"

if os.path.exists(TMP_DIR):
    shutil.rmtree(TMP_DIR)
os.makedirs(TMP_DIR, exist_ok=True)

print("Extracting existing notification-service.jar...")
with zipfile.ZipFile(JAR_PATH, 'r') as z:
    z.extractall(TMP_DIR)

lib_dir = os.path.join(TMP_DIR, "BOOT-INF", "lib")
classes_dir = os.path.join(TMP_DIR, "BOOT-INF", "classes")

classpath_entries = [classes_dir]
for f in os.listdir(lib_dir):
    if f.endswith(".jar"):
        classpath_entries.append(os.path.join(lib_dir, f))

cp_str = ";".join(classpath_entries)

print("Compiling updated NotificationDispatcher.java with javac (--release 17)...")
cmd = ["javac", "--release", "17", "-cp", cp_str, "-d", classes_dir, SRC_FILE]
res = subprocess.run(cmd, capture_output=True, text=True)
if res.returncode != 0:
    print("Compilation failed:")
    print(res.stderr)
    print(res.stdout)
    sys.exit(1)

print("Compilation successful! Repacking jar...")
backup_jar = JAR_PATH + ".bak"
if not os.path.exists(backup_jar):
    shutil.copy2(JAR_PATH, backup_jar)

# Repack jar preserving manifest
manifest_path = os.path.join(TMP_DIR, "META-INF", "MANIFEST.MF")
with zipfile.ZipFile(JAR_PATH, 'w', zipfile.ZIP_DEFLATED) as z_out:
    for root, dirs, files in os.walk(TMP_DIR):
        for file in files:
            full_path = os.path.join(root, file)
            rel_path = os.path.relpath(full_path, TMP_DIR).replace("\\", "/")
            z_out.write(full_path, rel_path)

print(f"Successfully rebuilt {JAR_PATH} (size: {os.path.getsize(JAR_PATH)} bytes)")
