# Changelog

All notable changes to Apexy are listed here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/).

Versions before 1.6.0 were released privately, before Apexy was open-sourced.

## [Unreleased]

### Added
- A `foss` build flavor for F-Droid, with no proprietary Google libraries. It leaves out the Photo mission and snore detection, which need MediaPipe.

### Changed
- QR and barcode missions scan with ZXing instead of Google ML Kit, in every build.
- The package name is now `com.hblaze.apexy` (was `com.apexy.alarm`). Android treats this as a new app, so 1.7.0 installs next to 1.6.x instead of updating it. To move over: in the old app, go to **Settings → Backup → Back up**; install 1.7.0, go to **Settings → Backup → Restore** and pick that file; then uninstall the old app. Alarms, registered codes, reference photos, settings, history and sleep nights all come across. Snore recordings don't.

## [1.6.1]

### Fixed
- The Steady mission's timer never started; it now counts while you're on target, with an arrow showing which way to aim.

## 1.6.0

### Added
- Open-source release under GPL-3.0, with contribution guides, issue templates and CI.

### Changed
- The Steady mission asks for a random compass direction and tilt each time, so a phone left on the bed can't pass it.

### Fixed
- The Photo mission always reported a 0% match in release builds.

## 1.5.0

### Added
- About screen covering privacy, permissions and the mission list.

## 1.4.0

### Added
- Typing-mission phrase lists you can browse and extend.
- Synthesised UI sounds, with a toggle in Settings → Sounds.

### Changed
- Short units everywhere (15s, 5m, 1h).
- Better text and icon contrast for every theme colour.
- The sleep-sound mixer responds instantly and turns off cleanly.

## 1.3.0

### Added
- Volume ramp start and curve, speaker limit, and "get louder if ignored".
- Spoken alarm message (offline text-to-speech), custom vibration patterns, torch and screen flash.
- Missions: NFC, Light, Odd one out, In order, Steady; random mission pool.
- Shrinking snooze and snooze totals, morning card, Reliability screen with per-brand guides.
- New logo.

## 1.1.0

### Changed
- Icon-first UI, real vibration feedback, and a mission-complete celebration.

## 1.0.0

### Added
- Onboarding, JSON backup and restore, next-alarm widget, per-alarm accent colour.

## 0.6.0

### Added
- Sleep sounds, sleep tracking with stages, on-device snore detection, bedtime reminders.

## 0.5.0

### Added
- Wake-up history, streaks and reports.

## 0.4.0

### Added
- Anti-cheat: wake-up check, change lock, volume lock, bring-back.

## 0.3.0

### Added
- Wake-up missions: math, memory, typing, shake, steps, squats, QR code, barcode, photo.

## 0.2.0

### Added
- Core alarms: scheduling, ringing screen, snooze, repeat days.

[Unreleased]: https://github.com/H-BlaZe/Apexy/compare/v1.6.1...HEAD
[1.6.1]: https://github.com/H-BlaZe/Apexy/releases/tag/v1.6.1
