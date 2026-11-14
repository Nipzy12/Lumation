# Android FFmpeg integration (initial module)

The `android/ffmpeg` module is a **native Android library**, not yet an APK. It is designed to be wired into a future Capacitor bridge / Lumation editor.

## Dependency

`dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.7` from Maven Central. This is a community-maintained Android continuation of FFmpegKit, under the same `com.arthenica.ffmpegkit` Java package. The legacy `com.arthenica` releases were retired.

- Target: Android arm64-v8a (Realme 8 Pro), minSdk 24.
- Intended: offline media operations, MP4 export, audio and format conversion.
- Current helper: `LumationFfmpeg.exportH264Mp4(inputFile, outputFile, onComplete)`.
- It uses Android `h264_mediacodec`; hardware encoder capability and quality must be tested on real devices.
- Completion occurs on an FFmpeg worker thread. Dispatch to the Android UI thread when updating views.
- `inputFile` and `outputFile` are native filesystem paths. Android `content://` URIs need to be copied from / to `ContentResolver` or handled via a suitable protocol. Do not pass a gallery URI as a raw file path.

## Build module

Install JDK 17, Android SDK platform 35, Gradle 8.9. Then, from the repo root:

```bash
gradle -p android :ffmpeg:assembleDebug
```

This builds an AAR library at `android/ffmpeg/build/outputs/aar/`, **not a usable installable APK**.

## Later tasks

1. Add a Capacitor Android plugin to handle permission-free Android document picker import / save.
2. Add Media3 Transformer with H.264 hardware export and fallback for codecs the device lacks.
3. Add cancellation, progress reporting, background/foreground task lifecycle, and work queue.
4. Connect the timeline composition result from Remotion to the export pipeline.
5. Build a demonstration APK and test it on Realme 8 Pro.
6. Check FFmpeg and bundled codec licenses, license notices and LGPL obligations before distribution.

## Why no legacy FFmpegKit?

The original `arthenica/ffmpeg-kit` is retired. The project also references `FFmpegKitNext` as a source-distributed continuation; choose and audit the final runtime after CI and device tests.

For small editing tasks and H.264 hardware exports, official Android Media3 Transformer may be more efficient than FFmpeg. Both can coexist.

No third-party native binaries or GGUF models are checked into this repo.
