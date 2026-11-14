# AppLock

A simple Android app lock written in Kotlin.

## Features
- PIN lock (stored as a salted PBKDF2 hash, never in plain text)
- Optional fingerprint unlock
- Choose which installed apps to lock
- Locks again every time you leave a protected app
- 30-second lockout after 5 wrong PINs
- Lock screen blocks screenshots
- AppLock's own settings are protected by your PIN
- Restarts protection automatically after reboot

## How it works
A foreground service (`LockService`) checks which app is in front using
`UsageStatsManager`. When a locked app appears, it opens `LockActivity` on top.

## Build
1. Open this folder in Android Studio (Koala or newer) and let Gradle sync.
2. Run on a device, or use **Build > Build APK(s)**.

## First run on the phone
1. Set a PIN.
2. Grant **Usage access** and **Display over other apps** (buttons open the right settings page).
3. Choose the apps to lock.
4. Tap **Start protection**.

Some phones (Xiaomi, Oppo, Vivo, Samsung, etc.) kill background apps. Set AppLock to
"No restrictions" / "Autostart: on" in battery settings so protection keeps running.

## Play Store note
Apps using Usage Access and Draw-over-apps need a clear in-app explanation and a
privacy policy, and Google reviews them. Sideloaded APKs have no such restriction.
