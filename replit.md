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

---

## Family Security Auditor — Full Feature Set

A comprehensive Shizuku-powered security dashboard built into the System Console APK.

### Architecture — Scan Pipeline (9 data sources)
| Source | What it provides |
|---|---|
| `PackageManager` | Installed apps, requested permissions, services |
| `AppOpsManager` | Camera/mic/location recent usage (non-root) |
| `UsageStatsManager` | Last-used timestamps, foreground/background time |
| `ActivityManager` | Running process list |
| `NetworkUsageHelper` | Per-app 7-day network bytes (NetworkStatsManager + TrafficStats) |
| `ShizukuCommandHelper` | Full AppOps dump, `ps -A` process list, `dumpsys package` granted perms, device admin, accessibility, notification listeners |
| `SpyDetectionEngine` | 20+ known stalkerware DB + permission-combo heuristics |
| `BankingRiskAnalyzer` | Banking app detection, OTP interception + overlay phishing checks |
| `DeviceSecurityHelper` | Device admins, VPN apps, accessibility services, notification listener apps |

### Key Source Files
| File | Purpose |
|---|---|
| `SecurityScanManager.java` | Master 9-source scan pipeline, ScanCallback with phase labels |
| `ShizukuCommandHelper.java` | Privileged shell via `Shizuku.newProcess()` — AppOps, processes, dumpsys |
| `SpyDetectionEngine.java` | Known stalkerware DB + heuristic rules (20+ threats) |
| `BankingRiskAnalyzer.java` | Banking identification + OTP/overlay phishing analysis |
| `NetworkUsageHelper.java` | Per-app network bytes via `NetworkStatsManager` |
| `DeviceSecurityHelper.java` | Elevated-privilege app enumeration (admin/VPN/accessibility) |
| `AppInstallReceiver.java` | Real-time `ACTION_PACKAGE_ADDED/REMOVED` monitoring |
| `SecurityReportExporter.java` | Full audit text export + Android share sheet |
| `AppSecurityInfo.java` | Data model — 35+ fields including threat level, banking risk, network bytes |
| `SecurityDashboardActivity.java` | Main hub: spyware section, elevated apps, alerts, running apps |
| `AppDetailsActivity.java` | Per-app: risk gauge, all permissions, Shizuku-granted perms, network, threat banner |
| `MonitorActivity.java` | Live real-time sensor/AppOps feed |

### Features
- **Spyware & Stalkerware Detection** — known threat DB + 6 permission-combo heuristic rules
- **Shizuku Deep Scan** — runs `dumpsys appops`, `ps -A`, `cmd appops get <pkg>` with privilege
- **Per-App Granted Permissions** — via `dumpsys package <pkg>` parsed by Shizuku
- **Full Running Process List** — from Shizuku `ps -A` (not just visible app processes)
- **Real-Time Install Monitoring** — `AppInstallReceiver` fires risk analysis on every install
- **Banking Risk Analysis** — OTP interception detection, overlay phishing detection
- **Network Usage (7-day)** — per app, shown in details and factored into risk score
- **Device Admin / VPN / Accessibility / Notification Listener detection** — with Shizuku augmentation
- **Security Report Export** — shareable text report covering all threats, risks, and alerts
- **Risk Score 0-100** — multi-factor including actual AppOps usage evidence

### GitHub Actions CI/CD — `.github/workflows/security-auditor-ci.yml`
- Job 1: Debug APK build (always)
- Job 2: Android Lint (always)
- Job 3: Security file audit (verifies all required files exist, checks for hardcoded secrets, counts Shizuku API usages)
- Job 4: Signed release APK (on `v*` tag push or manual trigger with secret keystore)
