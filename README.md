# OriginTools

OriginTools 0.1.0 BETA — Android utility app built with Kotlin and Jetpack Compose.

## Features
- Shizuku connection and permission status
- Device and Android diagnostics
- Shizuku-gated fingerprint icon setting controls
- Dark Material 3 UI with a Haze glass bottom bar
- GitHub Actions APK build

## Build
Open this repository in Android Studio and run `./gradlew assembleDebug`.

## Notes
Shizuku does not grant root by itself. It only exposes the privilege level of an already running Shizuku server. The one-click jailbreak row is informational and does not run an exploit or attempt privilege escalation.

Vivo Sans font files are expected from the repository's `Fonts` release and are downloaded by the workflow when the release contains the four font assets. The app has a system sans-serif fallback.
