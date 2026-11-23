#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <cstring>
#include <mutex>
#include <string>
#include <type_traits>
#include <vector>

#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"

#define LOG_TAG "JarvisNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

std::mutex g_mutex;
llama_model * g_model = nullptr;
llama_context * g_ctx = nullptr;
mtmd_context * g_mctx = nullptr;
int32_t g_n_batch = 128;
int32_t g_n_threads = 4;
bool g_backend_ready = false;
std::atomic<bool> g_cancel{false};

// SFINAE compatibility helpers for evolving llama.cpp mtmd structs
template <typename T, typename = void>
struct has_text_len : std::false_type {};
template <typename T>
struct has_text_len<T, std::void_t<decltype(std::declval<T>().text_len)>> : std::true_type {};

template <typename T, typename = void>
struct has_warmup : std::false_type {};
template <typename T>
struct has_warmup<T, std::void_t<decltype(std::declval<T>().warmup)>> : std::true_type {};

mtmd_input_text make_mtmd_input_text(const std::string & text, bool add_special, bool parse_special) {
    mtmd_input_text inp{};
    inp.text = text.c_str();
    if constexpr (has_text_len<mtmd_input_text>::value) {
        inp.text_len = text.size();
    }
    inp.add_special = add_special;
    inp.parse_special = parse_special;
    return inp;
}

void disable_warmup_if_present(mtmd_context_params & params) {
    if constexpr (has_warmup<mtmd_context_params>::value) {
        params.warmup = false;
    }
}

void unload_locked() {
    if (g_mctx) {
        mtmd_free(g_mctx);
        g_mctx = nullptr;
    }
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
}

void emit_bytes(JNIEnv * env, jobject callback, jmethodID method, const std::string & bytes) {
    if (!callback || !method || bytes.empty()) return;
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(bytes.size()));
    if (!arr) return;
    env->SetByteArrayRegion(arr, 0, static_cast<jsize>(bytes.size()),
                            reinterpret_cast<const jbyte *>(bytes.data()));
    env->CallVoidMethod(callback, method, arr);
    env->DeleteLocalRef(arr);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
}

// Returns count of complete valid UTF-8 bytes at start of buffer
size_t complete_utf8_prefix(const std::string & s) {
    size_t i = 0;
    const size_t n = s.size();
    while (i < n) {
        unsigned char c = static_cast<unsigned char>(s[i]);
        size_t need = 1;
        if ((c & 0x80) == 0x00) need = 1;
        else if ((c & 0xE0) == 0xC0) need = 2;
        else if ((c & 0xF0) == 0xE0) need = 3;
        else if ((c & 0xF8) == 0xF0) need = 4;
        else {
            // Invalid leading byte; emit 1 byte to avoid stalling
            i += 1;
            continue;
        }
        if (i + need > n) break;
        i += need;
    }
    return i;
}

