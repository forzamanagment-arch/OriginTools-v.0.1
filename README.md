# OriginTools

**OriginTools 0.1.0 BETA** — Android utility app built with Kotlin and Jetpack Compose.

## Included

- Dark Material 3 interface with four bottom tabs
- Haze glass-style bottom navigation
- Shizuku server and permission status
- Device diagnostics from Android `Build` and kernel properties
- Heuristic Root presence indicator (not a certification)
- Shizuku-gated controls for `fingerprint_icon_style_resource_a`
- Russian-only settings and project information
- GitHub Actions build for debug APK

## Build

The GitHub Actions workflow builds on every push to `main` or `master`.

1. Open the **Actions** tab in this repository.
2. Select **Build OriginTools APK**.
3. Open the latest successful run.
4. Download the `OriginTools-0.1.0-BETA-debug` artifact.

For a local build, use Android Studio with JDK 17 and Gradle 8.11.1, then run the `assembleDebug` Gradle task.

## Vivo Sans

The workflow checks the repository's **Fonts** release and supports either individual TTF/OTF files or a ZIP containing them. The filenames should identify the Regular, Medium, Bold and Black weights. Missing weights fall back to the system sans-serif font so the APK can still build.

## Shizuku and system changes

Shizuku does not grant Root by itself. It exposes the privilege level of an already running Shizuku server after the user grants this app permission.

The fingerprint option uses the Android `settings` command through Shizuku to write the `system` namespace. Turning the option off deletes the override so Android can use its default behavior. OEM firmware may ignore or not implement this key.

The home-screen `一键越狱` row is informational only. OriginTools does not run an exploit, unlock a bootloader, or attempt privilege escalation.

## Project

- Package: `com.origintools.app`
- Version: `0.1.0 BETA`
- Min Android: 8.0 (API 26)
- Compile SDK: 35
