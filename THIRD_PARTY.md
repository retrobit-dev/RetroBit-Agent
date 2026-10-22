# Native inference dependency

The on-device CPU backend uses **llama.cpp**, MIT license, pinned to commit
`74d4f5b041ad837153b0e90fc864b8290e01d8d5` (b5046). CMake downloads the source
from codeload.github.com and verifies SHA-256
`8dd9fcc7c17f972673960ffdcc7521f52de5a9ec594aa6acebd9dfd8c17c0a5c`.
Source: https://github.com/ggml-org/llama.cpp
The upstream license is included in the APK assets (`llama-LICENSE.txt`).
The backend is CPU-only, statically linked into `libretrobit_local.so`, with
ARM64 and x86_64 builds and 16 KB ELF segment alignment. This does not use
MediaTek's NPU or require another app/server.

Models are NOT bundled, downloaded automatically, or covered by this license.
Users must obtain an instruct GGUF from a trusted publisher and comply with
its license. GGUF imports are bounded and header-checked; full compatibility
is established only when llama.cpp loads the model. Do not import untrusted
models. Updating the native backend requires review and regression testing;
formats/architectures added after this pin may not work.
