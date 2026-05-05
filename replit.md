# Keyboard2 / UnBelievable Keyboard

Android IME (keyboard) app with Typing Master, Developer Console, and Smart Clips system.

## Features
- Full Android keyboard (QWERTY, AZERTY, Dvorak, and many more layouts)
- Typing Master training activity
- Clipboard History — auto-tracks copied text, search, bulk delete, export
- **Smart Clips** — manually saved clips (passwords, tokens, emails) with PIN lock
- Floating Developer Console (logcat + app logs overlay)
- Fixed Home Screen Widget (clipboard history + smart clips toggle)
- Floating Widget (overlay, clipboard history + smart clips toggle)

## Smart Clips System (New)

### SmartClipsService (`srcs/juloo.keyboard2/SmartClipsService.java`)
- Singleton data service for Smart Clips
- Each clip: serial no (auto-increment), content, description, keyword, hidden, locked, timestamp
- PIN protection: SHA-256 hashed PIN stored in SharedPreferences
- 10-minute auto-unlock: after PIN entry, can keep unlocked for 10 min
- Persists data as JSON in SharedPreferences (`smart_clips_data`)
- Formula resolution: resolve `{serialNo}` or `{keyword}` tokens

### SmartClipsActivity (`srcs/juloo.keyboard2/SmartClipsActivity.java`)
- Full activity with List / Grid view toggle
- Search bar (searches content, description, keyword, serial)
- Newest clips shown at top
- Each clip shows: serial #, content, description, keyword, timestamp
- Buttons per clip: Copy, Edit, Hide (from widget), Lock (blur with PIN)
- Locked clips shown as `●●●●●●●● (Locked)` — require PIN to view/copy
- Hidden clips excluded from widget display only (still visible in app)
- PIN setup on first use, modify PIN option, lock toggle switch
- Auto-unlock countdown timer shown at bottom

### Formula Expansion in IME (`srcs/juloo.keyboard2/KeyEventHandler.java`)
- When you type `{1}` or `{mykeyword}` anywhere (while using this keyboard), the IME detects the `}` and replaces `{token}` with the matching smart clip content
- Serial number: `{3}` → clip #3 content
- Keyword: `{mypassword}` → clip with keyword "mypassword"
- Locked clips show a toast instead of pasting

### Widget Support
- Fixed widget (`ClipboardWidgetProvider`) has a "Smart" toggle button
- Clicking "Smart" toggles between Clipboard History and Smart Clips mode
- Title updates to reflect current mode
- If Smart Clips tab lock is ON and tab is locked, widget shows toast to open app first
- Floating widget has two mode buttons: "Clipboard" | "Smart Clips"
- Floating widget shows PIN dialog inline when switching to locked Smart Clips
- Both widgets sync live with SmartClipsService data

## Dev Console
- Located in `srcs/juloo.keyboard2/devconsole/`
- **DevConsoleManager** – singleton that reads logcat and collects app logs (pause/resume, up to 1000 logs)
- **DevConsoleService** – floating overlay window service matching the React reference UI exactly
- **DevConsoleHelper** – `show(context)` / `hide(context)` + `d/i/w/e(tag, message)` helpers
- **Opened from LauncherActivity** via the "Open Dev Console" button

## Clipboard History
- ClipboardHistoryActivity — full activity, search bar, bulk delete, export (txt/pdf)
- ClipboardHistoryService — captures clipboard changes, persists to SharedPreferences
- Navigation button to Smart Clips from Clipboard History screen

## Build
- Android SDK 35, minSdk 21
- Gradle Kotlin DSL (`build.gradle.kts`)
- Build via `./gradlew assembleDebug`
- Requires Android SDK (ANDROID_HOME) to be set
