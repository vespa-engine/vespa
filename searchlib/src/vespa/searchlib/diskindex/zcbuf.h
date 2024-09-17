// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cassert>
#include <cstdint>
#include <cstddef>
#include <span>
#include <vector>

namespace search::diskindex {

/*
 * Class containing Zc-encoded data in a memory buffer, typically
 * docid deltas and skip information for posting lists.
 */
class ZcBuf
{
    std::vector<uint8_t> _buffer;

public:
    ZcBuf();
    ~ZcBuf();

    static constexpr uint64_t encode_max = (static_cast<uint64_t>(1) << 42) - 1;
    static constexpr uint8_t mark = 1 << 7;
    static constexpr uint8_t mask = mark - 1;
    void clear() noexcept { _buffer.clear(); }
    std::span<const uint8_t> view() const noexcept { return _buffer; }
    size_t size() const { return _buffer.size(); }

    void encode(uint64_t num) {
        assert(num <= encode_max);
        for (;;) {
            if (num < mark) {
                _buffer.push_back(num);
                break;
            }
            _buffer.push_back((num & mask) | mark);
            num >>= 7;
        }
    }
};

}
