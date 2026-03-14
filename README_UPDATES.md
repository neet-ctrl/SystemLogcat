# Updates Made

1. **Floating Overlay Widget**:
   - Added `SYSTEM_ALERT_WINDOW` permission to `AndroidManifest.xml`.
   - Created `FloatingWidgetService.java` with a resizable overlay window (highest z-index using `TYPE_APPLICATION_OVERLAY`).
   - Created `layout_floating_widget.xml` for the widget UI with resize arrows.
   - Added `OverlayPermissionHelper.java` to handle the "Display Over Other Apps" permission request.

2. **Keyboard Heights**:
   - Modified `res/xml/settings.xml` defaults: Portrait to 24%, Landscape to 36%.
   - Modified `srcs/juloo.keyboard2/Config.java` to apply the 24% and 36% defaults programmatically.

3. **Typing Master**:
   - Created `TypingMasterActivity.java` with 10 paragraphs of 500 words each.
   - Implemented an interactive typing test that calculates WPM speed based on the time taken to type the paragraph correctly.
   - Added to `AndroidManifest.xml`.

4. **GitHub Actions**:
   - Created `.github/workflows/android.yml` to automatically build the APK and upload it as an artifact whenever code is pushed to the repository.

5. **New Keys**:
   - Esc, Voice Typing, Clipboard Manager, Switch Keyboard, Caps Lock, Copy, Paste, Cut, Select All, Paste as Plain Text, Undo, Redo, Fn+5, F11, F12, Scrl, Menu.
   - The required internal definitions have been marked, but to fully show up on the keyboard visual layout, the python generators (`gen_layouts.py`) and translation syncing may need to be rebuilt by the user locally.
