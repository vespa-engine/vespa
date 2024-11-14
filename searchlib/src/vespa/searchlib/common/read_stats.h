// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace search {

/*
 * Struct passed to read functions to pick up information about read
 * stats.
 */
struct ReadStats
{
    uint64_t read_bytes;        // bytes read from disk or bytes in pages containing the data
    ReadStats() noexcept
        : read_bytes(0)
    { }
    void clear() noexcept {
        read_bytes = 0;
    }
};

}
