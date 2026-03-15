# Keyboard2 / UnBelievable Keyboard

Android IME (keyboard) app with Typing Master and integrated Developer Console.

## Features
- Full Android keyboard (QWERTY, AZERTY, Dvorak, and many more layouts)
- Typing Master training activity
- Clipboard History
- Floating Developer Console (logcat + app logs overlay)

## Dev Console
- Located in `srcs/juloo.keyboard2/devconsole/`
- **DevConsoleManager** – singleton that reads logcat and collects app logs (pause/resume, up to 1000 logs)
- **DevConsoleService** – floating overlay window service matching the React reference UI exactly:
  - Header: connection dot, "Enhanced Console" title, Server/Logcat toggle, status text, action buttons
  - Buttons: Pause/Resume, Refresh, Errors (with count), Copy (with dropdown: all / select / range), Save (Archive), Saved Logs (Clock), Maximize, Close
  - Selection mode bar: Select All, Clear, Copy Selected (N), Exit Selection
  - Log entries: entry #, level badge (ERROR/WARN/INFO/DEBUG/LOG coloured), source badge, timestamp, message, metadata, per-item copy button
  - Scroll-to-top / bottom buttons inside log area
  - Drag (header) and resize (bottom-right handle)
  - Dialogs: Save Collection, Range Copy, Saved Log Collections (Load/Export/Delete), Error/Warn viewer
  - Persistent storage via SQLite (`DevConsoleDatabaseHelper`)
- **DevConsoleHelper** – `show(context)` / `hide(context)` + `d/i/w/e(tag, message)` helpers
- **Opened from LauncherActivity** via the "Open Dev Console" button (added to `launcher_activity.xml`)

## System Console (in main keyboard)
- `srcs/juloo.keyboard2/devconsole/SystemConsoleService.java` — device-wide logcat via Shizuku reflection
- `LauncherActivity.java` has 4th button "System Console" with Shizuku status dot
- minSdk bumped to 24 (required by Shizuku API 13.1.5)

## Build
- Android SDK 35, minSdk 24
- Gradle Kotlin DSL (`build.gradle.kts`)
- Build via `./gradlew assembleDebug`

---

## Standalone System Console APK — `sysconsole/`

Separate Android project at `sysconsole/` with package `juloo.sysconsole`.
Contains only the System Console feature — no keyboard code.

### Key files
| File | Purpose |
|---|---|
| `sysconsole/AndroidManifest.xml` | Package `juloo.sysconsole`, Shizuku provider, FileProvider |
| `sysconsole/build.gradle.kts` | SDK 35 / minSdk 24 / Shizuku + AndroidX deps |
| `sysconsole/srcs/juloo/sysconsole/MainActivity.java` | Launcher: Shizuku status + consent UI |
| `sysconsole/srcs/juloo/sysconsole/SysConsoleService.java` | Floating overlay service (logcat + all features) |
| `sysconsole/srcs/juloo/sysconsole/SysConsoleLog.java` | Log data model |
| `sysconsole/srcs/juloo/sysconsole/SysConsoleDatabaseHelper.java` | SQLite persistence |
| `sysconsole/res/layout/console_overlay.xml` | Main overlay UI |
| `sysconsole/res/layout/log_item.xml` | Single log entry row |
| `.github/workflows/sysconsole-build.yml` | CI — triggers on `sysconsole/**` changes, produces `sysconsole-debug` artifact |

### Build
```bash
cd sysconsole && ./gradlew assembleDebug
```
