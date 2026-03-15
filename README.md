# UnBelievable Keyboard

**UnBelievable Keyboard** is a feature-rich, privacy-first Android keyboard (IME) application built entirely in native Java on the Android platform. It is an extended fork of the open-source `juloo.keyboard2` engine, enhanced with a built-in Clipboard History manager, a home-screen Widget, a Typing Master trainer, and a floating Developer Console — all tightly integrated into a single, self-contained app with zero network access and zero ads.

---

## Table of Contents

1. [Language, Framework & Tech Stack](#language-framework--tech-stack)
2. [Build & Install](#build--install)
3. [Permissions](#permissions)
4. [Keyboard](#keyboard)
   - [Swipe & Gesture Input](#swipe--gesture-input)
   - [Compose Key](#compose-key)
   - [Emoji Pane](#emoji-pane)
   - [Inline Clipboard Pane](#inline-clipboard-pane)
   - [Word Suggestions](#word-suggestions)
   - [Voice Typing](#voice-typing)
   - [Numpad & Number Row](#numpad--number-row)
   - [Multi-Layout Support](#multi-layout-support)
   - [Extra Keys](#extra-keys)
   - [Foldable & Wide-Screen Support](#foldable--wide-screen-support)
5. [Clipboard History](#clipboard-history)
   - [Inline Keyboard Pane](#inline-keyboard-pane)
   - [Clipboard History Activity](#clipboard-history-activity)
   - [Pin Entries](#pin-entries)
   - [Backup & Restore](#backup--restore)
6. [Widget](#widget)
   - [Home-Screen Widget](#home-screen-widget)
   - [Floating Widget Overlay](#floating-widget-overlay)
7. [Typing Master](#typing-master)
8. [Developer Console](#developer-console)
   - [Opening the Console](#opening-the-console)
   - [Header Controls](#header-controls)
   - [Log List](#log-list)
   - [Selection Mode](#selection-mode)
   - [Saved Collections & PDF Export](#saved-collections--pdf-export)
   - [Resize & Drag](#resize--drag)
9. [Settings](#settings)
   - [Layout](#layout)
   - [Typing](#typing)
   - [Behavior](#behavior)
   - [Style & Themes](#style--themes)
   - [Clipboard](#clipboard)
   - [Backup & Restore Settings](#backup--restore-settings)
10. [Localization](#localization)
11. [Project Structure](#project-structure)
12. [Advantages](#advantages)

---

## Language, Framework & Tech Stack

| Property | Details |
|---|---|
| **Primary Language** | Java (Android SDK) |
| **Platform** | Android (native — no cross-platform framework) |
| **Build System** | Gradle with Kotlin DSL (`build.gradle.kts`) |
| **Min SDK** | 21 (Android 5.0 Lollipop) |
| **Target SDK** | 35 (Android 15) |
| **Compile SDK** | android-35 |
| **App Version** | 1.32.1 (versionCode 50) |
| **Application ID** | `juloo.keyboard2` |
| **Key Dependencies** | `androidx.window:window-java:1.4.0`, `androidx.core:core:1.16.0` |
| **Test Framework** | JUnit 4.13.2 |
| **Database** | Android SQLite (via `SQLiteOpenHelper`) |
| **PDF Generation** | Android `android.graphics.pdf.PdfDocument` |
| **Font** | Custom TTF font built from SVG sources via FontForge |
| **Layout Assets** | Python-generated XML (`gen_layouts.py`, `gen_method_xml.py`) |
| **Emoji Assets** | Python-generated raw text (`gen_emoji.py`) |

---

## Build & Install

**Requirements:** Android SDK, Gradle, Python 3 (for asset generation), FontForge (for custom font build).

```bash
# Clone the repository
git clone <repo-url>
cd keyboard

# Build a debug APK
./gradlew assembleDebug

# The APK will be output to:
# app/build/outputs/apk/debug/app-debug.apk

# Install on a connected device via ADB
adb install app/build/outputs/apk/debug/app-debug.apk
```

After installing, go to **Settings → System → Language & Input → On-screen keyboard** and enable **UnBelievable Keyboard**. Then select it as the active input method from the input method picker.

---

## Permissions

| Permission | Purpose |
|---|---|
| `VIBRATE` | Haptic feedback when typing |
| `RECEIVE_BOOT_COMPLETED` | Restore clipboard history state on device boot |
| `SYSTEM_ALERT_WINDOW` | Draw floating overlays (Developer Console, Floating Widget) |
| `READ_LOGS` | Read device Logcat in Developer Console Logcat mode |
| `WRITE_EXTERNAL_STORAGE` | Save files on Android 9 (API 28) and below |

---

## Keyboard

The keyboard is implemented as a standard Android **Input Method Service** (`InputMethodService`) in `Keyboard2.java`. It is activated through the system input method settings and fully replaces the default on-screen keyboard.

### Swipe & Gesture Input

Each key on the keyboard supports **swipe gestures** towards its four corners and four edges, giving every physical key access to up to 9 values without cluttering the layout. For example, swiping up-left from `A` might produce `À`, while swiping right produces an arrow.

Four gesture types are recognized by the `Gesture.java` state machine:

| Gesture | Description |
|---|---|
| **Swipe** | Slide the finger in any direction from a key to access its corner or edge character |
| **Roundtrip** | Swipe out from a key and return back to it to trigger a roundtrip action |
| **Circle (Clockwise)** | Rotate the finger clockwise around a key |
| **Anticircle (Anticlockwise)** | Rotate the finger anticlockwise around a key |

Swipe distance sensitivity is fully configurable. Circle gesture sensitivity has its own independent setting to prevent accidental activation during back-and-forth swipes.

### Compose Key

The keyboard includes a **Compose Key** (`ComposeKey.java`, `ComposeKeyData.java`) that allows typing special characters via multi-key sequences — identical to the Compose key on desktop keyboards. Example: Compose → `'` → `e` → `é`. The compose sequence engine uses a compact binary state machine for maximum performance.

### Emoji Pane

A dedicated **Emoji Pane** (`EmojiGridView.java`, `EmojiGroupButtonsBar.java`) slides up within the keyboard. Emojis are organized into thematic groups selectable from a scrollable category bar at the top. Tap any emoji to insert it instantly. The emoji list is generated from a Python script at build time and bundled as raw assets.

### Inline Clipboard Pane

A **Clipboard Pane** (`ClipboardHistoryView.java`, `ClipboardPinView.java`) is accessible directly inside the keyboard without leaving any app. It shows:
- **Pinned items** at the top (items explicitly saved for long-term reuse)
- **Recent clipboard history** below

Each entry has a **Paste** button to send the text directly to the active text field and a **Pin** button to pin it permanently.

### Word Suggestions

A **Candidates View** (`CandidatesView.java`, `Suggestions.java`) appears above the keyboard and displays word completion suggestions as you type. Tapping a suggestion inserts it and the keyboard learns your word preferences over time. Learned words are viewable and manageable directly from the Settings screen.

### Voice Typing

The keyboard can switch to any installed **voice input app** via `VoiceImeSwitcher.java`. A voice key is available as an extra key or directly from supported layouts. The keyboard automatically detects compatible voice IMEs installed on the device.

### Numpad & Number Row

- **Number Row**: An optional dedicated row of number keys appears at the top of the keyboard. Numbers can be shown alone or alongside symbols.
- **Numpad**: A side numpad panel can be shown always, only in landscape mode, or never. Two orderings are supported: high-first (789 on top) or low-first (123 on top).
- **PIN Entry Mode**: When a numeric-only input field is focused, the keyboard can automatically switch to a compact PIN-entry layout.

### Multi-Layout Support

The keyboard supports switching between **multiple active layouts** configured by the user. Layouts are loaded from XML files at runtime and cover a very wide range of writing systems:

| Script Family | Examples |
|---|---|
| **Latin** | QWERTY, AZERTY (FR/BE), BÉPO, Dvorak, Colemak, Bone, Turkish F, and many regional variants |
| **Cyrillic** | Russian (JCUKEN), Ukrainian, Kazakh, Mongolian, Tajik, Ossetian, Serbian, Macedonian |
| **Arabic** | Standard Arabic, Persian, Kurdish (Sorani), Hindko variants |
| **Devanagari** | INSCRIPT, Phonetic (Hindi) |
| **Bengali** | National, Provat |
| **Greek** | QWERTY-based Greek layout |
| **Hebrew** | Two standard layouts |
| **Armenian** | Classical, QWERTY-mapped |
| **Georgian** | MES standard, QWERTY-based |
| **Gujarati** | Phonetic input |
| **Hangul** | Dubeolsik (Korean standard) |
| **Kannada** | Standard layout |
| **Other scripts** | Thai, Lao, Khmer, Myanmar, Tamil, Telugu supported via layout XML |

Layouts are auto-generated by Python scripts and embedded as XML resources. Users can also define fully **custom layouts** using the simple XML format documented in `doc/Custom-layouts.md`. All possible key values are listed in `doc/Possible-key-values.md`.

### Extra Keys

Users can add extra keys to any layout from two sources:
- **Built-in extra keys**: A curated list of functional keys (arrow keys, media controls, function keys, navigation keys, etc.) selectable from Settings.
- **Custom extra keys**: Fully user-defined keys specified as key values or key sequences.

Extra keys are placed automatically at preferred positions within the keyboard grid.

### Foldable & Wide-Screen Support

The keyboard uses `FoldStateTracker.java` (backed by `androidx.window`) to detect whether a foldable device is unfolded. In unfolded or wide-screen mode, separate height and margin settings apply, and the layout automatically adapts. The wide-screen threshold is 600 dp.

---

## Clipboard History

The clipboard history system is a persistent in-memory service (`ClipboardHistoryService.java`) that monitors Android's `ClipboardManager` for any changes and stores every copied item automatically.

### How It Works

- Every time you copy anything on your device, the service captures the text immediately via a `ClipboardManager.OnPrimaryClipChangedListener`.
- History is saved to `SharedPreferences` so it survives app restarts and device reboots.
- Each history entry stores: **content**, **timestamp**, **description** (user-editable), and **version** (e.g., "2" for an edited copy).
- History capacity is effectively unlimited (backed by `Integer.MAX_VALUE`).
- A configurable **clipboard duration** controls how long entries are retained: 1 minute, 5 minutes, 30 minutes, or until the app stops.

### Inline Keyboard Pane

Accessible directly from within the keyboard through the clipboard key. Shows pinned items and recent history. Paste any item with a single tap without ever leaving the current app.

### Clipboard History Activity

A full standalone screen (`ClipboardHistoryActivity.java`) accessible from the Launcher. Features:

- **Numbered list** of all clipboard entries (newest first)
- **Alternating row colors** for easy scanning
- **Timestamps** shown for every entry
- **Version labels** (e.g., "v2" for entries that have been edited)
- **Real-time search** — filters entries by content or description as you type
- **Tap to edit** — opens a dialog to edit content and description, save as version 2, delete, or share the entry
- **Long-press to copy** — immediately copies the entry back to the system clipboard with a toast confirmation
- **Share** — shares any entry as plain text via the Android share sheet

### Pin Entries

Entries can be **pinned** from the inline keyboard pane. Pinned entries always appear at the top of the clipboard pane and are never removed by the duration setting.

### Backup & Restore

All clipboard history, settings, and learned words can be exported as a single **JSON backup file** (`BackupRestoreSystem.java`). Three export methods are available:
- Copy raw JSON directly to clipboard
- Share as a `.json` file via the Android share sheet
- Download via browser

Restoring is done by selecting a backup file from the device file picker. The restore process merges clipboard entries (skipping duplicates) and restores all settings and learned words.

---

## Widget

### Home-Screen Widget

`ClipboardWidgetProvider.java` and `ClipboardWidgetService.java` provide a standard **Android App Widget** placeable on the home screen. The widget shows a scrollable list of the most recent clipboard entries. Features:
- **Tap any entry** to copy it back to the clipboard instantly
- **Refresh button** to reload the latest clipboard items on demand
- **Floating button** to launch the floating widget overlay

The widget uses Android `RemoteViews` with a `RemoteViewsFactory` to efficiently render entries from the live clipboard service.

### Floating Widget Overlay

`FloatingWidgetService.java` creates a **draggable floating overlay** (using `SYSTEM_ALERT_WINDOW`) that sits above all other running apps at all times. Features:

- Scrollable list of up to **50 recent clipboard entries**
- **Alternating pastel color-coded backgrounds** (blue, green, orange, purple, teal cycling pattern) for each entry
- Entries are truncated to 2 lines with ellipsis for readability
- **Copy button** on each entry for one-tap copying
- Tapping a list item also copies it
- **Live updates** — automatically refreshes whenever the clipboard history changes via a registered listener
- **Collapsible** — tap the floating icon to collapse to a small button and expand again
- **Draggable** — grab the header and move the widget anywhere on the screen

---

## Typing Master

`TypingMasterActivity.java` is a built-in typing practice tool accessible from the Launcher screen. It helps users improve their speed and accuracy directly on their mobile keyboard.

### Features

- **10 curated practice paragraphs**, each 50–70 words, covering technology, nature, coding, communication, fitness, travel, music, and mindset.
- **Real-time character validation** as you type:
  - Correctly typed characters are highlighted in **green** in the paragraph display
  - The next character to type is highlighted in **blue** with bold styling
  - Typing a **wrong character** triggers a short error beep (via `ToneGenerator`) and the wrong character is immediately reverted — you can only advance with the correct key
- **WPM (Words Per Minute) calculation** is automatic when a paragraph is completed, using the elapsed time from your first keystroke to your last
- **"Next" button** becomes active after completing a paragraph and cycles through all 10 paragraphs in order
- The paragraph display is scrollable for longer texts
- The typing area and paragraph view are both accessible with the UnBelievable Keyboard itself, making it a true in-app training loop

---

## Developer Console

The Developer Console is a **floating overlay panel** (`DevConsoleService.java`) that opens above all other running apps and provides a complete, real-time log monitoring and debugging tool — all without leaving any app you are using.

### Opening the Console

Tap **"Open Dev Console"** on the Launcher screen. The console appears as a dark-themed, resizable, draggable floating window. On first launch, the app will request the `SYSTEM_ALERT_WINDOW` permission if not already granted.

### Header Controls

**Row 1 — Status Bar**

| Element | Description |
|---|---|
| **Connection Dot** | Color indicator: green = connected and streaming, red = disconnected, yellow = paused |
| **Title** | "Enhanced Console" |
| **Server / Logcat Toggle** | Switch between streaming server logs and reading system Logcat |
| **Status Text** | Shows current log count, active filter level, and connection/pause state |

**Row 2 — Horizontally Scrollable Action Buttons**

| Button | Function |
|---|---|
| **Filter (ALL / ERROR / WARN / INFO / DEBUG / LOG)** | Opens a dialog to filter displayed logs by level. The button changes color to match the active filter. Filtering only affects the view — all logs are always stored in full. |
| **Pause / Resume** | Pauses live log streaming (button turns yellow). Tap again to resume. |
| **Refresh** | Re-fetches logs from the server (disabled in Logcat mode since it streams live). |
| **Errors (E:n W:n)** | Opens an Error/Warning viewer listing all ERROR and WARN entries with a "Copy All" option. |
| **Copy** | Sub-menu: **Copy All** (all visible logs), **Copy Selected** (checked items), **Copy Range** (enter start–end log numbers). |
| **Save** | Prompts for a name and saves all currently displayed logs to the local SQLite database as a named collection. |
| **Saved** | Opens the Saved Collections list. Each collection shows its name, log count, and Load / Export buttons. |
| **Maximize / Restore** | Toggles the console between normal size and full-screen. |
| **Close (✕)** | Closes the floating console and stops the overlay service. |

### Log List

Each log entry is rendered as a card containing:

| Field | Description |
|---|---|
| **#number** | Sequential entry number (newest entries at the top) |
| **Level Badge** | Color-coded pill: ERROR (red), WARN (yellow), INFO (blue), DEBUG/LOG (grey) |
| **Source Badge** | Identifies the log tag or component name |
| **Timestamp** | `DD/MM/YY HH:MM:SS` format |
| **Message** | Full log message text |
| **Metadata** | (If present) Grey inset box below the message for structured extra data |

The console stores up to **1,000 log entries** in memory. Long-press any entry to enter Selection Mode.

### Selection Mode

A top bar appears when selection mode is active:

| Control | Action |
|---|---|
| **Selected count badge** | Shows how many entries are currently selected |
| **Select All** | Selects every visible log entry |
| **Clear Selection** | Deselects all without exiting selection mode |
| **Copy Selected** | Copies all selected entries to the clipboard as formatted text |
| **Exit (✕)** | Exits selection mode and clears all selections |

Individual entries can be tapped to toggle their selection while in selection mode.

### Source Modes

- **Server Mode** — Connects to a remote server endpoint and streams JSON log entries in real time. The connection dot reflects live status (green = streaming, red = disconnected).
- **Logcat Mode** — Reads Android system Logcat output directly from the device shell via a background reader thread. Refresh is disabled in this mode since Logcat streams live automatically.

Switching between modes clears the current view and loads logs from the newly selected source.

### Saved Collections & PDF Export

**Saving:** Tap **Save**, enter a collection name, and all currently displayed logs are saved to a local **SQLite database** (`DevConsoleDatabaseHelper.java`). Collections persist across sessions and app restarts.

**Loading saved collections:** Tap **Saved** to see all collections. Each shows its name and entry count, with two action buttons:
- **Load** — Loads that collection's logs into the console (active filter is applied on load).
- **Export** — Generates a styled **PDF** using `android.graphics.pdf.PdfDocument`.

**PDF Format:**
- Dark header bar per page: collection name, export date/time, total entries, page number
- Per-entry cards matching the console's color scheme (red for ERROR, yellow for WARN, blue for INFO, grey for LOG/DEBUG)
- Color-coded level badge pill, source badge, timestamp, message, and metadata — all in monospace font, word-wrapped to fit the page
- Entries automatically flow across multiple pages

The PDF is immediately shared via the Android share sheet for sending by email, cloud storage, messaging, or any installed app.

### Resize & Drag

- **Drag** anywhere on the header bar to reposition the console across the screen
- **Drag the resize handle** (bottom-right corner) to freely resize the panel to any size
- **Maximize (⤢)** snaps to full screen; tap again **(⤡)** to restore previous size and position

---

## Settings

All settings are accessible from the Launcher via the menu (top-right) → **Settings**, or from any app by going to system input settings. The settings screen (`SettingsActivity.java`) is organized into six categories:

### Layout

| Setting | Description |
|---|---|
| **Layouts** | Add, remove, and reorder multiple keyboard layouts. Supports the system locale layout plus any number of additional layouts that cycle with a layout switch key. |
| **Extra Keys** | Add built-in keys (arrows, function keys, media keys, etc.) or define fully custom keys that appear in the keyboard. |
| **Number Row** | Show a dedicated number row at the top: hidden, numbers only, or numbers with symbols. |
| **Show Numpad** | Show a side numpad: never, landscape only, or always. |
| **Numpad Layout** | Numpad ordering: high-first (789 on top row) or low-first (123 on top row). |

### Typing

| Setting | Description |
|---|---|
| **Learned Words** | View the full list of words the keyboard has learned from your typing. |
| **Swipe Distance** | How far you must swipe from a key to trigger its corner or edge character. Options: Very Short, Short, Default, Far, Very Far. |
| **Circle Sensitivity** | Sensitivity for circular gesture recognition: High, Medium, Low, or Disabled. |
| **Slider Sensitivity** | Speed of the slider (long-press-and-slide) input: Slow, Medium, Fast. |
| **Long-Press Timeout** | Time (ms) to hold a key before long-press activates. Range: 50–2000 ms, default 600 ms. |
| **Key Repeat** | Enable or disable key repeat while a key is held down. |
| **Key Repeat Interval** | Speed (ms) at which a held key repeats. Range: 5–100 ms, default 25 ms. |
| **Double-Tap Lock Shift** | Lock Shift (caps lock) by double-tapping the Shift key instead of single long-press. |

### Behavior

| Setting | Description |
|---|---|
| **Auto-Capitalisation** | Automatically capitalize the first letter after a sentence-ending punctuation. |
| **Switch Input Immediately** | Switch to voice or another IME immediately when selected, without any confirmation step. |
| **Custom Vibration** | Override system vibration behavior with a custom vibration duration. |
| **Vibration Duration** | Duration of haptic feedback when custom vibration is enabled. |

### Style & Themes

| Setting | Description |
|---|---|
| **Theme** | Visual theme for the entire keyboard. 17 built-in themes available (see below). |
| **Label Brightness** | Brightness/opacity of key label text. Slider 0–100%. |
| **Keyboard Opacity** | Background opacity of the entire keyboard panel. |
| **Key Opacity** | Opacity of unpressed key backgrounds. |
| **Key Activated Opacity** | Opacity of key backgrounds while pressed. |
| **Keyboard Height** | Height of the keyboard as a percentage of screen height. Separate settings for portrait, landscape, portrait-unfolded (foldable), and landscape-unfolded. |
| **Bottom Margin** | Space below the keyboard in dp. Separate settings for all four orientation modes. |
| **Horizontal Margin** | Left and right margins of the keyboard in dp. Separate settings for all four orientations. |
| **Character Size** | Scale factor for key label characters. Range: 0.75× – 1.5×, default 1.15×. |
| **Key Vertical Spacing** | Vertical gap between keys as a percentage of key height. |
| **Key Horizontal Spacing** | Horizontal gap between keys as a percentage of key width. |
| **Borders** | Enable or disable custom key border rendering. |
| **Corner Radius** | Rounded corner radius of keys when borders are enabled. Range: 0–100%. |
| **Border Width** | Width of key borders in dp. Range: 0–5 dp. |

**Available Themes:**

| Theme | Description |
|---|---|
| System | Follows Android system dark/light mode automatically |
| Dark | Dark grey keyboard |
| Light | Light grey keyboard |
| Black | Pure black keyboard |
| Alternative Black | Alternative pure black variant with different accent |
| White | Pure white keyboard |
| ePaper | Low-contrast e-ink inspired look |
| ePaper Black | Dark e-ink variant |
| Desert | Warm sandy and tan tones |
| Jungle | Dark green nature-inspired tones |
| Monet (System) | Android 12+ dynamic color — follows your wallpaper palette |
| Monet (Light) | Dynamic color, forced light variant |
| Monet (Dark) | Dynamic color, forced dark variant |
| Rosé Pine | Muted rose and pine pastel tones |
| Everforest Light | Earthy green light theme |
| Cobalt | Deep cobalt blue |
| Pine | Deep pine green |

### Clipboard

| Setting | Description |
|---|---|
| **Clipboard History** | Enable or disable clipboard history tracking entirely. |
| **Memory Duration** | How long clipboard entries are retained: 1 minute, 5 minutes, 30 minutes, or until the app stops. |

### Backup & Restore Settings

| Setting | Description |
|---|---|
| **Backup Settings** | Creates a full JSON backup containing all app settings, the complete clipboard history (unlimited entries), and all learned words. Export options: copy JSON to clipboard, share as a `.json` file via share sheet, or download via browser. The backup dialog shows the number of clips being backed up in the title. |
| **Restore Settings** | Opens the Android file picker to select a backup `.json` file. Merges clipboard entries (skipping exact duplicates), restores settings preferences, and restores the learned word dictionary. |

---

## Localization

The app UI is fully translated into **21 languages**:

| Language | Code |
|---|---|
| Czech | cs-CZ |
| German | de-DE |
| English | en-US |
| Spanish | es-ES |
| Farsi (Persian) | fa |
| Filipino | fil |
| French | fr-FR |
| Hungarian | hu |
| Indonesian | in (id) |
| Italian | it-IT |
| Japanese | ja-JP |
| Korean | ko-KR |
| Latvian | lv |
| Dutch | nl-NL |
| Polish | pl-PL |
| Portuguese (Brazil) | pt-BR |
| Russian | ru-RU |
| Turkish | tr-TR |
| Ukrainian | uk |
| Vietnamese | vi |
| Chinese (Simplified) | zh-CN |

Fastlane store metadata (title, short description, full description, changelogs) is provided for all major locales for Google Play and F-Droid distribution.

---

## Project Structure

```
UnBelievable Keyboard/
│
├── srcs/juloo.keyboard2/              All Java source code
│   │
│   ├── Keyboard2.java                 Core IME service — layout switching, emoji pane, clipboard pane
│   ├── Keyboard2View.java             Custom keyboard view — rendering and touch handling
│   ├── KeyboardData.java              Layout data model — rows, keys, preferred positions
│   ├── KeyEventHandler.java           Key event processing — modifiers, actions, editor output
│   ├── KeyValue.java                  Individual key value model (char, string, action, compose)
│   ├── KeyValueParser.java            Parses key values from layout XML definitions
│   ├── KeyModifier.java               Modifier key state management (Shift, Ctrl, Alt, Meta, Fn)
│   ├── LayoutModifier.java            Applies numpad, pin-entry, and layout modifications
│   │
│   ├── Gesture.java                   Swipe / circle / roundtrip gesture state machine
│   ├── Pointers.java                  Multi-touch pointer tracking
│   ├── ComposeKey.java                Compose key sequence processor
│   ├── ComposeKeyData.java            Binary state machine data for all compose sequences
│   │
│   ├── ClipboardHistoryService.java   System clipboard monitor — stores and manages history
│   ├── ClipboardHistoryActivity.java  Full clipboard history screen — search, edit, share, delete
│   ├── ClipboardHistoryView.java      Inline clipboard pane rendered inside the keyboard
│   ├── ClipboardHistoryCheckBox.java  Checkbox entry item for the clipboard pane
│   ├── ClipboardPinView.java          Pinned clipboard entries display
│   │
│   ├── TypingMasterActivity.java      Typing practice screen with WPM scoring and audio feedback
│   │
│   ├── LauncherActivity.java          App launcher screen — entry point to all features
│   ├── SettingsActivity.java          Settings screen — all preferences, backup, restore
│   │
│   ├── Config.java                    Global configuration — loads and caches all preferences
│   ├── Theme.java                     Theme color and border drawing attributes
│   ├── BackupRestoreSystem.java       JSON backup and restore for settings + clipboard + words
│   ├── Suggestions.java               Word prediction and user-learned word dictionary
│   ├── CandidatesView.java            Word suggestions bar displayed above the keyboard
│   ├── Autocapitalisation.java        Auto-capitalize logic after sentence-ending punctuation
│   ├── EmojiGridView.java             Emoji grid — displays emoji in a scrollable grid
│   ├── EmojiGroupButtonsBar.java      Emoji category tab bar
│   ├── Emoji.java                     Emoji data model
│   ├── ExtraKeys.java                 Extra key insertion and positioning logic
│   ├── NumberLayout.java              Numpad and number row layout logic
│   ├── VoiceImeSwitcher.java          Detects installed voice IME apps and switches to them
│   ├── FoldStateTracker.java          Detects foldable device fold/unfold state via androidx.window
│   ├── DirectBootAwarePreferences.java  SharedPreferences wrapper safe in direct-boot mode
│   ├── EditorConfig.java              Per-editor input-type configuration (numeric, password, etc.)
│   ├── CurrentlyTypedWord.java        Tracks the word currently being typed for suggestions
│   ├── VibratorCompat.java            Haptic feedback compatible across multiple API levels
│   ├── Logs.java                      Internal debug logging utility
│   ├── Utils.java                     Shared utility methods
│   ├── NonScrollListView.java         Non-scrolling ListView (for use inside outer ScrollViews)
│   ├── CustomLayoutEditDialog.java    In-app dialog for editing custom keyboard layout XML
│   ├── Modmap.java                    Modifier-to-character mapping tables
│   │
│   ├── devconsole/
│   │   ├── DevConsoleService.java         Floating console overlay — all UI, log display, actions
│   │   ├── DevConsoleManager.java         Console service lifecycle and log listener registry
│   │   ├── DevConsoleHelper.java          Log fetching helpers (server endpoint + Logcat reader)
│   │   ├── DevConsoleDatabaseHelper.java  SQLite persistence for saved log collections
│   │   └── DevConsoleLog.java             Log entry data model
│   │
│   ├── widget/
│   │   ├── ClipboardWidgetProvider.java   Android App Widget — home screen clipboard list
│   │   ├── ClipboardWidgetService.java    RemoteViewsFactory for widget list items
│   │   ├── FloatingWidgetService.java     Floating overlay widget (draggable, live-updating)
│   │   └── OverlayPermissionHelper.java   Helper for requesting SYSTEM_ALERT_WINDOW at runtime
│   │
│   └── prefs/
│       ├── LayoutsPreference.java         Multi-layout selector preference widget
│       ├── ExtraKeysPreference.java       Built-in extra keys selector preference
│       ├── CustomExtraKeysPreference.java Custom extra keys editor preference
│       ├── IntSlideBarPreference.java     Integer slider preference widget
│       ├── SlideBarPreference.java        Float slider preference widget
│       └── ListGroupPreference.java       Grouped list preference widget
│
├── res/
│   ├── layout/
│   │   ├── keyboard.xml                   Main keyboard layout container
│   │   ├── launcher_activity.xml          Launcher screen UI
│   │   ├── activity_typing_master.xml     Typing Master screen UI
│   │   ├── clipboard_history_activity.xml Clipboard history standalone screen
│   │   ├── clipboard_pane.xml             Inline clipboard pane (inside keyboard)
│   │   ├── clipboard_history_entry.xml    Single clipboard history row item
│   │   ├── clipboard_pin_entry.xml        Pinned clipboard entry row item
│   │   ├── clipboard_widget.xml           Home-screen widget layout
│   │   ├── clipboard_widget_item.xml      Widget list row item
│   │   ├── layout_floating_widget.xml     Floating widget overlay layout
│   │   ├── dev_console_overlay.xml        Floating developer console overlay layout
│   │   ├── dev_console_log_item.xml       Individual log entry card layout
│   │   └── emoji_pane.xml                 Emoji pane layout
│   │
│   ├── xml/
│   │   ├── settings.xml                   Preference screen definitions
│   │   ├── method.xml                     IME subtypes (auto-generated by Python)
│   │   ├── clipboard_widget_info.xml      App widget metadata and sizing
│   │   └── file_paths.xml                 FileProvider paths for PDF file sharing
│   │
│   └── values/
│       ├── strings.xml                    All user-visible strings (English base)
│       ├── arrays.xml                     Preference list entries and their values
│       ├── layouts.xml                    Keyboard layout name list (auto-generated)
│       └── styles.xml                     UI styles for keyboard components
│
├── assets/
│   ├── special_font.ttf                   Custom TTF symbol font (built from SVG sources via FontForge)
│   ├── dictionaries/english.txt           English word dictionary for word suggestions
│   └── typing_master/                     Bundled Typing Master web assets
│
├── srcs/layouts/                          60+ keyboard layout XML definition files
│
├── test/juloo.keyboard2/                  Unit tests
│   ├── ComposeKeyTest.java
│   ├── KeyValueParserTest.java
│   ├── KeyValueTest.java
│   └── ModmapTest.java
│
├── doc/
│   ├── Custom-layouts.md                  Guide to writing custom keyboard layout XML files
│   └── Possible-key-values.md             Reference for all supported key value names
│
├── fastlane/metadata/android/             Store metadata for 20+ locales (Google Play / F-Droid)
├── AndroidManifest.xml                    App manifest — permissions, services, activities, widget
├── build.gradle.kts                       Gradle build configuration (Kotlin DSL)
└── README.md                              This file
```

---

## Advantages

- **Completely private** — no internet permission, no data collection, no analytics, no advertisements
- **Offline-first** — every single feature works without any network connection
- **Open source** — fully auditable code, based on the juloo.keyboard2 engine
- **Multi-script** — 60+ keyboard layouts covering Latin, Cyrillic, Arabic, Hebrew, Devanagari, Bengali, Greek, Georgian, Armenian, Hangul, Kannada, and more
- **Swipe-based input** — access up to 9 characters per physical key via directional swipes, eliminating the need to switch layouts constantly for symbols and punctuation
- **Clipboard history** — never lose copied text again; accessible inline from the keyboard without switching apps, with search, edit, pin, and share features
- **Home-screen widget** — your clipboard is always one glance away on your home screen
- **Floating clipboard widget** — a draggable overlay puts clipboard access above every app at any time
- **Typing trainer** — built-in WPM-scored typing practice with real-time character validation and audio feedback
- **Developer console** — a complete floating log monitor with filtering, selection, saving, and styled PDF export that works above any running app
- **Backup & Restore** — all settings and clipboard history are portable via a single JSON file, exportable in three ways
- **Foldable support** — separate layout, height, and margin configurations for folded and unfolded foldable device states
- **Highly themeable** — 17 built-in themes including Monet dynamic color, ePaper, Rosé Pine, Everforest, Cobalt, and more
- **Direct-boot aware** — keyboard is fully usable immediately after device boot, even before the device is unlocked
- **Multi-language UI** — the app interface is translated into 21 languages
