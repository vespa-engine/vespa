// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <limits>

namespace search::index {

/**
 * The result after performing a disk bitvector dictionary lookup.
 **/
class BitVectorDictionaryLookupResult {
public:
    static constexpr uint32_t invalid = std::numeric_limits<uint32_t>::max();
    uint64_t idx;

    explicit BitVectorDictionaryLookupResult(uint32_t idx_in) noexcept
        : idx(idx_in)
    {
    }
    BitVectorDictionaryLookupResult() noexcept
        : BitVectorDictionaryLookupResult(invalid)
    {
    }
    bool valid() const noexcept { return idx != invalid; }
};

}
