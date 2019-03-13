// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

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
    EntryRefT(uint64_t offset_, uint32_t bufferId_);
    EntryRefT(const EntryRef & ref_) : EntryRef(ref_.ref()) {}
    uint32_t hash() const { return offset() + (bufferId() << OffsetBits); }
    uint64_t offset() const { return _ref >> BufferBits; }
    uint32_t bufferId() const { return _ref & (numBuffers() - 1); }
    static uint64_t offsetSize() { return 1ul << OffsetBits; }
    static uint32_t numBuffers() { return 1 << BufferBits; } 
    static uint64_t align(uint64_t val) { return val; }
    static uint64_t pad(uint64_t val) { (void) val; return 0ul; }
    static constexpr bool isAlignedType = false;
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
    AlignedEntryRefT(uint64_t offset_, uint32_t bufferId_) :
        ParentType(align(offset_) >> OffsetAlign, bufferId_) {}
    AlignedEntryRefT(const EntryRef & ref_) : ParentType(ref_) {}
    uint64_t offset() const { return ParentType::offset() << OffsetAlign; }
    static uint64_t offsetSize() { return ParentType::offsetSize() << OffsetAlign; }
    static uint64_t align(uint64_t val) { return val + pad(val); }
    static uint64_t pad(uint64_t val) { return (-val & PadConstant); }
    static constexpr bool isAlignedType = true;
};

}
