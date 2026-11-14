# Lumation Android v0.1 demo

This is a **test APK** built as a minimal native Android video editor. It is NOT the final Alight Motion-style multilayer editor, and does NOT yet ship AI, Remotion integration, or a GGUF model.

## Features implemented

- Native dark portrait video editor UI with a single video track.
- Android document picker: import a user-selected video without broad gallery permission.
- Native VideoView preview and play/pause when Android supports decoding the selected format.
- Interactive seekable clip timeline.
- Start and end trim controls.
- FFmpegKit offline video transcoding to MP4, with an H.264 Android hardware-encoder attempt followed by an MPEG-4 Part 2 software fallback.
- Export to Android MediaStore: `Movies/Lumation` in the gallery on Android 10+.

## Install (test-only)

1. Open the latest successful `Android FFmpeg module` GitHub Actions workflow on branch `feat/android-ffmpeg`.
2. Download the `lumation-demo-apk` Actions artifact (ZIP), extract it and install the contained `app-debug.apk`.
3. The debug APK is unsigned for release and is intended only for local testing. Android may display an installation warning. Do not distribute a debug build as a production release.
4. On a Realme 8 Pro, tap `+ Media` to select a video; press play, adjust trim sliders and tap `Export MP4`.
5. Check the Photos/Gallery app or the `Movies/Lumation` folder for output.

Note: the artifact is available only once the CI workflow succeeds. This document is not confirmation that a physical-device export test passed.

## Limitations and tests

- Only a single actual media track exists in v0.1. There is no AI, text layer, audio mixer, compositing or Remotion rendering yet.
- Preview uses Android's native video decoder. For formats unsupported by VideoView, FFmpeg export can still be attempted.
- FFmpeg export may need considerable CPU and battery. No cancel/progress callback yet, and screen backgrounding is not managed through a foreground service.
- The H.264 hardware encoder may not initialize on every device; fallback uses MPEG-4 Part 2 video codec inside MP4, not H.264.
- The program copies imported videos into the app's internal storage. Selecting a large video requires additional free storage.
- Android 10+ only; arm64-v8a APK; targetSdk 35.
- Validate real-device testing: short 720p and 1080p video with audio, no-audio clip, portrait clip, seek, trim boundaries, save to gallery and playback in external gallery.
- As with any FFmpeg distribution, review the native codec build's LGPL/GPL licenses, notices and third-party obligations before public commercial release.

## Roadmap

Next: clip thumbnails, multiple independent video/audio tracks, keyframe editor, caption layers, render pipeline / React-Remotion bridge, local llama.cpp GGUF model downloader, custom AI API settings, optimized exports via Android Media3.

The current native app is an integration and reliability milestone for the later hybrid editor.
