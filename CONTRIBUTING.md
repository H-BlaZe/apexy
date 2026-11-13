# Contributing to Apexy

Thanks for wanting to help. Apexy is small and opinionated, so a few minutes reading this saves everyone a round of review.

## Ways to help

- **Report a bug:** [open an issue](https://github.com/H-BlaZe/Apexy/issues/new/choose) using the bug template. Always include your phone model and Android version; alarm bugs are often device-specific.
- **Missed alarm on your phone?** That's the most valuable report we can get. Use the "Alarm didn't ring" template.
- **Suggest a feature:** use the feature template. Explain the problem first, then your idea.
- **Write code:** pick an issue labelled `good first issue` or `help wanted`, comment that you're on it, then open a pull request.
- **Translate:** strings live in `app/src/main/res/values/strings.xml` (and inline in Compose for now; moving them to resources is itself a welcome PR).

## Ground rules

These aren't up for debate in a PR. If you think one should change, open an issue first.

1. **No internet, ever.** The manifest strips `INTERNET`, `ACCESS_NETWORK_STATE` and `ACCESS_WIFI_STATE` with `tools:node="remove"`. Don't add a dependency that needs the network, and don't remove those lines.
2. **No ads, analytics, tracking or crash-reporting SDKs.** No paywalls or "premium" features.
3. **Every Room schema change needs a migration.** Bump the version in `data/AppDatabase.kt`, add a `MIGRATION_N_N+1`, and test upgrading from the previous release with real data. Never use destructive migration.
4. **Reliability first.** Anything that touches scheduling, the ringing service or wake locks must keep working on aggressive OEM skins (Xiaomi/HyperOS especially). Say in the PR which phones you tested on.
5. **Use the design system.** UI goes through the neumorphic components in `ui/neu/` and the colours in `NeuColors`. Use `accent` for text and icons, `primary` for fills. Don't pull in stock Material components that break the look.
6. **Icons over words, short units.** Prefer an icon to a label, and write `15s`, `5m`, `1h` rather than "15 seconds". Keep copy short.
7. **Sounds are synthesised or user-supplied.** Don't commit copyrighted audio or assets from other apps.

## Setting up

You need JDK 17+ and the Android SDK with platform 35. Android Studio (Ladybug or newer) handles both.

```bash
git clone https://github.com/<you>/Apexy.git
cd Apexy
./gradlew testFullDebugUnitTest testFossDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/full/debug/app-full-arm64-v8a-debug.apk
```

No keystore is needed. Debug builds are signed with the standard debug key. Note that a debug build can't be installed over an official release (different signature), so uninstall the release first or use a second device or emulator.

The `x86_64` APK is for the emulator. Camera-based missions and snore detection can't be fully tested there: the emulated camera is black and MediaPipe ships no x86_64 libraries. Test those on a real phone.

There are two flavors: `full` (GitHub releases) and `foss` (F-Droid, no MediaPipe, so no Photo mission or snore detection). Code that needs MediaPipe lives in `app/src/full/`, with a stub of the same name in `app/src/foss/`. Never add a proprietary Google library (ML Kit, Play Services, Firebase) to the `foss` flavor; CI fails if you do.

Releases: `scripts/build-foss-apk.sh <version>` builds the F-Droid APK from the version's tag and signs it. F-Droid rebuilds the same tag and only publishes if its build matches that APK, so always attach it to the GitHub release as `Apexy-<version>-foss.apk`.

## Code style

- Kotlin, official code style (`kotlin.code.style=official`), 4-space indent. `.editorconfig` covers the basics.
- Match the code around you: naming, comment density, file layout.
- Comments explain *why*, not *what*.
- Pure logic (scheduling maths, stats, sleep analysis, mission rules) belongs in plain functions with unit tests in `app/src/test/`.

## Pull requests

1. Fork, then branch from `main`: `fix/snooze-crash`, `feat/nfc-mission-timeout`, and so on.
2. Keep each PR to one change. Separate refactors from features.
3. Run `./gradlew testFullDebugUnitTest testFossDebugUnitTest assembleDebug` before pushing. CI runs the same thing.
4. Add or update tests for logic changes.
5. For UI changes, attach before/after screenshots in light **and** dark mode.
6. Update `CHANGELOG.md` under **Unreleased**.
7. Fill in the PR template, including the devices you tested on.

Write commit messages in the imperative, describing the change: `Fix snooze count resetting after reboot`, not `fixed bug`.

By contributing, you agree your work is released under the [GPL-3.0](LICENSE) license.

## Project layout

| Path (`app/src/main/java/com/hblaze/apexy/`) | What's there |
|---|---|
| `core/` | `AlarmScheduler`, `AlarmService` (ringing), receivers, anti-cheat (`AlarmGuard`), backup, torch |
| `data/` | Room entities, `AppDatabase` and migrations, DataStore `Settings` |
| `missions/` | Each mission, `MissionHost` runner, setup screens, typing phrases |
| `sleep/` | `SleepService`, procedural ambient sounds, `SleepAnalysis`, `SnoreDetector`, bedtime reminders |
| `stats/` | `WakeStats` streaks and summaries |
| `ui/` | Activities, widget, haptics, UI sounds, celebration screen |
| `ui/neu/` | Neumorphic design system: theme, shadows, controls, charts, scroll effects |
| `ui/screens/` | App screens |

Bundled models are in `app/src/main/assets/`: `yamnet.tflite` (snore detection) and `image_embedder.tflite` (photo mission).

## Releases (maintainers)

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Move **Unreleased** in `CHANGELOG.md` under the new version.
3. `./gradlew testReleaseUnitTest assembleRelease` with `keystore.properties` present.
4. Rename the outputs to `Apexy-<version>-arm64.apk` and `Apexy-<version>-x86_64.apk`.
5. Tag `v<version>` and attach both APKs plus their SHA-256 sums to a GitHub release.

Always sign releases with the same key, or existing users won't be able to update.
