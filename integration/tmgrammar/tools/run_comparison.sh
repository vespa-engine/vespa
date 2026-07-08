#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e
cd "$(dirname "$0")/.."

# Regenerate the grammar from source files in the vespa repo
if [[ "$1" == "--regenerate" ]]; then
    shift
    uv run tools/generate_tmgrammar.py
fi

# Run TM tokenizer
(cd tools && npm install --silent 2>/dev/null && node tm_tokenize.mjs)

# Run comparison (requires java_tokens.json to exist — see README)
if [[ -f tools/java_tokens.json ]]; then
    uv run tools/compare_tokens.py "$@"
else
    echo "Note: tools/java_tokens.json not found — skipping comparison."
    echo "To generate it, run SemanticTokenDumper in the vespa repo (see README)."
fi
