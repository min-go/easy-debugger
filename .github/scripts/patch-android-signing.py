#!/usr/bin/env python3
"""Inject a release signingConfig into the Tauri-generated Android Gradle project.

Reads keystore.properties (written by the CI step) so `tauri android build --apk`
produces a signed, installable release APK.
"""
f = "src-tauri/gen/android/app/build.gradle.kts"
s = open(f).read()

# Add only the imports that are missing; the Tauri template already imports
# java.util.Properties, so adding it again causes a "conflicting import" error.
for _imp in ("java.io.FileInputStream", "java.util.Properties"):
    _line = "import " + _imp
    if _line not in s:
        s = _line + "\n" + s

sign = """
    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            val keystoreProperties = Properties()
            if (keystorePropertiesFile.exists()) {
                keystoreProperties.load(FileInputStream(keystorePropertiesFile))
            }
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["password"] as String
            storeFile = file(keystoreProperties["storeFile"] as String)
            storePassword = keystoreProperties["password"] as String
        }
    }
"""

if "signingConfigs {" not in s:
    s = s.replace("android {\n", "android {\n" + sign, 1)

if "signingConfig = signingConfigs.getByName(\"release\")" not in s:
    s = s.replace(
        'getByName("release") {',
        'getByName("release") {\n            signingConfig = signingConfigs.getByName("release")',
        1,
    )

open(f, "w").write(s)
print("patched", f)
