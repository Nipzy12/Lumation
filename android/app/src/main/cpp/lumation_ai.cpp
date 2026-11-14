#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <mutex>
#include <string>
#include <vector>
#include "llama.h"

namespace {
std::mutex g_lock;
llama_model * g_model = nullptr;
bool g_initialized = false;

std::string jstr(JNIEnv * env, jstring input) {
    if (!input) return "";
    const char * chars = env->GetStringUTFChars(input, nullptr);
    if (!chars) return "";
    std::string value(chars);
    env->ReleaseStringUTFChars(input, chars);
    return value;
}

void unload_locked() {
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
}

// Provide output logits only for the last input token.
bool fill_batch(llama_batch_ext * batch, const llama_token * tokens, int count, int base_pos) {
    llama_batch_ext_clear(batch);
    for (int index = 0; index < count; index++) {
        const int32_t slot = llama_batch_ext_add_token(batch, 0, tokens[index]);
        if (slot < 0) return false;
        const llama_pos pos = base_pos + index;
        if (!llama_batch_ext_set_pos(batch, slot, &pos)) return false;
    }
    return llama_batch_ext_set_output_logits(batch, count - 1, true);
}

std::string format_chat(const llama_model * model, const std::string & prompt) {
    const std::string instruction =
        "You control a tiny offline video motion editor. Respond with ONE LINE only. "
        "Pick exactly one of these commands: 'geser kanan', 'geser kiri', 'naik', "
        "'turun', 'diam', 'teks: <short words>', or 'skala 100%'. "
        "For a scale command, use an integer from 40 to 180. "
        "Do not add explanations, quotes, JSON, markdown, or multiple commands.";
    const llama_chat_message messages[] = {
        {"system", instruction.c_str()},
        {"user", prompt.c_str()}
    };
    const char * templ = llama_model_chat_template(model, nullptr);
    if (templ) {
        const int32_t required = llama_chat_apply_template(templ, messages, 2, true, nullptr, 0);
        if (required > 0 && required < 8192) {
            std::vector<char> buffer(static_cast<size_t>(required) + 1);
            if (llama_chat_apply_template(templ, messages, 2, true, buffer.data(),
                                          static_cast<int32_t>(buffer.size())) >= 0) {
                return std::string(buffer.data(), static_cast<size_t>(required));
            }
        }
    }
    // ChatML fallback for the recommended Qwen 2.5 Instruct GGUF family.
    return "<|im_start|>system\n" + instruction +
           "<|im_end|>\n<|im_start|>user\n" + prompt +
           "<|im_end|>\n<|im_start|>assistant\n";
}

std::string infer_locked(const std::string & request) {
    if (!g_model) return "ERROR: No GGUF model loaded";
    if (request.empty() || request.size() > 1024) return "ERROR: Invalid prompt length";

    llama_context_params params = llama_context_default_params();
    params.n_ctx = 1024;
    params.n_batch = 512;
    params.n_ubatch = 128;
    params.n_threads = 4;
    params.n_threads_batch = 4;
    llama_context * ctx = llama_init_from_model(g_model, params);
    if (!ctx) return "ERROR: Unable to allocate model context (insufficient RAM?)";

    auto fail = [&](const char * message) {
        llama_free(ctx);
        return std::string("ERROR: ") + message;
    };

    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    const std::string input = format_chat(g_model, request);
    const int count = -llama_tokenize(vocab, input.c_str(),
                                      static_cast<int32_t>(input.size()), nullptr, 0, true, true);
    if (count < 1 || count > 480) return fail("Prompt too long for local context");

    std::vector<llama_token> ids(static_cast<size_t>(count));
    if (llama_tokenize(vocab, input.c_str(), static_cast<int32_t>(input.size()),
                       ids.data(), count, true, true) < 0) {
        return fail("Tokenization failed");
    }

    llama_batch_ext * batch = llama_batch_ext_init(ctx);
    if (!batch) return fail("Unable to allocate token batch");

    auto sample_params = llama_sampler_chain_default_params();
    llama_sampler * sampler = llama_sampler_chain_init(sample_params);
    if (!sampler) {
        llama_batch_ext_free(batch);
        return fail("Unable to create sampler");
    }
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    std::string output;
    int position = count;
    bool processed = fill_batch(batch, ids.data(), count, 0) &&
        llama_process(ctx, LLAMA_PROCESS_TYPE_DECODE, batch) == 0;

    for (int generated = 0; processed && generated < 80; ++generated) {
        const llama_token id = llama_sampler_sample(sampler, ctx, -1);
        if (llama_vocab_is_eog(vocab, id)) break;

        std::vector<char> piece(512);
        int n = llama_token_to_piece(vocab, id, piece.data(),
                                      static_cast<int32_t>(piece.size()), 0, false);
        if (n < 0) {
            piece.resize(static_cast<size_t>(-n));
            n = llama_token_to_piece(vocab, id, piece.data(),
                                      static_cast<int32_t>(piece.size()), 0, false);
        }
        if (n < 0) { processed = false; break; }
        output.append(piece.data(), static_cast<size_t>(n));
        if (output.find('\n') != std::string::npos || output.size() > 512) break;

        processed = fill_batch(batch, &id, 1, position) &&
            llama_process(ctx, LLAMA_PROCESS_TYPE_DECODE, batch) == 0;
        position++;
    }
    llama_sampler_free(sampler);
    llama_batch_ext_free(batch);
    llama_free(ctx);
    if (!processed) return "ERROR: Inference failed (context or token buffer)";
    if (output.empty()) return "ERROR: Model returned no output";
    return output;
}
} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_lumavun_lumation_LocalLlamaBridge_nativeLoad(JNIEnv * env, jobject, jstring path) {
    std::lock_guard<std::mutex> guard(g_lock);
    const auto name = jstr(env, path);
    if (name.empty()) return JNI_FALSE;
    if (!g_initialized) {
        llama_backend_init();
        g_initialized = true;
    }
    // Only one model at a time, to keep memory use predictable on Android.
    unload_locked();
    llama_model_params params = llama_model_default_params();
    params.n_gpu_layers = 0;
    g_model = llama_model_load_from_file(name.c_str(), params);
    return g_model ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_lumavun_lumation_LocalLlamaBridge_nativeInfer(JNIEnv * env, jobject, jstring request) {
    std::lock_guard<std::mutex> guard(g_lock);
    const std::string response = infer_locked(jstr(env, request));
    return env->NewStringUTF(response.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_lumavun_lumation_LocalLlamaBridge_nativeUnload(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> guard(g_lock);
    unload_locked();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_lumavun_lumation_LocalLlamaBridge_nativeVersion(JNIEnv * env, jobject) {
    return env->NewStringUTF(llama_version());
}
