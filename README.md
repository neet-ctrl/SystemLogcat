# System Console — devConsole App

A floating, device-wide logcat viewer for Android, powered by [Shizuku](https://shizuku.rikka.app/).

---

## What it does

System Console runs as a floating overlay window that you can drag, resize, and collapse into a small icon while using any other app. It streams live logcat output from the entire Android system (or filtered to a specific app), lets you search and filter logs by severity level or package, copy entries to the clipboard, save collections to an on-device database, and export them as PDF files.

---

## Features

| Feature | Details |
|---|---|
| **Live logcat stream** | Uses Shizuku to run `logcat -v threadtime` with elevated privileges, giving access to every process on the device |
| **Floating overlay** | Draggable, resizable window; collapses to a 56 dp icon that snaps to the screen edge |
| **App filter with search** | Tap the `📦 APP` button, then type in the search bar to instantly filter the installed app list |
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
| `QUERY_ALL_PACKAGES` | Used to list installed apps for the app-filter picker |
| `FOREGROUND_SERVICE` | Keeps the overlay service alive while the app is in the background |
| `moe.shizuku.manager.permission.API_V23` | Required by the Shizuku SDK |

---

## How to use

1. **Install Shizuku** from [Google Play](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) or [F-Droid](https://f-droid.org/en/packages/moe.shizuku.privileged.api/).
2. **Start Shizuku** using wireless debugging (Developer Options → Wireless debugging) or via root.
3. **Install System Console** (debug APK from GitHub Actions, or build from source).
4. **Open System Console** — the main screen shows Shizuku and overlay permission status.
5. Grant both permissions when prompted, then tap **Open System Console**.
6. The floating console will appear. Drag the header to move it; drag the `◢` handle to resize.

### Filtering by app

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
├── AndroidManifest.xml          # App manifest — permissions, activities, services, providers
├── build.gradle.kts             # Gradle build config — dependencies (AndroidX, Shizuku), SDK versions
├── gradle.properties            # android.useAndroidX=true
├── settings.gradle.kts          # Repository config (Google, Maven Central, Rikka)
├── gradlew / gradlew.bat        # Gradle wrapper
├── res/
│   ├── drawable/                # Custom drawables for console UI (buttons, badges, dots)
│   ├── layout/
│   │   ├── console_overlay.xml  # Main floating overlay layout
│   │   └── log_item.xml         # Single log row layout
│   ├── mipmap-*/                # App launcher icons at all screen densities
│   ├── values/strings.xml       # App name string resource
│   └── xml/provider_paths.xml   # FileProvider paths for PDF export
└── srcs/juloo/sysconsole/
    ├── MainActivity.java         # Launcher activity — permission status + console launch
    ├── SysConsoleService.java    # Core service — overlay window, logcat, filters, dialogs
    ├── SysConsoleLog.java        # Log entry model with level, source, message, timestamp
    └── SysConsoleDatabaseHelper.java  # SQLite helper — save/load/delete log collections
```

---

## Architecture notes

- **SysConsoleService** is an Android `Service` that creates a `WindowManager` overlay. It is long-lived and marked `stopWithTask="false"` so it survives the launcher being closed.
- Logcat is read by spawning a privileged process via `Shizuku.newProcess()` (reflection), reading its stdout on a dedicated daemon thread, and posting each parsed line to the main thread via a `Handler`.
- PID-to-app-name mapping is refreshed every 5 seconds using `ActivityManager.getRunningAppProcesses()` and cached in a `HashMap` guarded by `synchronized`.
- Log entries are capped at 2 000 in both the full buffer (`mAllLogs`) and the filtered view (`mLogs`).
- Saved collections are stored in SQLite using `SysConsoleDatabaseHelper` (two tables: `saved_collections` and `saved_log_entries` with a foreign key and cascade delete).

---

## License

See [LICENSE](LICENSE) if present, or contact the repository owner.
