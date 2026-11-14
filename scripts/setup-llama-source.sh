#!/usr/bin/env bash
set -euo pipefail
# Pin upstream source for reproducible Android CMake/JNI builds.
LLAMA_SHA="a956a852c9c8bc1fb07206364838ddef299178c0"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
DEST="${REPO_DIR}/android/third_party/llama.cpp"

if [ -f "${DEST}/include/llama.h" ]; then
  actual="$(git -C "${DEST}" rev-parse HEAD)"
  if [ "${actual}" = "${LLAMA_SHA}" ]; then
    echo "llama.cpp already pinned at ${LLAMA_SHA}"
    exit 0
  fi
  echo "Unexpected llama.cpp source SHA at ${DEST}: ${actual}" >&2
  exit 1
fi

mkdir -p "${DEST}"
git -C "${DEST}" init
git -C "${DEST}" remote add origin https://github.com/ggml-org/llama.cpp.git
git -C "${DEST}" fetch --depth=1 origin "${LLAMA_SHA}"
git -C "${DEST}" checkout --detach FETCH_HEAD
echo "Pinned llama.cpp source is ready"
