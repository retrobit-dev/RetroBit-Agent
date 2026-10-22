cmake_minimum_required(VERSION 3.22.1)
project(retrobit_local LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
include(FetchContent)
# Reproducible, audited source archive, not a moving branch. See THIRD_PARTY.md.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
FetchContent_Declare(llama
    URL https://codeload.github.com/ggml-org/llama.cpp/tar.gz/74d4f5b041ad837153b0e90fc864b8290e01d8d5
    URL_HASH SHA256=8dd9fcc7c17f972673960ffdcc7521f52de5a9ec594aa6acebd9dfd8c17c0a5c)
FetchContent_MakeAvailable(llama)
add_library(retrobit_local SHARED local_jni.cpp)
target_link_libraries(retrobit_local PRIVATE llama android log)
# Android 15/16 devices with 16 KB memory pages.
target_link_options(retrobit_local PRIVATE "-Wl,-z,max-page-size=16384")
