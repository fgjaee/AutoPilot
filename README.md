# AutoPilot Native v0.3 — Meijer request shape observer

Native Android launcher + Accessibility + KernelSU utilities and a Meijer-scoped, **passive LSPosed observer**.

## Evidence from installed Meijer 2026.11.2 APK

- React Native / Hermes bytecode, with `api/offers/Clip`, `clipOffer`, and `clipOfferRequestSchema` identifiers.
- Android DEX contains `com.facebook.react.modules.network.NetworkingModule`, `OkHttpClientProvider`, and `okhttp3` classes.
- **Not yet confirmed**: live request URL, method, body schema, account auth flow, success status, or batch semantics.

## Build

Gradle 8.11.1, JDK 17, Android SDK 35. GitHub Actions `.github/workflows/android.yml` produces `AutoPilot-debug` APK artifact on push.

## Capture one user-initiated Clip

1. Install the built AutoPilot APK; open from home screen.
2. LSPosed Manager > Modules > AutoPilot > enable; scope **only** `com.meijer.mobile.meijer`. Force-stop and reopen Meijer to activate the hooks.
3. In Meijer app, manually clip **one** coupon (do not clip all yet).
4. Open AutoPilot and select **Read last Meijer clip request trace · root**. Export/copy the output and share it for analysis.
5. Expected lines tagged `AutoPilotMeijer` include `rn_sendRequest_hook_ready` / `okhttp_hooks_ready`, `path=/api/offers/Clip`, `json_keys=...`, and potentially `status=200`. The actual endpoint may differ.

Observer never alters requests or responses and logs no header values, cookies, tokens, account identifiers, or body values. It purposely does not support auto clipping yet.

If no Clip event appears, collect **only** `AutoPilotMeijer` lines and the output of `pm path com.meijer.mobile.meijer`; the app may use another network pipeline or scope could be inactive.

## Current scope

Workday, Achievers, SmartSense adapters are NOT implemented. This build adds instrumentation only; `Clip All` comes after the request/response shape is verified. Do not run the slow Termux OCR clipper for this diagnostic.
