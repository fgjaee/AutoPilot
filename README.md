# AutoPilot Native v0.5 — Unified Android automation hub

AutoPilot is **one native Android app**, with **one shared LSPosed entry point** dispatching to app-specific adapters. Each saved automation is a workflow, **not a separate APK**.

## Integrated
- Meijer mPerks: passive network observer verified on a real device, POST /offers/Clip and HTTP 200. Captures redacted request shape, not raw values. Clip All is **not** implemented yet.
- Achievers / Workday / SmartSense: discovery slots only. Actual app packages and integrations must be verified before activating hooks.
- Core: Android Accessibility, KernelSU/root, scheduled saved workflows, optional AI provider.

## LSPosed
AutoPilotBridge is the *only* Xposed entry point. Add each new app's package to the existing AutoPilot module's LSPosed scope **once**, then use workflows without new APKs or HMA entries. Meijer remains the only currently implemented adapter.

## Signing / upgrades
The installed v0.3 APK has a different certificate from v0.4+. **Do not uninstall v0.3 just to try updates.** GitHub Actions now refuses to generate a new debug key if the previously cached development signing key is missing. The cached debug key is temporary, not production signing infrastructure; see docs/SIGNING.md.

One-time signature migration from v0.3 may be possible with a compatible CorePatch setup. Until verified, keep your current installation and configuration intact.
