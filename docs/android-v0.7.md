# Lumation Android v0.7: Offline GGUF AI using llama.cpp

This update adds an **actual local inference engine**, not a fake API stub. A real model must be imported by the user; no GGUF weights or user data are uploaded or bundled in the APK.

## Android architecture

- `LocalLlamaBridge.kt` calls `liblumation_ai.so` via JNI; the native library is built directly from pinned `ggml-org/llama.cpp`.
- `GgufModelStore.kt` imports selected GGUF files from Android's SAF document picker into private app storage, validates GGUF magic and an upper size limit (1.5 GiB).
- The C++ runtime loads the model once into memory and runs CPU inference off the UI thread, with a 1024-token context and up to 80 newly generated tokens.
- `AiSuggestion.kt` only accepts **one single recognized editor instruction**, such as `geser kanan`, `naik`, `teks: ...`, or `skala 125%`.
- The AI's suggestion is placed in the existing Quick Command field. It **does not edit the project until the user presses Apply**.
- Prior v0.6 Undo/Redo, project library, media preview, and offline FFmpeg export remain.
- The fallback Quick Command rule parser works even with no model installed.

### Recommended model for Realme 8 Pro

Start with [Qwen2.5-0.5B-Instruct-GGUF Q4_K_M](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/blob/main/qwen2.5-0.5b-instruct-q4_k_m.gguf), approximately **491 MB**. The GGUF model must be downloaded separately, in accordance with its license.

**Minimum practical setup:** a working ARM64 phone, at least ~1.5 GB spare storage before import (download + app-private copy + export files), with sufficient available RAM after the OS and video editor are running. Actual memory/performance depends on device and model quantization. CPU inference on Snapdragon 720G may be slow. Close other apps before testing and use short prompts.

### How to test

1. Install the debug APK as **Lumation AI Lab**. The package ID is `com.lumavun.lumation.dev`, separate from the previously installed `com.lumavun.lumation`. Both can run side by side, protecting existing v0.6 app-private project data. This AI Lab package has **its own empty project library** and cannot read the old app's private files.
2. Download the Q4_K_M GGUF separately using the browser.
3. Open **Import GGUF**, select the downloaded `.gguf`; the model is copied to `files/ai-models/active.gguf`.
4. Tap **Load Model** to initialize the local model in RAM. If the device is low on RAM or the model is unsupported, a failure will be shown instead of claiming AI is ready.
5. Type an instruction such as `Tolong buat teks bergerak naik` into the Quick Command field, then tap **AI Suggest (Offline)**. Generation stays offline.
6. If a recognized suggestion appears, review the proposed command and then press the existing **Apply** button to edit your layer.
7. Tap **Unload** to free the model's RAM before exporting a large video, or **Remove GGUF** to reclaim app storage.

### Build from source

GitHub Actions fetches exactly upstream llama.cpp commit `a956a852c9c8bc1fb07206364838ddef299178c0`, then uses Android NDK r27c and CMake 3.22.1 to compile an ARM64 JNI library.

Local Android Studio builds require JDK 17, Android SDK 35, NDK `27.2.12479018`, CMake `3.22.1`, and an upstream checkout at `android/third_party/llama.cpp` (run `bash scripts/setup-llama-source.sh`). Import the `android` Gradle project.

### Security and limitations

- Local inference does not enable unrestricted arbitrary code execution. Model text is interpreted as data, and only recognized animation commands can be manually applied.
- The selected GGUF file is untrusted data; import checks magic/size, but the actual model compatibility is determined by llama.cpp load. Use models from reputable sources.
- This is **not** a full conversational AI agent, general scene generator, multimodal model, voice assistant or Remotion renderer. It suggests one command at a time.
- The v0.7 debug APK deliberately uses a separate `.dev` package and launcher name `Lumation AI Lab` to avoid replacing your working v0.6 installation. It starts with separate empty app-private storage. A later release needs stable signing and an explicit project export/import migration before merging packages.
- The debug APK is not a production release; audit native dependencies, app signing, model licensing and on-device memory/performance first.
- GitHub build/CI passing only establishes compilation and JVM test success. **Real GGUF inference on Realme 8 Pro is not hardware-verified until tested on a real device.**
- The app does not ship a default model and has no internet/download permission. Models must be imported via Android's document picker.
- Models are stored in app-private data and disappear upon uninstall/clear data.
