// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>
#include <cstddef>

namespace document::select {

// Any resource constraints set for parsing document selection expressions
struct ParserLimits {
    // Max depth allowed for nodes in the AST tree.
    constexpr static uint32_t MaxRecursionDepth    = 1024;
    // Max size of entire input document selection string, in bytes.
    constexpr static size_t   MaxSelectionByteSize = 1024*1024;
};

void __attribute__((noinline)) throw_max_depth_exceeded_exception();

}
