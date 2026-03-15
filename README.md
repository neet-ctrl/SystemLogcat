# UnBelievable Keyboard (Keyboard2)

An Android keyboard (IME) app built on the open-source `juloo.keyboard2` engine, extended with a built-in floating **Developer Console** for real-time log monitoring and debugging — all without leaving the keyboard or any other app.

---

## Features

### Keyboard
- Full custom IME keyboard with multi-layout support
- Activated via **Settings → System → Language & Input → On-screen keyboard**
- Launcher activity accessible from the app icon for setup and utilities

---

## Developer Console

A floating overlay panel that can be opened from the app's launcher screen. It sits above any running app and provides a complete log monitoring and debugging tool.

### Opening the Console
Tap **"Open Dev Console"** on the launcher screen. The console appears as a draggable, resizable floating window.

---

### Header — Row 1
| Element | Description |
|---|---|
| **● Connection dot** | Green = connected to server, red = disconnected, yellow = paused |
| **Enhanced Console** | Title |
| **⬛ Server / 💻 Logcat** | Source toggle button — switches between server logs and system Logcat |
| **Status text** | Shows current log count, filter tag, and connection/pause state |

### Header — Row 2 (scrollable button bar)
All action buttons sit in a horizontally scrollable strip so every button is reachable on any screen size.

| Button | Function |
|---|---|
| **⊡ ALL** (filter) | Opens a dialog to filter displayed logs by level: ALL / ERROR / WARN / INFO / DEBUG / LOG. Active filter shown in matching colour on the button and in the status bar. Persists across source switches and saved-log loads. |
| **⏸ / ▶** (pause/resume) | Pauses live log streaming (button turns yellow). Tap again to resume. |
| **↺** (refresh) | Re-fetches logs from the server (disabled in Logcat mode). |
| **⚠ E:n W:n** (errors) | Opens the Error/Warning viewer showing all ERROR and WARN entries with a "Copy All" option. |
| **⎘ Copy** | Opens a sub-menu: **Copy All** (all visible logs), **Copy Selected** (checked items), **Copy Range** (enter start–end log numbers). |
| **💾 Save** | Prompts for a collection name and saves all currently displayed logs to a local SQLite database collection. |
| **📂 Saved** | Opens the Saved Collections list. Each collection shows its name, log count, and two buttons: **Load** (replace current view) and **Export** (generate a styled PDF). |
| **⤢ / ⤡** (maximize) | Toggles the console between its normal size and full-screen. |
| **✕** (close) | Closes and stops the floating console service. |

---

### Log List
Each log entry is displayed as a card with:
- **#number** — sequential entry number (newest at top)
- **Level badge** — coloured pill: ERROR (red), WARN (yellow), INFO (blue), DEBUG/LOG (grey)
- **Source badge** — identifies the log source (e.g. a tag or component name)
- **Timestamp** — `DD/MM/YY HH:MM:SS` format
- **Message** — full log message text
- **Metadata** (if present) — grey inset box below the message

Long-tap any entry to enter **Selection Mode** (see below).

---

### Selection Mode
Entering selection mode shows a top bar with a count of selected entries and three buttons:

| Button | Function |
|---|---|
| **☐ Select All** | Selects every visible log entry |
| **⎘ Copy Selected** | Copies all selected entries to the clipboard |
| **✕ Cancel** | Exits selection mode and deselects all |

Tap individual entries to toggle their selection.

---

### Source: Server vs Logcat
- **Server mode** — connects to a server endpoint and streams logs in real time. The connection dot shows live status.
- **Logcat mode** — reads system Logcat output directly from the device shell. Refresh is disabled in this mode since Logcat streams live.

---

### Log Filtering
Tap **⊡ ALL** to open the filter dialog. Options:

| Filter | Shows |
|---|---|
| ALL | Every log entry |
| ERROR | Error-level logs only |
| WARN | Warning-level logs only |
| INFO | Info-level logs only |
| DEBUG | Debug-level logs only |
| LOG | Plain log entries only |

The filter button changes colour to match the active level. All incoming logs are always stored in full — only the view is filtered, so switching back to ALL restores everything instantly.

---

### Saved Collections
Tap **💾 Save** to save the current logs under a name. Collections are stored in a local SQLite database and persist across sessions.

Tap **📂 Saved** to view all collections. For each:
- **Load** — loads that collection into the console view (subject to the current filter)
- **Export** — generates a **PDF** of all logs in that collection

#### PDF Export
Tapping **Export** on a saved collection immediately generates a styled PDF and opens the Android share sheet so you can send it via any app (email, messaging, Drive, etc.).

The PDF visually matches the dev console:
- **Dark header bar** — collection name, export date/time, total entries, page number
- **Per-entry cards** — same background colours as the console (red for ERROR, yellow for WARN, blue for INFO, grey for LOG/DEBUG)
- **Level badge** — coloured filled pill (same colours as console)
- **Source badge**, **timestamp**, **message**, and **metadata block** — all rendered in monospace, word-wrapped to fit the page
- Multi-page layout — entries automatically flow onto new pages

---

### Resize and Drag
- **Drag** anywhere on the header to move the console around the screen
- **Drag the resize handle** (bottom-right corner) to resize the panel
- **⤢ Maximize** — snaps to full screen; tap again (⤡) to restore previous size

---

## Project Structure

```
srcs/juloo.keyboard2/
  devconsole/
    DevConsoleService.java        — Main floating overlay service (UI, log display, all actions)
    DevConsoleManager.java        — Service lifecycle manager (start/stop/state)
    DevConsoleHelper.java         — Log fetching helpers (server + Logcat)
    DevConsoleDatabaseHelper.java — SQLite persistence for saved log collections
    DevConsoleLog.java            — Log entry data model
  LauncherActivity.java           — Launcher screen with "Open Dev Console" button
res/
  layout/
    dev_console_overlay.xml       — Floating console window layout
    dev_console_log_item.xml      — Individual log row layout
    launcher_activity.xml         — Launcher screen layout
  xml/
    file_paths.xml                — FileProvider paths for PDF sharing
AndroidManifest.xml               — Permissions, service declarations, FileProvider
```

---

## Permissions Required
- `SYSTEM_ALERT_WINDOW` — required to show the floating overlay above other apps
- `FOREGROUND_SERVICE` — keeps the console service running
- `READ_LOGS` — required for Logcat mode

---

## Building

```bash
./gradlew assembleDebug
```

The APK is output to `app/build/outputs/apk/debug/`.

Install on a connected device:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```
