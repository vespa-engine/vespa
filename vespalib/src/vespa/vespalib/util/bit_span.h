// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>
#include <cstdint>

namespace vespalib {

/**
 * A lightweight read-only view over bit-packed bool data.
 * Bits are stored LSB-first: bool[i] is bit (i%8) of byte[i/8].
 * An optional bit offset allows the span to start at any bit position.
 */
class BitSpan {
    const std::byte* _data;
    uint32_t         _offset;
    uint32_t         _count;
public:
    class Sentinel {
        const uint32_t _end;
    public:
        explicit Sentinel(uint32_t end) noexcept : _end(end) {}
        bool valid(uint32_t pos) const noexcept { return pos < _end; }
    };
    class Iterator {
        const std::byte* _data;
        uint32_t         _pos;
    public:
        Iterator(const std::byte* data, uint32_t pos) noexcept : _data(data), _pos(pos) {}
        bool operator*() const noexcept { return ((_data[_pos / 8] >> (_pos % 8)) & std::byte{1}) != std::byte{0}; }
        Iterator& operator++() noexcept { ++_pos; return *this; }
        bool operator!=(Sentinel s) const noexcept { return s.valid(_pos); }
    };

    BitSpan() noexcept : _data(nullptr), _offset(0), _count(0) {}
    BitSpan(const void* data, uint32_t count) noexcept : _data(static_cast<const std::byte*>(data)), _offset(0), _count(count) {}
    BitSpan(const void* data, uint32_t offset, uint32_t count) noexcept : _data(static_cast<const std::byte*>(data)), _offset(offset), _count(count) {}
    bool operator[](uint32_t i) const noexcept { return ((_data[(_offset + i) / 8] >> ((_offset + i) % 8)) & std::byte{1}) != std::byte{0}; }
    uint32_t size() const noexcept { return _count; }
    bool empty() const noexcept { return _count == 0; }
    Iterator begin() const noexcept { return Iterator(_data, _offset); }
    Sentinel end() const noexcept { return Sentinel(_offset + _count); }
};

}
