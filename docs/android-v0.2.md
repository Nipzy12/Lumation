# Lumation Android v0.2 test APK

## Implemented

- Native Android dark editor with **two real media tracks**: one video clip, one optional looping music track.
- Video picker and audio picker use Android's storage access framework. Both media files are copied to private app storage.
- Preview: video plays through Android VideoView, optional soundtrack plays through MediaPlayer; soundtrack seeks with the video playhead and pauses with video playback.
- Video timeline remains a *single* video track with trim handles; soundtrack is displayed as a second lane.
- Music gain slider from 0–100%, remove/replace soundtrack.
- FFmpegKit export: trims video and either retains original sound, replaces silence with imported music, or mixes original sound with the music. Imported music loops to fill the selected trim range. Output MP4 saved to Movies/Lumation.
- Pure audio mixer FFmpeg argument builder with JVM tests for normal video, silent video, validation, and codec fallback argument selection.
- GitHub Actions builds a debug APK and FFmpeg AAR.

## Installation

Visit the latest **successful** Android FFmpeg module workflow on the feat/android-ffmpeg branch, then download the artifact named `lumation-demo-apk`, unzip it and install `app-debug.apk` on an ARM64 Android 10+ device.

Test with a *short*, non-sensitive clip first, ideally under 30 seconds. Tap **+ Media**, then **+ Audio**; play and seek in the timeline, adjust volume and trim, then export MP4. Check Movies/Lumation. Both import and export can consume substantial disk space.

## Known limitations

- **No real-device validation yet:** successful Gradle compilation does not prove hardware codec export or MediaPlayer synchronization works on Realme 8 Pro.
- The v0.2 timeline displays one video lane and one music lane, not general-purpose Alight Motion-style multilayer editing.
- Soundtrack playback is a lightweight preview: preview mixing/latency can differ from final FFmpeg output. Source audio and soundtrack are mixed with FFmpeg's normalized amix filter on export, while live preview plays two separate Android players.
- If H.264 hardware encoding fails, the FFmpeg fallback uses MPEG-4 Part 2 in an MP4 container. Some platforms may not accept the fallback codec.
- No thumbnail waveform, editing history, project saves, caption layers, keyframes, text animations, Remotion integration, on-device AI, or custom AI API **yet**.
- Current export lacks a cancel button, timed progress updates and background service.
- When a short soundtrack is used, FFmpeg loops it throughout the video trim range.
- Phone runtime testing, codec license due diligence, and Play Store release compliance are required before production.

## Suggested Realme 8 Pro smoke test

1. Use a portrait MP4 (720p, around 10 seconds) containing original audio.
2. Add a short MP3 soundtrack and set volume around 40–60%.
3. Preview, pause, scrub, set trim range (for example 1 to 7 seconds), export.
4. Open Movies/Lumation in Gallery and check picture, audio mix, timing and duration.
5. Remove soundtrack and export a video-only test.
6. Test a video with *no* original audio and verify imported music is audible.
7. If export fails, share the status text and logcat; failures may be codec or filter availability related.

## Next

Implement composited image/text layers and keyframe properties, then connect an actual Remotion composition adapter and llama.cpp GGUF model manager. Do not call the v0.2 demo a Remotion app or an AI app yet.
