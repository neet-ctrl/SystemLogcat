# System Console — devConsole App

A floating, device-wide logcat viewer and family security auditor for Android, powered by [Shizuku](https://shizuku.rikka.app/).

---

## What it does

System Console has two major modes:

1. **Floating Logcat Console** — A draggable, resizable overlay window that streams live logcat output from every process on the device. Filter by app, severity level, or search term. Copy, save, and export logs as PDF.

2. **Family Security Auditor** — A full security dashboard that scans every installed APK for risk factors, detects spyware and stalkerware, analyzes banking-app threats, monitors live sensor access (camera, mic, location), and lets you uninstall suspicious apps directly via Shizuku — no root required.

---

## Features

### Floating Console

| Feature | Details |
|---|---|
| **Live logcat stream** | Uses Shizuku to run `logcat -v threadtime` with elevated privileges, giving access to every process on the device |
| **Floating overlay** | Draggable, resizable window; collapses to a 56 dp icon that snaps to the screen edge |
| **App filter with search** | Tap `📦 APP`, then type to instantly filter the installed app list |
| **Level filter** | Filter logs by ALL / ERROR / WARN / INFO / DEBUG / LOG |
| **Selection mode** | Long-press any log entry to enter selection mode; select individual entries or all at once, then copy |
| **Save collections** | Save the current log view with a custom name; stored in SQLite on-device |
| **Load saved logs** | Reload any previously saved collection for offline review |
| **PDF export** | Export a saved collection as a formatted PDF and share it via any installed app |
| **Error / Warning badge** | The `⚠` button shows a live count of errors and warnings and opens a filtered error viewer |
| **Pause / Resume** | Pause the live stream without stopping logcat; resume at any time |
| **Refresh** | Restart the logcat process and clear all buffered logs |
| **System / App toggle** | Switch between device-wide system logs and this app's own logs |
| **Copy options** | Copy all logs, copy a range by index, or copy individually selected entries |

### Family Security Auditor

| Feature | Details |
|---|---|
| **Security Dashboard** | Overview card showing total apps scanned, high-risk count, active alerts, and last scan time |
| **App Security Tab** | Lists every installed APK fetched live via PackageManager + Shizuku — no pre-scan required |
| **Risk scoring** | Each app is scored 0–100 based on permission combos, privilege escalation, sensor usage, and network behavior |
| **Permission tags** | Each app row shows colour-coded tags: CAM, MIC, LOC, SMS, A11Y, OVL, VPN, NOTIF |
| **Live process badge** | Apps with a running process show a green `● LIVE` badge |
| **Filters** | Filter by All / High / Medium / Low risk; toggle System apps on or off; real-time search by name or package |
| **Tap to act** | Tapping any app row shows a dialog: View Details or Uninstall |
| **Uninstall via Shizuku** | Runs `pm uninstall --user 0 <package>` silently; app is removed from the list immediately on success |
| **Uninstall fallback** | If Shizuku is not running, falls back to the standard Android system uninstall dialog |
| **System app protection** | System apps cannot be uninstalled — the option is labelled and blocked with a toast |
| **App detail screen** | Full audit page per app: granted permissions, background time, network usage, privilege analysis, risk factors |
| **Spy / stalkerware detection** | Known-threat database + permission-combo heuristics via SpyDetectionEngine |
| **Banking risk analysis** | Detects OTP-interception risk, overlay phishing, and banking-app combinations via BankingRiskAnalyzer |
| **Live sensor monitor** | Parses full AppOps dump via Shizuku to show which apps accessed camera/mic/location recently |
| **Alerts tab** | Aggregated security alerts with severity levels |
| **Security settings** | Scheduled scan interval, alert thresholds, notification preferences |

---

## Requirements

- Android 7.0 (API 24) or higher
- [Shizuku](https://shizuku.rikka.app/) installed and running (via wireless debugging or root)
- "Draw over other apps" permission granted to System Console

---

## Permissions

| Permission | Reason |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Required to draw the floating console overlay over other apps |
| `READ_LOGS` | Grants access to full device logcat output via Shizuku |
| `QUERY_ALL_PACKAGES` | Used to list all installed apps for the app-filter picker and security scan |
| `FOREGROUND_SERVICE` | Keeps the overlay service alive while the app is in the background |
| `PACKAGE_USAGE_STATS` | Used by the security auditor to read per-app last-used and background time |
| `moe.shizuku.manager.permission.API_V23` | Required by the Shizuku SDK |

---

## How to use

1. **Install Shizuku** from [Google Play](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) or [F-Droid](https://f-droid.org/en/packages/moe.shizuku.privileged.api/).
2. **Start Shizuku** using wireless debugging (Developer Options → Wireless debugging) or via root.
3. **Install System Console** (debug APK from GitHub Actions, or build from source).
4. **Open System Console** — the main screen shows Shizuku and overlay permission status.
5. Grant both permissions when prompted.
6. Tap **Open System Console** for the floating logcat overlay, or **Security Auditor** for the security dashboard.

### Using the App Security Tab

1. Open the **Family Security Auditor** and tap the **Apps** tab.
2. All installed apps are loaded immediately — no prior scan needed.
3. Use the filter chips (**All / High / Medium / Low**) to narrow by risk level.
4. Toggle the **System** chip to show or hide system apps.
5. Type in the search bar to filter by app name or package name.
6. **Tap any app row** to open the options dialog:
   - **View Details** — opens the full audit screen for that app.
   - **Uninstall via Shizuku** — silently uninstalls using `pm uninstall --user 0`. Requires Shizuku to be running. The app is removed from the list immediately on success.
   - If Shizuku is not available, the option falls back to the standard Android uninstall screen.
   - System apps show "System apps cannot be uninstalled" instead.

### Filtering by app in the Logcat Console

1. Tap the **`📦 APP`** button in the toolbar.
2. A dialog appears with the full list of installed apps.
3. **Type in the search bar** at the top to filter the list in real time.
4. Tap any app name to filter the console to that app's logs.
5. Tap **✓ All Apps (no filter)** or clear the filter to return to system-wide logs.

### Saving and exporting logs

1. Tap **🗄** (save) to save the current log view — enter a name and tap Save.
2. Tap **🕐** (saved) to open saved collections.
3. From a saved collection you can **Load** (view offline), **Export** (share as PDF), or **Delete** it.

---

## Building from source

### Prerequisites

- JDK 17
- Android SDK with build tools and platform 35
- Shizuku running on a connected device or emulator (for runtime testing)

### Clone and build

```bash
git clone https://github.com/<your-username>/SystemLogcat.git
cd SystemLogcat
./gradlew assembleDebug
```

The debug APK will be at `build/outputs/apk/debug/`.

### Signing

For CI builds, a fresh debug keystore is generated automatically. For local development, the standard Android debug keystore (`~/.android/debug.keystore`) is used by default unless you set:

```
DEBUG_KEYSTORE          path to your keystore file
DEBUG_KEYSTORE_PASSWORD keystore password
DEBUG_KEY_ALIAS         key alias
DEBUG_KEY_PASSWORD      key password
```

To use a custom keystore in GitHub Actions, GPG-encrypt your keystore:
```bash
gpg --symmetric --cipher-algo AES256 --passphrase "debug0" debug.keystore
base64 debug.keystore.gpg > debug.keystore.asc
```
Then add the contents of `debug.keystore.asc` as the `DEBUG_KEYSTORE` repository secret.

---

## CI / GitHub Actions

| Workflow | Trigger | What it does |
|---|---|---|
| `Make Apk CI` | push / PR / manual | Builds the debug APK; restores keystore from secrets or generates a fresh one automatically |
| `Build Android APK` | push / PR (main/master) / manual | Alias build workflow |
| `Android CI` | push / PR (main/master) | Alias build workflow |
| `Build System Console APK` | push / PR (main/master) / manual | Dedicated sysconsole build |
| `Check layouts` | push / PR / manual | Placeholder (layout check removed) |

All workflows upload the resulting APK as a build artifact named `<owner> <branch> debug_apk`.

---

## Project structure

```
.
├── AndroidManifest.xml              # App manifest — permissions, activities, services, providers
├── build.gradle.kts                 # Gradle build config — dependencies (AndroidX, Shizuku), SDK versions
├── gradle.properties                # android.useAndroidX=true
├── settings.gradle.kts              # Repository config (Google, Maven Central, Rikka)
├── gradlew / gradlew.bat            # Gradle wrapper
├── res/
│   ├── drawable/                    # Custom drawables for console UI (buttons, badges, dots)
│   ├── layout/
│   │   ├── console_overlay.xml      # Main floating overlay layout
│   │   └── log_item.xml             # Single log row layout
│   ├── mipmap-*/                    # App launcher icons at all screen densities
│   ├── values/strings.xml           # App name string resource
│   └── xml/provider_paths.xml       # FileProvider paths for PDF export
└── srcs/juloo/sysconsole/
    ├── MainActivity.java            # Launcher activity — permission status + console/auditor launch
    ├── SysConsoleService.java       # Core service — overlay window, logcat, filters, dialogs
    ├── SysConsoleLog.java           # Log entry model with level, source, message, timestamp
    ├── SysConsoleDatabaseHelper.java# SQLite helper — save/load/delete log collections
    ├── SecurityHub.java             # Singleton hub — owns SecurityScanManager instance
    ├── SecurityScanManager.java     # Full security scan engine — all data sources + risk scoring
    ├── SecurityDashboardActivity.java  # Main security auditor dashboard screen
    ├── AppsListActivity.java        # App Security tab — live APK list, filters, Shizuku uninstall
    ├── AppDetailsActivity.java      # Per-app full audit detail screen
    ├── AppSecurityInfo.java         # Data model for a single app's security profile
    ├── AlertsActivity.java          # Security alerts list screen
    ├── MonitorActivity.java         # Live sensor access monitor screen
    ├── SecuritySettingsActivity.java# Auditor settings screen
    ├── ShizukuCommandHelper.java    # Privileged shell runner via Shizuku.newProcess()
    ├── SpyDetectionEngine.java      # Spyware/stalkerware heuristics + known-threat DB
    ├── BankingRiskAnalyzer.java     # Banking-app threat analysis (OTP, overlay phishing)
    ├── NetworkUsageHelper.java      # Per-app network bytes — 7-day window via NetworkStatsManager
    ├── DeviceSecurityHelper.java    # Elevated-privilege app enumeration (admin, VPN, accessibility)
    ├── SecurityUiHelper.java        # Shared UI building utilities (colours, dp, cards, labels)
    ├── SecurityAlert.java           # Alert data model
    ├── AppInstallReceiver.java      # BroadcastReceiver for new app installs
    └── ScheduledScanReceiver.java   # AlarmManager receiver for scheduled background scans
```

---

## Recent changes

### App Security Tab — full rewrite (AppsListActivity)

**What was broken before:**
- The tab called `getCachedApps()` which returned nothing unless a full Deep Scan had been run from the Dashboard first. Opening the tab with a fresh install showed an empty list with "No scan data yet."
- The filter chips (All / High / Low) depended on that same empty cache, so they appeared but did nothing.
- The 2-column grid layout was inconsistent and filter state was not visually reflected.

**What was fixed:**
- **Live loading on open** — the tab now fires its own background load the instant it opens. It calls `PackageManager.getInstalledPackages()` directly (always works) and simultaneously uses Shizuku's `pm list packages -f` and `ps -A` to cross-reference packages and detect running processes. No pre-scan needed.
- **Working filters** — All / High / Medium / Low chips filter the live-loaded list. Each chip visually highlights with its colour (red = High, orange = Medium, green = Low, teal = All). The System toggle shows/hides system apps with its own active state.
- **Clean list UI** — replaced the broken 2-column grid with a reliable single-column list. Each row shows: app icon, name, package, coloured permission tags (CAM / MIC / LOC / SMS / A11Y / OVL / VPN / NOTIF), a green `● LIVE` badge if the process is running, and a numeric risk score with a HIGH / MED / LOW label.
- **Real-time search** — search bar filters by app name or package name as you type.
- **Loading indicator** — an indeterminate progress bar shows while loading; status text shows exactly what phase is running ("Fetching…", "Analyzing 45/312…", "87 of 312 apps").

### Shizuku-powered uninstall

**How it works:**
- Tapping any app row now opens an options dialog instead of going directly to the detail screen.
- Options: **View Details** or **Uninstall via Shizuku** (or **Uninstall (system dialog)** if Shizuku is not running).
- Selecting Uninstall shows a second confirmation dialog with the package name and a warning about permanent removal.
- On confirm, a background thread runs `pm uninstall --user 0 <package>` via Shizuku. If that fails, it retries without `--user 0`.
- **On success**: a success toast appears and the app is immediately removed from the list — no reload required.
- **On failure**: an error toast tells you to check Shizuku is running.
- **System apps**: the uninstall option is visible but tapping it shows a toast explaining system apps cannot be removed this way.
- **No Shizuku**: falls back to Android's built-in `ACTION_DELETE` intent (the standard system uninstall screen).

---

## Architecture notes

- **SysConsoleService** is an Android `Service` that creates a `WindowManager` overlay. It is long-lived and marked `stopWithTask="false"` so it survives the launcher being closed.
- Logcat is read by spawning a privileged process via `Shizuku.newProcess()` (reflection), reading its stdout on a dedicated daemon thread, and posting each parsed line to the main thread via a `Handler`.
- PID-to-app-name mapping is refreshed every 5 seconds using `ActivityManager.getRunningAppProcesses()` and cached in a `HashMap` guarded by `synchronized`.
- Log entries are capped at 2 000 in both the full buffer (`mAllLogs`) and the filtered view (`mLogs`).
- Saved collections are stored in SQLite using `SysConsoleDatabaseHelper` (two tables: `saved_collections` and `saved_log_entries` with a foreign key and cascade delete).
- The security scan engine (`SecurityScanManager`) uses nine data sources layered in order of depth: PackageManager → AppOpsManager → UsageStatsManager → ActivityManager → NetworkStatsManager → Shizuku (full AppOps, process list, granted permissions, device policy, accessibility) → SpyDetectionEngine → BankingRiskAnalyzer → DeviceSecurityHelper.

---

## License

See [LICENSE](LICENSE) if present, or contact the repository owner.
