#!/usr/bin/env python3
from pathlib import Path
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
manifest=root/'app/src/main/AndroidManifest.xml'
ET.parse(manifest)
assert (root/'app/src/main/assets/xposed_init').read_text().strip()=='com.fgjaee.autopilot.MeijerNetworkHook'
java=(root/'app/src/main/java/com/fgjaee/autopilot/MeijerNetworkHook.java').read_text()
assert 'com.meijer.mobile.meijer' in java
assert 'hookAllMethods(type, "sendRequest"' in java
assert '"okhttp3.Request$Builder"' in java
assert '"okhttp3.Response$Builder"' in java
assert 'Log.i(TAG' in java
assert 'setResult(' not in java and 'p.args[' in java
assert 'getHeader' not in java and 'Authorization' not in java and 'writeTo(' not in java
assert 'responseBody' not in java
assert 'compileOnly("de.robv.android.xposed:api:82")' in (root/'app/build.gradle.kts').read_text()
assert 'Read last Meijer clip request trace' in (root/'app/src/main/java/com/fgjaee/autopilot/MainActivity.kt').read_text()
print('Meijer trace integration structural checks passed; APK compilation not performed')
