// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>
#include <cstdint>

namespace vespalib { class asciistream; }

namespace vespalib::datastore {

class EntryRef {
protected:
    uint32_t _ref;
public:
    EntryRef() noexcept : _ref(0u) { }
    explicit EntryRef(uint32_t ref_) noexcept : _ref(ref_) { }
    uint32_t ref() const noexcept { return _ref; }
    uint32_t hash() const noexcept { return _ref; }
    bool valid() const noexcept { return _ref != 0u; }
    uint32_t buffer_id(uint32_t offset_bits) const noexcept { return _ref >> offset_bits; }
    bool operator==(const EntryRef &rhs) const noexcept { return _ref == rhs._ref; }
    bool operator!=(const EntryRef &rhs) const noexcept { return _ref != rhs._ref; }
    bool operator <(const EntryRef &rhs) const noexcept { return _ref < rhs._ref; }
    bool operator <=(const EntryRef &rhs) const noexcept { return _ref <= rhs._ref; }
};

/**
 * Class for entry reference where we use OffsetBits bits for offset into buffer,
 * and (32 - OffsetBits) bits for buffer id.
 **/
template <uint32_t OffsetBits, uint32_t BufferBits = 32u - OffsetBits>
class EntryRefT : public EntryRef {
public:
    static constexpr uint32_t offset_bits = OffsetBits;
    EntryRefT() noexcept : EntryRef() {}
    EntryRefT(size_t offset_, uint32_t bufferId_) noexcept;
    EntryRefT(const EntryRef & ref_) noexcept : EntryRef(ref_.ref()) {}
    size_t offset() const noexcept { return _ref & (offsetSize() - 1); }
    uint32_t bufferId() const noexcept { return _ref >> OffsetBits; }
    static size_t offsetSize() noexcept { return 1ul << OffsetBits; }
    static uint32_t numBuffers() noexcept { return 1 << BufferBits; }
};

vespalib::asciistream& operator<<(vespalib::asciistream& os, const EntryRef& ref);

}