std::string jstring_to_std(JNIEnv * env, jstring jstr) {
    if (!jstr) return "";
    const char * chars = env->GetStringUTFChars(jstr, nullptr);
    if (!chars) return "";
    std::string out(chars);
    env->ReleaseStringUTFChars(jstr, chars);
    return out;
}

} // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_jarvis_ai_engine_JarvisNative_nativeLoadModel(
        JNIEnv * env,
        jobject /* thiz */,
        jstring modelPathJ,
        jstring mmprojPathJ,
        jint nCtx,
        jint nBatch,
        jint nThreads) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_backend_ready) {
        llama_backend_init();
        g_backend_ready = true;
    }

    unload_locked();

    const std::string modelPath = jstring_to_std(env, modelPathJ);
    const std::string mmprojPath = jstring_to_std(env, mmprojPathJ);

    if (modelPath.empty()) {
        return env->NewStringUTF("ERR: Путь к модели пустой");
    }

    LOGI("Loading LLM: %s (ctx=%d, batch=%d, threads=%d)", modelPath.c_str(), nCtx, nBatch, nThreads);

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;

    g_model = llama_model_load_from_file(modelPath.c_str(), mparams);
    if (!g_model) {
        LOGE("Failed to load llama model");
        return env->NewStringUTF("ERR: Не удалось загрузить файл модели GGUF");
    }

    g_n_threads = nThreads > 0 ? nThreads : 4;
    g_n_batch = nBatch > 0 ? nBatch : 128;

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = nCtx > 256 ? nCtx : 1024;
    cparams.n_batch = g_n_batch;
    cparams.n_ubatch = g_n_batch;
    cparams.n_threads = g_n_threads;
    cparams.n_threads_batch = g_n_threads;

    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        LOGE("Failed to create llama context");
        unload_locked();
        return env->NewStringUTF("ERR: Не хватило ОЗУ для создания контекста");
    }

    if (!mmprojPath.empty()) {
        LOGI("Loading vision projector: %s", mmprojPath.c_str());
        mtmd_context_params vparams = mtmd_context_params_default();
        vparams.use_gpu = false;
        vparams.print_timings = false;
        vparams.n_threads = g_n_threads;
        disable_warmup_if_present(vparams);

        g_mctx = mtmd_init_from_file(mmprojPath.c_str(), g_model, vparams);
        if (!g_mctx) {
            LOGE("Vision mmproj failed to load; continuing in text-only mode");
            return env->NewStringUTF("WARN: Модель загружена, но зрение (mmproj) не запустилось");
        }
        LOGI("Vision projector loaded successfully");
        return env->NewStringUTF("OK_VISION");
    }

    LOGI("LLM loaded in text-only mode");
    return env->NewStringUTF("OK_TEXT");
}

JNIEXPORT void JNICALL
Java_com_jarvis_ai_engine_JarvisNative_nativeUnload(JNIEnv * /* env */, jobject /* thiz */) {
    g_cancel.store(true);
    std::lock_guard<std::mutex> lock(g_mutex);
    unload_locked();
    LOGI("Model unloaded");
}

JNIEXPORT void JNICALL
Java_com_jarvis_ai_engine_JarvisNative_nativeCancel(JNIEnv * /* env */, jobject /* thiz */) {
    g_cancel.store(true);
}

JNIEXPORT jboolean JNICALL
Java_com_jarvis_ai_engine_JarvisNative_nativeHasVision(JNIEnv * /* env */, jobject /* thiz */) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return g_mctx != nullptr ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_jarvis_ai_engine_JarvisNative_nativeMediaMarker(JNIEnv * env, jobject /* thiz */) {
    const char * marker = mtmd_default_marker();
    return env->NewStringUTF(marker ? marker : "<__media__>");
}

