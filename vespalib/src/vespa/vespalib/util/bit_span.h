// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace vespalib {

/**
 * A lightweight read-only view over bit-packed bool data.
 * Bits are stored LSB-first: bool[i] is bit (i%8) of byte[i/8].
 */
class BitSpan {
    const char* _data;
    uint32_t    _count;
public:
    class Sentinel {
        uint32_t _end;
    public:
        explicit Sentinel(uint32_t end) noexcept : _end(end) {}
        uint32_t value() const noexcept { return _end; }
    };
    class Iterator {
        const char* _data;
        uint32_t    _pos;
    public:
        Iterator(const char* data, uint32_t pos) noexcept : _data(data), _pos(pos) {}
        bool operator*() const noexcept { return (_data[_pos / 8] >> (_pos % 8)) & 1; }
        Iterator& operator++() noexcept { ++_pos; return *this; }
        bool operator!=(Sentinel s) const noexcept { return _pos != s.value(); }
    };

    BitSpan() noexcept : _data(nullptr), _count(0) {}
    BitSpan(const char* data, uint32_t count) noexcept : _data(data), _count(count) {}
    bool operator[](uint32_t i) const noexcept { return (_data[i / 8] >> (i % 8)) & 1; }
    uint32_t size() const noexcept { return _count; }
    bool empty() const noexcept { return _count == 0; }
    Iterator begin() const noexcept { return Iterator(_data, 0); }
    Sentinel end() const noexcept { return Sentinel(_count); }
};

}
