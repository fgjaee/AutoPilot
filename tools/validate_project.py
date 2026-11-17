#!/usr/bin/env python3
"""Quick project structure checks; NOT a substitute for an Android compilation."""
import pathlib
import re
import xml.etree.ElementTree as ET

root = pathlib.Path(__file__).resolve().parents[1]
manifest = root / 'app/src/main/AndroidManifest.xml'
access = root / 'app/src/main/res/xml/accessibility_service.xml'
for path in (manifest, access, root / 'app/src/main/res/values/strings.xml'):
    ET.parse(path)
    print('XML OK:', path.relative_to(root))

files = list((root / 'app/src/main/java').rglob('*.kt'))
assert len(files) >= 6, 'Missing Kotlin source files'
expected = {
    'AutoPilotAccessibilityService.kt', 'DeviceTools.kt', 'ProviderSettings.kt',
    'AgentClient.kt', 'MainActivity.kt', 'WorkflowStore.kt', 'WorkflowWorker.kt'
}
assert expected <= {p.name for p in files}, 'Missing core modules'

tools = (root / 'app/src/main/java/com/fgjaee/autopilot/DeviceTools.kt').read_text()
agent = (root / 'app/src/main/java/com/fgjaee/autopilot/AgentClient.kt').read_text()
for tool in ('inspect_ui','tap_text','tap_element','scroll','launch_app','root_shell','type_text'):
    assert f'"{tool}"' in tools, tool
    assert f'"{tool}"' in agent, tool
assert 'ProcessBuilder("su", "-c", command)' in tools
assert 'tool_calls' in agent
assert 'androidx.work' in (root / 'app/build.gradle.kts').read_text()
assert 'actions/upload-artifact' in (root / '.github/workflows/android.yml').read_text()
assert 'api_key' not in (root / 'gradle.properties').read_text().lower()
print('Core modules / tool definitions / build workflow: OK')
print(f'{len(files)} Kotlin files validated structurally (Android compile not performed)')
