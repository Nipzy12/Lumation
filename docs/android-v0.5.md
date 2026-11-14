# Lumation Android v0.5: Project Library

## What changed

The v0.4 single local project slot has been upgraded to a multi-project library under the app's private storage. All features from the previous demo remain available: single video clip, audio mix, editable text/image layers, start/end position keyframes, motion presets and FFmpeg export.

- **New** clears the current working editor after confirmation. Saved projects are untouched.
- **Save** updates the currently selected saved project, or asks for a title when saving for the first time.
- **Save As** creates an independent new project with its own video/audio/image copies.
- **Projects** lists saved projects newest first, and prompts before discarding unsaved edits on open.
- **Delete** removes only the selected saved project after confirmation, leaving the current editor content open but unsaved.

### On-disk layout

```
files/projects/
  current/                        # v0.4 backup, never modified during migration
  legacy-imported                 # one-time marker after import
  library/
    <project-id>/
      meta.json
      current/
        project.json
        video.mp4
        audio.mp3
        image.png
```

Each project is saved via its own `ProjectStore` staging/backup directory. Media is copied into the target project folder and is subsequently copied into a working folder when reopened. IDs are UUIDs; project titles never become filesystem paths. Traversal-style IDs are rejected.

### Upgrade from v0.4

Open the **Projects** browser once. The previous `files/projects/current` single-slot project is automatically imported as **Proyek lama v0.4**, while the original v0.4 slot remains as an untouched backup. Deleting the imported entry does not re-import it later.

### Important limitations

- App-private storage: uninstall / clear data deletes saved projects. There is no exported `.lumation` project archive or cloud synchronization.
- **No autosave**. Use Save before switching projects.
- In-app editor remains experimental. Realme 8 Pro needs real-device testing for media playback, export and lifecycle/restore.
- Separate projects copy their media and can significantly increase storage usage.
- Still only one video, one music, one text and one image layer. There are no undo/redo stacks, real arbitrary layer counts, or general animation property keyframes.
- No Remotion runtime, on-device GGUF model, or custom AI API integration yet.

## Test plan

1. Install debug APK and import a short MP4. Add text/image/audio, then **Save** as "Test A".
2. Change text, select **Save As** and name it "Test B".
3. Open **Projects**; confirm both entries exist and load distinct content.
4. Edit Test B, press **Save**, then verify Test A was unchanged.
5. Delete Test B, then confirm Test A remains in the library.
6. Test **New** and verify unsaved changes are reset without deleting saved projects.
7. If upgrading from v0.4 with one saved project, check the "Proyek lama v0.4" migration entry.

## Automated checks

CI runs JVM tests for ProjectStore (media copies, keyframes, save overwrite), ProjectLibrary (isolated multi-project CRUD, migration and ID validation), MotionExportCommand and AudioMixCommand, followed by a debug APK build. Device runtime behavior is not covered by these tests.
