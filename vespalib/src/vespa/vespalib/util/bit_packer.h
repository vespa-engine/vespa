// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bit_span.h"
#include <algorithm>
#include <vector>

namespace vespalib {

/**
 * Packs bool values into a vector of bytes (LSB-first), matching
 * the layout expected by BitSpan.
 */
class BitPacker {
    std::vector<std::byte> _data;
    uint64_t               _count;
public:
    BitPacker() noexcept : _data(), _count(0) {}
    void reserve(uint64_t bits) { _data.reserve((bits + 7) / 8); }
    void push_back(bool bit) {
        auto bit_idx = _count++ % 8;
        if (bit_idx == 0) {
            _data.push_back(std::byte{0});
        }
        if (bit) {
            _data.back() |= (std::byte{1} << bit_idx);
        }
    }
    BitSpan bit_span(uint64_t offset, uint64_t length) const noexcept {
        uint64_t clamped_offset = std::min(offset, _count);
        uint64_t clamped_length = std::min(length, _count - clamped_offset);
        return BitSpan(_data.data() + (clamped_offset / 8), clamped_offset % 8, clamped_length);
    }
    uint64_t size() const noexcept { return _count; }
    bool empty() const noexcept { return _count == 0; }
    const std::vector<std::byte> &storage() const noexcept { return _data; }
};

}