JNIEXPORT jstring JNICALL
Java_com_jarvis_ai_engine_JarvisNative_nativeGenerate(
        JNIEnv * env,
        jobject /* thiz */,
        jstring promptJ,
        jbyteArray rgbBytesJ,
        jint imgWidth,
        jint imgHeight,
        jint maxTokens,
        jfloat temperature,
        jobject callback) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_cancel.store(false);

    if (!g_model || !g_ctx) {
        return env->NewStringUTF("ERR: Модель не загружена");
    }

    const std::string prompt = jstring_to_std(env, promptJ);
    const llama_vocab * vocab = llama_model_get_vocab(g_model);

    // Always clear KV cache before each turn to keep RAM flat & predictable
    llama_memory_clear(llama_get_memory(g_ctx), true);

    jmethodID onBytesMethod = nullptr;
    if (callback) {
        jclass cls = env->GetObjectClass(callback);
        onBytesMethod = env->GetMethodID(cls, "onTokenBytes", "([B)V");
        env->DeleteLocalRef(cls);
    }

    const bool useImage = (rgbBytesJ != nullptr && imgWidth > 0 && imgHeight > 0 && g_mctx != nullptr);

    if (useImage) {
        jsize byteLen = env->GetArrayLength(rgbBytesJ);
        if (byteLen < imgWidth * imgHeight * 3) {
            return env->NewStringUTF("ERR: Неверный буфер изображения");
        }
        std::vector<unsigned char> rgb(static_cast<size_t>(byteLen));
        env->GetByteArrayRegion(rgbBytesJ, 0, byteLen, reinterpret_cast<jbyte *>(rgb.data()));

        mtmd_bitmap * bmp = mtmd_bitmap_init(
            static_cast<uint32_t>(imgWidth),
            static_cast<uint32_t>(imgHeight),
            rgb.data()
        );
        if (!bmp) {
            return env->NewStringUTF("ERR: Не удалось создать mtmd bitmap");
        }

        mtmd_input_chunks * chunks = mtmd_input_chunks_init();
        if (!chunks) {
            mtmd_bitmap_free(bmp);
            return env->NewStringUTF("ERR: Не удалось создать mtmd chunks");
        }

        mtmd_input_text textInput = make_mtmd_input_text(prompt, true, true);
        const mtmd_bitmap * bmps[1] = { bmp };

        int32_t tokRes = mtmd_tokenize(g_mctx, chunks, &textInput, bmps, 1);
        if (tokRes != 0) {
            mtmd_input_chunks_free(chunks);
            mtmd_bitmap_free(bmp);
            return env->NewStringUTF("ERR: Ошибка токенизации изображения");
        }

        llama_pos new_n_past = 0;
        int32_t evalRes = mtmd_helper_eval_chunks(
            g_mctx, g_ctx, chunks, 0, 0, g_n_batch, true, &new_n_past
        );

        mtmd_input_chunks_free(chunks);
        mtmd_bitmap_free(bmp);

        if (evalRes != 0) {
            return env->NewStringUTF("ERR: Ошибка обработки кадра (попробуйте уменьшить размер изображения или увеличить контекст)");
        }
    } else {
        // Pure text inference
        int32_t maxPromptTokens = static_cast<int32_t>(prompt.size()) + 32;
        std::vector<llama_token> tokens(maxPromptTokens);
        int32_t nTokens = llama_tokenize(
            vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
            tokens.data(), maxPromptTokens, true, true
        );
        if (nTokens < 0) {
            tokens.resize(-nTokens);
            nTokens = llama_tokenize(
                vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
                tokens.data(), static_cast<int32_t>(tokens.size()), true, true
            );
        }
        if (nTokens <= 0) {
            return env->NewStringUTF("ERR: Пустой запрос");
        }
        tokens.resize(nTokens);

        for (int32_t i = 0; i < nTokens; i += g_n_batch) {
            if (g_cancel.load()) {
                return env->NewStringUTF("");
            }
            int32_t chunk = std::min(g_n_batch, nTokens - i);
            llama_batch batch = llama_batch_get_one(tokens.data() + i, chunk);
            if (llama_decode(g_ctx, batch) != 0) {
                return env->NewStringUTF("ERR: Ошибка декодирования промпта");
            }
        }
    }

    // Sampler chain tuned for structured JSON responses
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler * smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.90f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature > 0.01f ? temperature : 0.25f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string fullResponse;
    std::string utf8Pending;
    char pieceBuf[256];

    const int32_t limit = maxTokens > 16 ? maxTokens : 256;
    for (int32_t step = 0; step < limit; ++step) {
        if (g_cancel.load()) break;

        llama_token tok = llama_sampler_sample(smpl, g_ctx, -1);
        if (llama_vocab_is_eog(vocab, tok)) break;

        int32_t n = llama_token_to_piece(vocab, tok, pieceBuf, sizeof(pieceBuf), 0, true);
        if (n > 0) {
            std::string piece(pieceBuf, static_cast<size_t>(n));
            fullResponse += piece;
            utf8Pending += piece;

            size_t readyLen = complete_utf8_prefix(utf8Pending);
            if (readyLen > 0) {
                emit_bytes(env, callback, onBytesMethod, utf8Pending.substr(0, readyLen));
                utf8Pending.erase(0, readyLen);
            }

            // Early stop if model prints Qwen stop marker as text
            if (fullResponse.find("<|im_end|>") != std::string::npos) {
                size_t pos = fullResponse.find("<|im_end|>");
                fullResponse.erase(pos);
                break;
            }
        }

        llama_batch batch = llama_batch_get_one(&tok, 1);
        if (llama_decode(g_ctx, batch) != 0) {
            break;
        }
    }

    if (!utf8Pending.empty()) {
        emit_bytes(env, callback, onBytesMethod, utf8Pending);
    }

    llama_sampler_free(smpl);
    return env->NewStringUTF("OK");
}

} // extern "C"
