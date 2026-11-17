# AutoPilot signing continuity

The v0.3, v0.4, and v0.5 APKs were compiled on ephemeral runners and had **different certificates**. Android ordinary updates require the same signing certificate; **do not uninstall v0.3** to address this.

## Immediate continuity guard
Beginning with v0.6, Gradle is explicitly configured to sign using the restored key. GitHub Actions compares the final APK certificate SHA-256 fingerprint with the keystore certificate, failing the build on any mismatch. The Android CI workflow restores `autopilot-dev-signing-v1` from the existing GitHub Actions cache and **fails if the cached signer is missing**, rather than silently generating another incompatible APK. This keeps development builds aligned with the v0.4+ debug signer while that cache exists.

This cache is **not** suitable as a permanent, securely backed-up production signing key, especially since the GitHub repository is public.

## Permanent signer migration
1. Create a dedicated release keystore outside GitHub Actions.
2. Back it up securely; upload only to encrypted repository secrets for signed builds.
3. Use the *same* key for every future signed release.
4. Perform one verified v0.3 signature migration via a compatible CorePatch install path, if available on this device; otherwise use a user-controlled backup-and-restore migration.
5. Future release APKs then update normally.

No legacy v0.3 private signing key was recovered and no release signing secrets have yet been provisioned.

### Verification correction
v0.5 compiled but its APK still had an ephemeral Android Debug certificate. It must **not** be used as the migration target. v0.6 adds explicit signing and fingerprint validation.
