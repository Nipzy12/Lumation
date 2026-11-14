# Lumation

**Lumation** is an in-development Android motion editor with a multilayer timeline, Remotion-based animations, offline AI, and native video tools.

## Status
- [x] Repository initialized
- [x] Android single-video timeline and separate audio track (v0.2 demo)
- [ ] AI on-device (GGUF / llama.cpp)
- [ ] Remotion composition adapter
- [ ] FFmpeg Android integration build-tested on a real device
- [x] Multi-project library, text/image layer edits, and offline quick commands (debug prototype)
- [ ] Device-verified APK: video import, soundtrack mixing, preview, trim and MP4 export

The experimental FFmpeg Android library is developed on the `feat/android-ffmpeg` branch before inclusion in the main application. It does **not** yet constitute a working APK.

## Planned stack
React + TypeScript + Capacitor frontend, Kotlin Android native modules, FFmpegKit maintained fork for offline media operations, and optional Android Media3 for hardware-accelerated H.264 export.

License caution: FFmpeg/FFmpegKit builds have LGPL/GPL obligations. The project uses a non-GPL dependency initially and will review bundled codec licenses before distributing an APK.
