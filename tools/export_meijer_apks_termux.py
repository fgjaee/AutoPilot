#!/data/data/com.termux/files/usr/bin/python
"""Export the actual installed Meijer Android APK(s) for native integration research.
No private app data, login sessions or account files are accessed.
Run: python export_meijer_apks_termux.py
"""
import json, pathlib, shlex, shutil, subprocess, time, zipfile, os

PKG = "com.meijer.mobile.meijer"
OUT = pathlib.Path(os.environ.get("AUTOPILOT_OUTPUT", "/sdcard/Download/Meijer-installed-APKs.zip"))
SU = os.environ.get("AUTOPILOT_SU_BIN", "su")


def root(*args):
    command = " ".join(shlex.quote(x) for x in args)
    return subprocess.run([SU, "-c", command], text=True, capture_output=True, timeout=30)


result = root("pm", "path", PKG)
paths = [line.removeprefix("package:").strip() for line in result.stdout.splitlines() if line.startswith("package:")]
if not paths:
    raise SystemExit("Meijer app not found. Check root grant to Termux and that Meijer is installed. " + result.stderr[:200])

with zipfile.ZipFile(OUT, "w", compression=zipfile.ZIP_STORED, allowZip64=True) as bundle:
    meta = {"package": PKG, "count": len(paths), "captured_at": int(time.time()), "source": "pm path"}
    bundle.writestr("installed-package.json", json.dumps(meta, indent=2))
    for idx, path in enumerate(paths):
        entry = "apks/base.apk" if idx == 0 else f"apks/split-{idx}-{pathlib.Path(path).name}"
        cmd = f"cat {shlex.quote(path)}"
        process = subprocess.Popen([SU, "-c", cmd], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        try:
            with bundle.open(entry, "w", force_zip64=True) as dest:
                shutil.copyfileobj(process.stdout, dest, length=1024 * 128)
            error = process.stderr.read().decode("utf-8", errors="replace")
            rc = process.wait(timeout=30)
            if rc != 0:
                raise RuntimeError(f"Could not export {path}: {error[:200]}")
        finally:
            if process.poll() is None:
                process.kill()
            process.wait()
        print(f"Saved {entry}")
print(f"READY: {OUT} ({OUT.stat().st_size / (1024*1024):.1f} MB)")
