# Keyboard2 / UnBelievable Keyboard

Android IME (keyboard) app — package `juloo.keyboard2`. Full keyboard engine with Smart Clips, Clipboard History, Typing Master, Dev Console, Keystroke Logger, and a complete modern UI with theme system.

GitHub repo: `https://github.com/neet-ctrl/FullKeyboard-SystemConsole`

## Build
- Android SDK 35, minSdk 21
- Gradle Kotlin DSL (`build.gradle.kts`)
- Build via `./gradlew assembleDebug` (requires ANDROID_HOME)
- Sources: `srcs/juloo.keyboard2/`, layouts: `res/layout/`, drawables: `res/drawable/`

---

## Theme System (`ThemeManager.java`)

Singleton utility managing Light / Dark / System / Matrix themes for all app activities.

| Key | Values | Default |
|-----|--------|---------|
| `theme` | `system` / `light` / `dark` | `system` |
| `matrix_mode` | boolean | false |
| `matrix_speed` | `slow` / `normal` / `fast` | `normal` |
| `matrix_density` | `low` / `medium` / `high` | `medium` |
| `haptic_feedback` | boolean | true |
| `auto_lock_mins` | 5 / 10 / 30 / -1 | 10 |
| `clip_widget_limit` | 10 / 20 / 50 | 20 |
| `show_serial_widget` | boolean | true |
| `clipboard_history_limit` | 50 / 100 / 200 / 500 | 100 |

### How to apply in any Activity:
```java
@Override protected void onCreate(Bundle b) {
    ThemeManager.applyActivityTheme(this);  // must be first
    super.onCreate(b);
    // ... setContentView or buildUI() ...
    ThemeManager.attachMatrixOverlay(this); // after setContentView
}
@Override protected void onResume() {
    super.onResume();
    if (!ThemeManager.signature(this).equals(mCreatedSig)) recreate();
}
```

### Colour palettes — `ThemeManager.ThemeColors` fields:
`background`, `surface`, `surfaceVariant`, `primary`, `secondary`,
`textPrimary`, `textSecondary`, `textHint`, `headerText`, `headerBg`,
`divider`, `green`, `blue`, `orange`, `purple`

---

## Matrix Rain Overlay (`MatrixRainView.java`)

Custom `View` rendering animated falling katakana / ASCII characters on a canvas.
Added to any activity via `ThemeManager.attachMatrixOverlay(activity)` at `alpha=0.22f`, `clickable=false`.
Speed and density controlled at runtime via `setSpeed()` / `setDensity()`.

---

## App Settings Screen (`AppSettingsActivity.java`)

Fully programmatic theme-aware Settings screen. Registered in `AndroidManifest.xml`.
Opened from the ⊕ icon button in the `LauncherActivity` header.

**Sections:**
- 🎨 Appearance — Theme selector (System/Light/Dark) + Matrix mode (on/off, speed, density)
- 🔐 Smart Clips — Auto-lock timer, show-serial badge toggle, widget clip limit
- 📋 Clipboard — History size limit
- ⚙ Behaviour — Haptic feedback toggle
- ✈ Telegram Bot — token, chatId, enable toggle, auto-forward
- 📁 File Cloud Backup — enable/disable, status, monitored folders list, start/stop
- 🔑 Permissions — notifications, overlay, battery optimization, manufacturer auto-start, storage/media, Shizuku
- ℹ About — App name, description, GitHub link

---

## Launcher (`LauncherActivity.java` + `res/layout/launcher_activity.xml`)

Modern card-based redesign. All original functionality preserved (animations, tryhere, onClick handlers).

**Layout structure:**
- Header banner (brand + `btnLaunchSettingsActivity` gear icon + `btn_app_settings` tune icon)
- Gesture animation strip (swipe / circle / round-trip anims)
- Setup card (`card_setup`) — IME enable / picker buttons
- Feature 2-column grid: `btn_clipboard_history`, `btn_smart_clips`, `btn_typing_master`, `btn_dev_console`
- Full-width `btn_system_console` with Shizuku dot (`shizuku_launcher_dot`) + status text
- Try-here card (`card_tryhere`) with `launcher_tryhere_area`
- Footer with repo link

