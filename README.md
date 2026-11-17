# AutoPilot Native v0.4 — Meijer Clip request-shape observer

## Verified on device · November 17, 2026
- LSPosed observer attached to the installed Meijer process.
- One manual coupon clip generated POST /offers/Clip and HTTP 200.
- HTTP 200 indicates an accepted HTTP request, not yet verified persistent clipping.

## Update
The Meijer-only passive hook can inspect small replayable JSON bodies, logging **field names and types only**. Raw body values, authentication headers, URLs with queries, coupon IDs and account IDs are not logged. Streaming, one-shot, non-JSON and large bodies are skipped. The observer never modifies Meijer requests or responses.

## Test
1. Install the APK from GitHub Actions.
2. Keep LSPosed scoped to Meijer only.
3. Force-stop and reopen Meijer to load the new hook.
4. Manually clip one coupon.
5. Open AutoPilot and press **Read last Meijer clip request trace · root**.
6. Look for body_shape=... and status=200, and share the output.

Automatic Clip All is not implemented yet. Workday, Achievers, SmartSense app-specific integrations remain future milestones.
