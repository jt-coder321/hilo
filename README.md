# Hi-Lo Trainer

An Android blackjack trainer for the Hi-Lo card counting system, built with Kotlin and Jetpack Compose, plus **Just Play**, a casual blackjack mode with no counting.

## Updates
The app's **Settings > Updates and backup > Check for updates** reads `latest.json` on the `updates` branch of this repository, downloads the APK it points to, checks its SHA-256, and opens Android's installer.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, build the release APK, then run

```
tools/publish_update.sh app/build/outputs/apk/release/app-release.apk "One line about what's new"
```

## Building
Release builds are signed with `signing/debug.keystore`, which is kept out of this public repository. Updates must be signed with the same key to install over the existing app.
