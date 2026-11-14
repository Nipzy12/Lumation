package com.lumavun.lumation

/**
 * llama.cpp JNI interface.
 * All native calls are synchronous and must run on a worker thread.
 * The native backend serializes load/inference/unload with a mutex.
 */
object LocalLlamaBridge {
    private val failure: Throwable? = try {
        System.loadLibrary("lumation_ai")
        null
    } catch (error: UnsatisfiedLinkError) {
        error
    } catch (error: SecurityException) {
        error
    }

    val isAvailable: Boolean get() = failure == null
    val loadFailureMessage: String? get() = failure?.message

    external fun nativeLoad(modelPath: String): Boolean
    external fun nativeInfer(prompt: String): String
    external fun nativeUnload()
    external fun nativeVersion(): String
}
