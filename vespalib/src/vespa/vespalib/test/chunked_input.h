// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/data/input.h>

namespace vespalib {
namespace test {

/**
 * Input filter making sure the input is split into chunks no larger
 * than the maximum chunk size given to the constuctor.
 **/
struct ChunkedInput : Input {
    Input &input;
    size_t max_chunk_size;
    ChunkedInput(Input &input_in, size_t max_chunk_size_in)
        : input(input_in), max_chunk_size(max_chunk_size_in) {}
    Memory obtain() override {
        Memory memory = input.obtain();
        memory.size = std::min(memory.size, max_chunk_size);
        return memory;
    }
    Input &evict(size_t bytes) override {
        EXPECT_LESS_EQUAL(bytes, max_chunk_size);
        input.evict(bytes);
        return *this;
    }
};

} // namespace test
} // namespace vespalib
