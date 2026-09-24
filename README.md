# SoundSplit Android Prototype 0.2

This is the **native Android** experiment for SoundSplit.

## Core experiment

1. SoundSplit plays a user-selected local audio file.
2. The user selects a Bluetooth output inside SoundSplit.
3. SoundSplit calls `MediaPlayer.setPreferredDevice(...)` for its own player.
4. SoundSplit deliberately does **not** request normal media audio focus.
5. Playback runs inside a `mediaPlayback` foreground service so it can stay alive in the background.
6. The user opens TikTok / Instagram / YouTube and starts another media stream.
7. SoundSplit shows both the **requested** route and the **actual route Android reports**.

## The result we want

- SoundSplit music continues when TikTok starts.
- SoundSplit stays on the chosen Bluetooth speaker.
- Ideally TikTok can be moved to the phone speaker using the phone's general media-output control while SoundSplit remains pinned to Bluetooth.

## Important limitation

`setPreferredDevice()` is a preference, not a guarantee. Android/OEM policy can ignore or override it. This prototype cannot directly command TikTok's audio route.

## Build requirements

- Android Gradle Plugin 9.4.0
- Gradle 9.6.0
- JDK 17
- compileSdk 36
- Android SDK Build Tools 36.0.0

## Cloud build

A GitHub Actions workflow is included at `.github/workflows/build-apk.yml`. If this project is pushed to a GitHub repository, the workflow builds `app-debug.apk` and uploads it as a workflow artifact.


## Build status
Validated for Android Gradle Plugin 9.4.0, Gradle 9.6.0, JDK 17, compileSdk 36. The included GitHub Actions workflow installs the required Android SDK packages and produces app-debug.apk.
