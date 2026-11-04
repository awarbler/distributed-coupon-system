#!/bin/sh
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
if curl -fsS --max-time 2 http://127.0.0.1:11434/api/tags >/dev/null 2>&1; then
  exit 0
fi
if [ ! -x .local-reader/runtime/ollama ]; then
  echo "Local reader runtime is missing. No paid API will be used." >&2
  exit 1
fi
mkdir -p .local-reader/models
export OLLAMA_MODELS="$PWD/.local-reader/models"
export OLLAMA_HOST=127.0.0.1:11434
export OLLAMA_NO_CLOUD=1
if [ "${1:-}" = "--background" ]; then
  nohup .local-reader/runtime/ollama serve > .local-reader/reader.log 2>&1 </dev/null &
  exit 0
fi
exec .local-reader/runtime/ollama serve
