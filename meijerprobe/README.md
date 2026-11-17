# AutoPilot Meijer Probe — Add-on for existing v0.3

**Keep AutoPilot v0.3 installed. Do not uninstall or clear any app data.**

The v0.3 and v0.4 APKs were built with different ephemeral debug certificates. Android cannot upgrade them in place. This separate minimal LSPosed module does not replace or modify your existing AutoPilot installation, HMA settings, or KernelSU root approval.

## Setup (one-time LSPosed step)
1. Install `meijerprobe-debug.apk`. No new root authorization or HMA exception is needed.
2. In LSPosed Manager > Modules, enable **AutoPilot Meijer Probe**, scoped **only to Meijer** (`com.meijer.mobile.meijer`). Keep the existing AutoPilot module enabled.
3. Force-stop Meijer, then reopen it and clip **one** coupon normally.
4. Open your existing **AutoPilot v0.3** home-screen app and use its original **Read last Meijer clip request trace · root** button.
5. Send the output lines containing `PROBE_ADDON_V1`, `body_shape=`, or `status=200`. The existing app reads the same log tag (`AutoPilotMeijer`).

The add-on passively observes the shape of small JSON requests, logging field names and types only; no raw values, credentials, coupon IDs, or tokens. `body_shape=skipped_...` indicates an unsupported body type rather than an error.

This is instrumentation, **not** a Clip All implementation.
