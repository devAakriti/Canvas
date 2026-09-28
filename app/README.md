# Canvas — what's new

Your `MainActivity.kt` now wires up real functionality instead of static buttons. Four new/changed files, all in `app/src/main/java/com/example/canvas/`:

- **MainActivity.kt** — rewritten. Same top bar / preview / bottom toolbar layout you had, now with working state and an Export button.
- **EditorModels.kt** — new. Small data classes (`TextOverlayItem`, `EditorTool`, `ExportState`).
- **EditorPanels.kt** — new. The Trim / Text / Audio panels that slide in when you tap a toolbar icon, plus the draggable caption layer over the preview.
- **VideoExportManager.kt** — new. Uses Media3 **Transformer** to actually bake the trim, captions, and audio into a final MP4 and save it to `Movies/Canvas`.
- **app/build.gradle.kts** — added `media3-transformer`, `media3-effect`, `media3-common` (same 1.11.1 version as your existing exoplayer/ui deps, so no version mismatches).

## What each tool does

| Tool | In the live preview | On export |
|---|---|---|
| **Trim** | Drag a two-handed slider to pick start/end | Video is clipped to that range |
| **Text** | Type a caption, drag it anywhere on the preview | Baked into the video as a real overlay at the same relative position |
| **Audio** | Pick a track, toggle "mute original audio" | Mixed in (or replaces original audio) in the export |
| **Export** (top-right) | — | Runs everything above through Transformer, shows a progress bar, saves the result to your device's Movies/Canvas folder and closes the loop with a success/error message |

Text captions are **not** mixed into the live ExoPlayer preview (that would need a much heavier renderer) — they show as a Compose overlay on top of the video so you can see and position them, and get properly rendered into the pixels only when you hit Export. That's the normal approach beginner editors use.

## One manifest thing to add

Open `app/src/main/AndroidManifest.xml` and add this permission for devices on Android 9 and below (Android 10+ doesn't need it):

```xml
<uses-permission
    android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
```

Nothing else needs to change there — the video/audio pickers use the system file picker, which doesn't need a runtime permission.

## Things to know before you build

- **Media3 API surface moves around between versions.** This is written against 1.11.x (matching your existing exoplayer/ui version). If Android Studio underlines a method in `VideoExportManager.kt` in red, it's almost always a same-purpose call that got renamed — right-click → "Go to declaration" on the class (e.g. `EditedMediaItem.Builder`) to see what's actually available in your version, or check the [Media3 Transformer docs](https://developer.android.com/media/media3/transformer).
- **Export can take a while** on longer clips since it's real encoding, not a preview trick — that's what the progress bar is for.
- **First real feature to add next**, if you want to keep going: a proper timeline scrubber thumbnail strip for Trim, since a plain slider gets awkward on long videos.

## How to add it to your project

Copy the four `.kt` files into `app/src/main/java/com/example/canvas/` (overwriting your current `MainActivity.kt`), replace `app/build.gradle.kts` with the updated one, add the manifest permission above, then **Sync Project with Gradle Files**.