# 🦁 MD Print

Premium blue + AMOLED black printing app for Android (`com.md.mdprint`).

**Features:** print PDFs, photos, text, clipboard, web pages · Wi-Fi printer discovery (mDNS/IPP) · add printer by IP ·
direct IPP printing (PDF with automatic image fallback) · system print dialog (Mopria/any print service) ·
copies, paper size, colour/B&W, duplex, orientation · live preview · job history · "Share to MD Print" from any app.

## Build the APK with GitHub Actions
1. Create a repo, push this folder to `main`.
2. Open **Actions → Build MD Print APK** (runs automatically on push). Download `MD-Print-apk` from the run's Artifacts.
3. Tag `v1.0.0` to get the APK attached to a GitHub Release.

Optional stable signing (so updates install over old versions): add repo secrets
`KEYSTORE_BASE64` (`base64 -w0 my.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## Local build
`gradle assembleRelease` (Gradle 8.9, JDK 17, Android SDK 34) — or open in Android Studio.
