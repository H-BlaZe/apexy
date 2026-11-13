# Installing Apexy

This guide is for everyone, including people who have never installed an app outside the Play Store. No computer needed.

- [1. Download](#1-download)
- [2. Install](#2-install)
- [3. First launch](#3-first-launch)
- [4. Make sure it always rings](#4-make-sure-it-always-rings)
- [5. Brand-specific steps](#5-brand-specific-steps)
- [Updating](#updating)
- [Uninstalling](#uninstalling)
- [FAQ](#faq)

## Before you start

- Your phone needs **Android 8.0 or newer**. To check: **Settings → About phone → Android version**.
- You need about **100 MB** of free space.

## 1. Download

1. On your phone, open this link: **https://github.com/H-BlaZe/Apexy/releases/latest**
2. Scroll down to the **Assets** section. Tap it if it's collapsed.
3. Tap the file whose name ends in **`-arm64.apk`**, for example `Apexy-1.7.1-arm64.apk`.

> **Which file do I pick?**
> - **`-arm64.apk`**: for phones and tablets. Pick this one.
> - **`-x86_64.apk`**: only for emulators and some Chromebooks. You almost certainly don't need it.
> - Ignore **Source code (zip)** and **Source code (tar.gz)**. Those are for developers.

Your browser may warn that *"this type of file can harm your device"*. Tap **Download anyway**. That message appears for every APK file.

## 2. Install

1. When the download finishes, tap **Open** in the notification or at the bottom of the browser. You can also find the file in your **Files** or **Downloads** app.
2. Android will probably say something like *"For your security, your phone is not allowed to install unknown apps from this source."*
   - Tap **Settings**.
   - Switch on **Allow from this source**.
   - Press back.
3. Tap **Install**.
4. If **Google Play Protect** shows a warning such as *"Unsafe app blocked"* or *"Send app for scanning?"*:
   - Tap **More details**, then **Install anyway**.
   - It's fine to tap **Don't send**.

   Play Protect shows this for apps that aren't on the Play Store. Apexy's source code is public, and it has no internet permission, so it can't send anything off your phone.
5. Tap **Open**.

You can turn **Allow from this source** back off afterwards if you like. Apexy keeps working.

## 3. First launch

Apexy shows a few welcome screens and asks for some permissions. Here's what each one is for:

| Permission | Why Apexy needs it |
|---|---|
| Notifications | To show the alarm screen when it rings |
| Alarms & reminders | To ring at the exact minute you set |
| Camera | Only for the QR code, barcode and photo missions |
| Physical activity | Only for the steps mission and sleep tracking |
| Microphone | Only for snore detection while sleep tracking is on |
| Display over other apps | To bring the alarm back if you try to leave it |
| Calendar | Only to show today's events on the morning card |

You can say no to anything you won't use. Missions that need a missing permission just won't be offered.

## 4. Make sure it always rings

**This is the most important step.** Many phones kill apps in the background to save battery, and that includes alarms.

1. Open Apexy and go to **Settings → Reliability**.
2. Tap **Fix** next to every row without a green tick, and allow what it asks for.
3. Follow the steps for your phone brand on that screen (also listed below).
4. Tap **Test alarm**, then lock your phone and put it down.
5. It should ring in about a minute. If it doesn't, go through section 5 for your brand.

## 5. Brand-specific steps

Menu names change a little between versions. If you can't find a setting, search for it in the Settings app.

### Xiaomi, Redmi and POCO (MIUI / HyperOS)

These phones are the strictest. Do all of these:

1. **Autostart:** Settings → Apps → Manage apps → Apexy → switch on **Autostart**.
2. **Battery:** same page → **Battery saver** → **No restrictions**.
3. **Other permissions:** same page → **Other permissions** → allow **Show on lock screen**, **Display pop-up windows while running in the background** and **Display pop-up window**.
4. **Lock it in recents:** open the recent-apps view, long-press Apexy (or pull it down) and tap the **lock** icon.

### Samsung (One UI)

1. Settings → Battery → **Background usage limits** → make sure Apexy isn't in **Sleeping apps** or **Deep sleeping apps**.
2. Add Apexy to **Never sleeping apps**.
3. Settings → Apps → Apexy → Battery → **Unrestricted**.

### OnePlus, Oppo and Realme (OxygenOS / ColorOS / Realme UI)

1. Settings → Apps → Apexy → Battery → **Allow background activity** (or **Don't optimise**).
2. Allow **Auto launch** / **Allow auto launch**.
3. Lock Apexy in the recent-apps view.

### Vivo and iQOO (Funtouch OS / OriginOS)

1. Settings → Battery → **Background power consumption management** → Apexy → **Allow**.
2. i Manager → App manager → **Autostart** → switch on Apexy.

### Huawei and Honor (EMUI / MagicOS)

1. Settings → Battery → **App launch** → Apexy → turn off **Manage automatically**, then switch on all three toggles.

### Google Pixel and most other phones

Usually just the Reliability checklist is enough. Make sure battery is set to **Unrestricted**: Settings → Apps → Apexy → App battery usage.

## Updating

1. Download the newest `-arm64.apk` from the [releases page](https://github.com/H-BlaZe/Apexy/releases/latest).
2. Open it and tap **Update**.

Your alarms, history and settings are kept.

> **Coming from 1.6.x to 1.7.0 or later?** Version 1.7.0 changed the app's package name, so Android installs it as a second app instead of updating the old one. To move over:
> 1. In the **old** Apexy, go to **Settings → Backup → Back up** and save the file.
> 2. Install the new APK and open it.
> 3. Go to **Settings → Backup → Restore** and pick the file you saved.
> 4. Uninstall the old Apexy. Until you do, both apps can ring.
>
> Snore recordings don't come across. Everything else does.

> If Android says *"App not installed as package conflicts with an existing package"*, the APK you downloaded was signed by someone else (for example, a build from a fork). Only install official releases from the link above. To switch anyway, back up first (**Settings → Backup**), uninstall, install the new one, then restore.

## Uninstalling

Long-press the Apexy icon → **Uninstall**, or go to Settings → Apps → Apexy → **Uninstall**. This deletes all Apexy data from your phone. If you want to keep your alarms, make a backup first in **Settings → Backup** and save the file somewhere safe.

## FAQ

**Is this safe?**
Apexy has no internet permission, so it can't send your data anywhere. The full source code is public on GitHub for anyone to inspect.

**Why isn't it on the Play Store?**
It may be in future. For now, GitHub releases are the official source.

**Do I need to keep the app open?**
No. Close it like any other app. Just finish the Reliability checklist first.

**It didn't ring!**
Run **Settings → Reliability → Test alarm** with the screen off. If the test fails, go through section 5 again for your brand. Still broken? [Open an issue](https://github.com/H-BlaZe/Apexy/issues/new/choose) with your phone model and Android version.

**Does it cost anything?**
No. Everything is free. If you'd like to support the project, see [Support Apexy](../README.md#-support-apexy).
