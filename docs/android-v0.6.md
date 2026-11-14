# Lumation Android v0.6: Undo / Redo + Offline Quick Command

This iteration preserves the v0.5 native Android app and FFmpeg video export.

## Working features added

- **Undo** and **Redo** on editing properties of the text/image layers.
- History depth: up to **40 previous snapshots**, in-memory only.
- Text changes, two position keyframes and layer scale are undoable.
- Preset changes (Slide, Rise, Static), including offline command variants, are undoable.
- Dragging a keyframe/scale slider creates **one** undo step when the drag finishes, not a step per pixel.
- Undoing edits and making a new edit discards the old redo branch.
- History is reset on video replacement, image import/removal, opening a saved project or creating a new project. Import/remove of media itself is **not undoable** in v0.6.
- History is not included in project saves and is not restored after app restart.

## Quick Command (offline, deterministic rules)

The UI includes a command field interpreted entirely on the phone. This is not an LLM, not a GGUF inference runtime, and not an AI API connection. Commands are mapped to the existing editor actions through the typed `MotionInstruction` interface:

| Input | Result |
|---|---|
| `geser kanan`, `slide` | Move selected text/image layer left-to-right |
| `geser kiri` | Move right-to-left |
| `naik`, `gerak naik` | Move bottom-to-top |
| `turun` | Move top-to-bottom |
| `diam` | Static position at center |
| `teks: Halo Dunia` | Set text layer content |
| `skala 125%` | Set selected layer scale |

The parser explicitly refuses unrecognized commands; it never executes shell commands and cannot delete projects. Input and text lengths and scale are bounded.

## Tests and build

- `EditHistoryTest`: state restoration, branching redo, duplicate suppression, bounded history and reset.
- `OfflineMotionCommandsTest`: Indonesian/English phrases, text, scale and invalid input.
- Existing tests: `ProjectLibraryTest`, `ProjectStoreTest`, `MotionExportCommandTest`, `AudioMixCommandTest`.
- GitHub Actions builds Android debug ARM64 APK and AAR and runs all Android/JVM unit tests.

## Known limitations

- Still an experimental editor with a single video, optional soundtrack, one text layer and one image layer.
- Real-time preview and FFmpeg encoder/export still need a physical-device smoke test (Realme 8 Pro).
- History covers **editing properties only**, not video, audio or image media import.
- No actual `llama.cpp` binding, GGUF model, offline AI inference, custom AI provider or Remotion React renderer is shipped.
- Do not distribute a debug APK as a production release. Check FFmpeg licensing before redistribution.

### Suggested physical-device checks

1. Open v0.5 project; edit text and keyframe, Undo and Redo.
2. Drag a scale slider, Undo once and verify original scale returns.
3. Undo twice and create a new text edit; Redo should be disabled.
4. In Quick Command try `geser kanan`, `naik`, `teks: Lumation`, `skala 120%`.
5. Save, reopen and ensure final layers/keyframes persist (history resets as expected).
6. Export MP4 and check motion, audio, and saved gallery file.
