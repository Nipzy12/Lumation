cmake_minimum_required(VERSION 3.22.1)
project(lumation_ai LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "llama libraries are linked into our JNI .so" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS OFF CACHE BOOL "" FORCE)

# GitHub Actions fetches this pinned upstream source at build time.
# Run scripts/setup-llama-source.sh locally before building in Android Studio.
set(LLAMA_SRC "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp")
if(NOT EXISTS "${LLAMA_SRC}/include/llama.h")
    message(FATAL_ERROR "llama.cpp source missing. Run scripts/setup-llama-source.sh from repository root.")
endif()
add_subdirectory("${LLAMA_SRC}" "${CMAKE_CURRENT_BINARY_DIR}/llama-build")

add_library(lumation_ai SHARED lumation_ai.cpp)
target_include_directories(lumation_ai PRIVATE "${LLAMA_SRC}/include")
target_link_libraries(lumation_ai PRIVATE llama log android)
