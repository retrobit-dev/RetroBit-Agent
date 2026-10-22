#include <jni.h>
#include "llama.h"
#include <algorithm>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>

// Each Kotlin request owns a session and closes it in finally. No global model,
// retained KV cache, network, or executable supplied by an imported file.
namespace {
void initialize_backend() {
    static std::once_flag backend;
    std::call_once(backend, [] { llama_backend_init(); });
}
struct Session {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    llama_sampler *sampler = nullptr;
    std::vector<llama_token> prompt;
    float temperature = 0;
    int offset = 0;
    int past = 0;
    llama_token previous = LLAMA_TOKEN_NULL;
    ~Session() {
        if (sampler) llama_sampler_free(sampler);
        if (ctx) llama_free(ctx);
        if (model) llama_model_free(model);
    }
};
void fail(JNIEnv *env, const char *message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
}
std::string bytes(JNIEnv *env, jbyteArray array) {
    const auto size = env->GetArrayLength(array);
    std::string result(size, '\0');
    env->GetByteArrayRegion(array, 0, size, reinterpret_cast<jbyte *>(result.data()));
    return result;
}
jbyteArray output(JNIEnv *env, const std::string &value) {
    auto out = env->NewByteArray(static_cast<jsize>(value.size()));
    if (out) env->SetByteArrayRegion(out, 0, value.size(), reinterpret_cast<const jbyte *>(value.data()));
    return out;
}
Session *session(jlong handle) {
    if (!handle) throw std::runtime_error("Sesi model tidak aktif.");
    return reinterpret_cast<Session *>(handle);
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_dev_retrobit_agent_local_NativeBridge_open(JNIEnv *env, jobject, jbyteArray path, jint context, jint threads, jfloat temperature) {
    try {
        initialize_backend();
        auto s = std::make_unique<Session>();
        auto mp = llama_model_default_params();
        mp.n_gpu_layers = 0; // portable CPU backend; no promise of MediaTek NPU acceleration
        s->model = llama_model_load_from_file(bytes(env, path).c_str(), mp);
        if (!s->model) throw std::runtime_error("GGUF tidak didukung, rusak, atau memori tidak cukup. Coba model 1.5B Q4.");
        auto cp = llama_context_default_params();
        cp.n_ctx = std::clamp<int>(context, 512, 4096);
        cp.n_ctx = std::min<uint32_t>(cp.n_ctx, llama_model_n_ctx_train(s->model));
        cp.n_batch = 128;
        cp.n_ubatch = 128;
        cp.n_threads = cp.n_threads_batch = std::clamp<int>(threads, 1, 4);
        s->ctx = llama_init_from_model(s->model, cp);
        if (!s->ctx) throw std::runtime_error("Memori tidak cukup untuk konteks. Turunkan konteks atau pilih model lebih kecil.");
        s->temperature = temperature;
        return reinterpret_cast<jlong>(s.release());
    } catch (const std::exception &e) { fail(env, e.what()); return 0; }
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_retrobit_agent_local_NativeBridge_begin(JNIEnv *env, jobject, jlong handle, jobjectArray roles, jobjectArray contents, jbyteArray grammar) {
    try {
        auto s = session(handle);
        const int count = env->GetArrayLength(roles);
        if (count != env->GetArrayLength(contents)) throw std::runtime_error("Pesan tidak valid.");
        std::vector<std::string> roleText, contentText;
        roleText.reserve(count); contentText.reserve(count);
        for (int i = 0; i < count; ++i) {
            auto role = static_cast<jbyteArray>(env->GetObjectArrayElement(roles, i));
            auto content = static_cast<jbyteArray>(env->GetObjectArrayElement(contents, i));
            roleText.push_back(bytes(env, role)); contentText.push_back(bytes(env, content));
            env->DeleteLocalRef(role); env->DeleteLocalRef(content);
        }
        std::vector<llama_chat_message> messages;
        for (int i = 0; i < count; ++i) messages.push_back({roleText[i].c_str(), contentText[i].c_str()});
        auto tmpl = llama_model_chat_template(s->model, nullptr);
        if (!tmpl) throw std::runtime_error("Model tidak punya chat template. Pilih model Instruct GGUF.");
        int size = llama_chat_apply_template(tmpl, messages.data(), count, true, nullptr, 0);
        if (size < 0) throw std::runtime_error("Chat template model belum didukung. Coba Qwen2.5 Instruct GGUF.");
        std::string prompt(size, '\0');
        int rendered = llama_chat_apply_template(tmpl, messages.data(), count, true, prompt.data(), size);
        if (rendered < 0 || rendered > size) throw std::runtime_error("Gagal menyiapkan prompt.");
        const auto vocab = llama_model_get_vocab(s->model);
        int n = -llama_tokenize(vocab, prompt.data(), rendered, nullptr, 0, true, true);
        if (n <= 0 || n >= static_cast<int>(llama_n_ctx(s->ctx)) - 16)
            throw std::runtime_error("Percakapan melebihi konteks lokal. Mulai chat baru atau pendekkan pesan.");
        s->prompt.resize(n);
        if (llama_tokenize(vocab, prompt.data(), rendered, s->prompt.data(), n, true, true) < 0)
            throw std::runtime_error("Gagal membaca token prompt.");
        s->sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
        const auto grammar_text = bytes(env, grammar);
        if (!grammar_text.empty()) {
            auto filter = llama_sampler_init_grammar(vocab, grammar_text.c_str(), "root");
            if (!filter) throw std::runtime_error("Grammar tool lokal tidak valid.");
            llama_sampler_chain_add(s->sampler, filter);
        }
        if (s->temperature <= 0) {
            llama_sampler_chain_add(s->sampler, llama_sampler_init_greedy());
        } else {
            llama_sampler_chain_add(s->sampler, llama_sampler_init_top_k(40));
            llama_sampler_chain_add(s->sampler, llama_sampler_init_top_p(0.95f, 1));
            llama_sampler_chain_add(s->sampler, llama_sampler_init_temp(s->temperature));
            llama_sampler_chain_add(s->sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
        }
        return n;
    } catch (const std::exception &e) { fail(env, e.what()); return 0; }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_dev_retrobit_agent_local_NativeBridge_next(JNIEnv *env, jobject, jlong handle) {
    try {
        auto s = session(handle);
        // One bounded batch per JNI call lets Kotlin observe Stop between batches.
        if (s->offset < static_cast<int>(s->prompt.size())) {
            int n = std::min(128, static_cast<int>(s->prompt.size()) - s->offset);
            if (llama_decode(s->ctx, llama_batch_get_one(s->prompt.data() + s->offset, n)) != 0)
                throw std::runtime_error("Gagal memproses prompt lokal.");
            s->offset += n; s->past += n;
            if (s->offset < static_cast<int>(s->prompt.size())) return output(env, "");
        } else if (s->previous != LLAMA_TOKEN_NULL) {
            if (s->past >= static_cast<int>(llama_n_ctx(s->ctx)) - 1) return nullptr;
            if (llama_decode(s->ctx, llama_batch_get_one(&s->previous, 1)) != 0)
                throw std::runtime_error("Gagal menghasilkan token lokal.");
            ++s->past;
        }
        auto token = llama_sampler_sample(s->sampler, s->ctx, -1);
        const auto vocab = llama_model_get_vocab(s->model);
        if (llama_vocab_is_eog(vocab, token)) return nullptr;
        s->previous = token;
        std::vector<char> piece(256);
        int n = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
        if (n < 0) {
            piece.resize(-n);
            n = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
        }
        if (n < 0) throw std::runtime_error("Gagal membaca keluaran model.");
        return output(env, std::string(piece.data(), n));
    } catch (const std::exception &e) { fail(env, e.what()); return nullptr; }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_retrobit_agent_local_NativeBridge_close(JNIEnv *, jobject, jlong handle) {
    delete reinterpret_cast<Session *>(handle);
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_retrobit_agent_local_NativeBridge_probe(JNIEnv *env, jobject) {
    try {
        initialize_backend();
        return env->NewStringUTF("Mesin CPU siap (llama.cpp b5046). Model belum diuji.");
    } catch (const std::exception &e) { fail(env, e.what()); return nullptr; }
}
