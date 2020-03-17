// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>
#include <cstdint>

namespace vespalib { class asciistream; }

namespace search::datastore {

class EntryRef {
protected:
    uint32_t _ref;
public:
    EntryRef() : _ref(0u) { }
    explicit EntryRef(uint32_t ref_) : _ref(ref_) { }
    uint32_t ref() const { return _ref; }
    bool valid() const { return _ref != 0u; }
    bool operator==(const EntryRef &rhs) const { return _ref == rhs._ref; }
    bool operator!=(const EntryRef &rhs) const { return _ref != rhs._ref; }
    bool operator <(const EntryRef &rhs) const { return _ref < rhs._ref; }
};

/**
 * Class for entry reference where we use OffsetBits bits for offset into buffer,
 * and (32 - OffsetBits) bits for buffer id.
 **/
template <uint32_t OffsetBits, uint32_t BufferBits = 32u - OffsetBits>
class EntryRefT : public EntryRef {
public:
    EntryRefT() : EntryRef() {}
    EntryRefT(size_t offset_, uint32_t bufferId_);
    EntryRefT(const EntryRef & ref_) : EntryRef(ref_.ref()) {}
    size_t offset() const { return _ref & (offsetSize() - 1); }
    uint32_t bufferId() const { return _ref >> OffsetBits; }
    static size_t offsetSize() { return 1ul << OffsetBits; }
    static uint32_t numBuffers() { return 1 << BufferBits; } 
    static size_t align(size_t val) { return val; }
    static size_t pad(size_t val) { (void) val; return 0ul; }
    static constexpr bool isAlignedType = false;
    // TODO: Remove following temporary methods when removing
    // AlignedEntryRefT
    size_t unscaled_offset() const { return offset(); }
    static size_t unscaled_offset_size() { return offsetSize(); }
};

/**
 * Class for entry reference that is similar to EntryRefT,
 * except that we use (2^OffsetAlign) byte alignment on the offset.
 **/
template <uint32_t OffsetBits, uint32_t OffsetAlign>
class AlignedEntryRefT : public EntryRefT<OffsetBits> {
private:
    typedef EntryRefT<OffsetBits> ParentType;
    static const uint32_t PadConstant = ((1 << OffsetAlign) - 1);
public:
    AlignedEntryRefT() : ParentType() {}
    AlignedEntryRefT(size_t offset_, uint32_t bufferId_) :
        ParentType(align(offset_) >> OffsetAlign, bufferId_) {}
    AlignedEntryRefT(const EntryRef & ref_) : ParentType(ref_) {}
    size_t offset() const { return ParentType::offset() << OffsetAlign; }
    static size_t offsetSize() { return ParentType::offsetSize() << OffsetAlign; }
    static size_t align(size_t val) { return val + pad(val); }
    static size_t pad(size_t val) { return (-val & PadConstant); }
    static constexpr bool isAlignedType = true;
};

vespalib::asciistream& operator<<(vespalib::asciistream& os, const EntryRef& ref);

}