`applyTheme()` in Java applies: root background, header colour, rounded card backgrounds (`GradientDrawable` 14dp radius), and text tinting to all feature cards. `tintTextViews()` skips emoji-only views.

---

## Smart Clips System

### `SmartClipsService.java` — Singleton data service
- Each clip: `serial`, `content`, `description`, `keyword`, `hidden`, `locked`, `timestamp`
- PIN: SHA-256 hash, stored in `smart_clips_data` SharedPreferences
- 10-minute auto-unlock: `unlock10Min()` / `getUnlockRemainingMs()`
- Formula resolution: `{serial}` or `{keyword}` tokens via `resolveFormula()`
- Listener system: `addListener()` / `removeListener()` / `OnSmartClipsChangeListener`

### `SmartClipsActivity.java` — Full theme-aware activity
- Programmatic UI using `ThemeManager.colors(this)` for all colours
- List view (`ClipAdapter`) and Grid view (`GridClipAdapter`) with toggle
- Search bar (searches content / description / keyword / serial)
- Per-clip buttons (pill-shaped `makePillBtn()`): Copy, Edit, Hide, Lock
- Locked clips shown as `⬛⬛⬛⬛  (Locked)` — require PIN to view/copy
- Hidden clips excluded from widget; still shown in activity
- PIN setup on first use; change PIN / lock toggle in header
- Auto-unlock countdown timer row above footer
- Matrix mode: Monospace font everywhere, Matrix-green colours, `[SMART_CLIPS]` title

### Formula expansion in IME (`KeyEventHandler.java`)
Typing `{token}` → IME replaces it with matching smart clip content on `}` keypress.

---

## Clipboard History (`ClipboardHistoryActivity.java`)
Full activity: search bar, bulk delete, export (txt/pdf). Navigation to Smart Clips.
`ClipboardHistoryService` — captures clipboard changes, persists to SharedPreferences.

---

## Developer Console (`srcs/juloo.keyboard2/devconsole/`)
- `DevConsoleManager` — reads logcat, collects app logs (up to 1000 entries)
- `DevConsoleService` — floating overlay window
- `DevConsoleHelper` — `show(ctx)` / `hide(ctx)` / `d/i/w/e(tag, msg)` statics
- `ShizukuPermissionActivity` — system-wide logcat via Shizuku

---

## Telegram Bot Persistence Layer

4-layer system ensuring the bot **never stops**:
1. **Foreground service** — `startForeground()` with persistent notification; Android cannot kill foreground services
2. **AlarmManager watchdog** — `BotWatchdogReceiver` fires every 30s using `setExactAndAllowWhileIdle` (works in Doze mode); self-chains on each fire
3. **WorkManager** — `BotKeepaliveWorker` scheduled every 15 min as tertiary fallback; survives reboots
4. **scheduleRestart** in `onDestroy` — fires in 5s via `setExactAndAllowWhileIdle`
- Permissions section in Settings now has **Disable Battery Optimization** (direct system dialog) and **Manufacturer Auto-start** (opens MIUI/OxygenOS/Samsung/Huawei auto-start screens)

## Telegram Bot (`TelegramBotService.java`)

Background service polling Telegram Bot API. Enabled **by default** (KEY_ENABLED defaults to `true`).

### Commands
| Command | Description |
|---------|-------------|
| `/recent` | 20 clips shown as individual tap-able buttons; paginated (Prev/Next replaces same message) |
| `/calendar` | Deep drill: Years → Months → Dates → Clips (all as buttons, same message replaced) |
| `/search` | Text search across all clips |
| `/stats` | Clipboard statistics |
| `/clipboard` | Export clipboard as PDF |
| `/smartclips` | Export Smart Clips as PDF (PIN protected) |
| `/all` | Combined clipboard + smart clips PDF (PIN protected) |
| `/appbackup` | Full app backup — same file as Settings backup (PIN protected). Choose JSON or PDF. |
| `/device` | Device info |
| `/status` | Bot + app status |
| `/lock` | Lock Smart Clips session |
| `/watchdog` | Uptime, all service health & persistence layer status |
| `/files` | Last 10 backed-up files with name/size/timestamp |
| `/filestats` | Upload queue stats: done / pending / failed / total bytes |

### Clip Detail View
When any clip is tapped (from /recent or /calendar), detail view shows:
- Full content preview (500 chars), timestamp, description, pinned badge
- **📄 Full Content** button — shows entire content (up to 4000 chars) for copying
- **📌 Description** button — shows clip description
- **🔙 Back** button — returns to previous list (replaces same message, never sends new)

### Calendar Navigation
`/calendar` → Year list → Month list (only years/months with clips) → Date list → Clip list → Clip detail

### /appbackup
PIN-protected. On tap shows format selector:
- **JSON** — uses `BackupRestoreSystem.createBackupJson()` (identical to Settings → Backup)
- **PDF** — uses `buildAllPdf()` (clipboard + smart clips readable report)

### Callback Data Format (all ≤ 64 bytes)
`rp_N` recent page, `rc_N` recent clip, `cp_N` copy content, `de_N` description,
`cy` cal years, `cyy_Y` cal year, `cym_Y_M` cal month, `cyd_Y_M_D` cal date,
`cc_Y_M_D_I` cal clip, `cpc_/dec_` copy/desc cal clip, `bk_*` back navigation,
`bkf/bkj/bkp` backup format/json/pdf

---

## File Cloud Backup System

Fully automated background service that monitors every file-producing folder and instantly uploads new files to Telegram as documents with rich metadata captions.

### Files
| File | Purpose |
|------|---------|
| `FileBackupService.java` | Foreground service: ContentObserver (MediaStore images/video/audio/downloads) + FileObserver on 26 dirs; upload consumer thread; scheduleRestart in onDestroy |
| `FileUploadQueue.java` | SQLite-backed upload queue; MD5 deduplication; status PENDING/UPLOADING/DONE/FAILED; MAX_RETRIES=3; 49 MB size cap; `formatSize()`/`guessMime()` helpers |
| `FileScanWorker.java` | WorkManager 15-min periodic scan calling `FileBackupService.scanAllDirs()` |

### How it works
1. On start (`TelegramBotService.onStartCommand`, `BootReceiver`, app settings toggle) → `FileBackupService.startIfEnabled(ctx)`
2. Service registers ContentObservers on MediaStore + FileObservers on 26 directories (DCIM, Screenshots, WhatsApp all media types, Telegram, Download, Bluetooth, Instagram, Facebook, Twitter, Snapchat, ScreenRecorder…)
3. New file detected → `FileUploadQueue.enqueue()` (MD5 dedup) → consumer thread → `sendDocument()` to Telegram with HTML caption (tag, filename, size, MIME, timestamp, device)
4. Files >49 MB → text notification only (Telegram limit)
5. WorkManager `FileScanWorker` runs every 15 min as fallback catch-up scan
6. `scheduleRestart` on `onDestroy` → 5s AlarmManager restart

### Prefs
`file_backup_prefs` / `backup_enabled` (default `true`)

### Bot commands
`/files` → last 10 uploaded · `/filestats` → queue stats · `/watchdog` → full health report

---

## Key Drawables Added
- `ic_tune.xml` — sliders icon (App Settings button)
- `ic_matrix_mode.xml` — matrix/terminal icon
- `ic_keyboard_gear.xml` — keyboard + cog icon
- `ic_add_clip.xml` — add/plus icon
