// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "allocatedbitvector.h"
#include <cstring>
#include <cassert>

namespace search {

namespace {

size_t computeCapacity(size_t capacity, size_t allocatedBytes) {
    size_t possibleCapacity = (allocatedBytes * 8) - 1;
    assert(possibleCapacity >= capacity);
    return possibleCapacity;
}

// This is to ensure that we only read size and capacity once during copy
// to ensure that they do not change unexpectedly under our feet due to resizing in different thread.
std::pair<BitVector::Index, BitVector::Index>
extract_size_size(const BitVector & bv) {
    BitVector::Index size = bv.size();
    return std::pair<BitVector::Index, BitVector::Index>(size, size);
}

std::pair<BitVector::Index, BitVector::Index>
extract_size_capacity(const AllocatedBitVector & bv) {
    BitVector::Index size = bv.size();
    BitVector::Index capacity = bv.capacity();
    while (capacity < size) {
        // Since size and capacity might be changed in another thread we need
        // this fallback to avoid inconsistency during shrink.
        std::atomic_thread_fence(std::memory_order_seq_cst);
        size = bv.size();
        capacity = bv.capacity();
    }
    return std::pair<BitVector::Index, BitVector::Index>(size, capacity);
}

}

AllocatedBitVector::AllocatedBitVector(Index numberOfElements)
    : BitVector(),
      _capacityBits(numberOfElements),
      _alloc(allocatePaddedAndAligned(numberOfElements))
{
    _capacityBits = computeCapacity(_capacityBits, _alloc.size());
    init(_alloc.get(), 0, numberOfElements);
    clear();
}

AllocatedBitVector::AllocatedBitVector(Index numberOfElements, Alloc buffer, size_t offset, size_t entry_size,
                                       Index true_bits)
    : BitVector(static_cast<char *>(buffer.get()) + offset, numberOfElements),
      _capacityBits(numberOfElements),
      _alloc(std::move(buffer))
{
    setTrueBits(true_bits);
    size_t vectorsize = getFileBytes(numberOfElements);
    if (vectorsize > entry_size) {
        // Fixup after reading fewer bytes than expected, e.g. due to file format changes.
        char* entry_end = static_cast<char*>(_alloc.get()) + offset + entry_size;
        memset(entry_end, '\0', vectorsize - entry_size);
        if (wordNum(size()) * sizeof(Word) >= entry_size) {
            // Loss of guard bit and data bits only occurs in bitvector unit test.
            setGuardBit();
            if (wordNum(size()) * sizeof(Word) > entry_size) {
               updateCount();
            }
        }
    }
}

AllocatedBitVector::AllocatedBitVector(Index numberOfElements, Index capacityBits, const BitVector* org, const Alloc* init_alloc)
    : BitVector(),
      _capacityBits(capacityBits),
      _alloc(allocatePaddedAndAligned(0, numberOfElements, capacityBits, init_alloc))
{
    _capacityBits = computeCapacity(_capacityBits, _alloc.size());
    init(_alloc.get(), 0, numberOfElements);
    if (org != nullptr) {
        initialize_from(*org);
        setGuardBit();
        updateCount();
    } else {
        clear();
    }
}

AllocatedBitVector::AllocatedBitVector(const AllocatedBitVector & rhs)
    : AllocatedBitVector(rhs, extract_size_capacity(rhs))
{ }

AllocatedBitVector::AllocatedBitVector(const BitVector & rhs)
    : AllocatedBitVector(rhs, extract_size_size(rhs))
{ }

AllocatedBitVector::AllocatedBitVector(const BitVector & rhs, std::pair<Index, Index> size_capacity)
    : BitVector(),
      _capacityBits(size_capacity.second),
      _alloc(allocatePaddedAndAligned(0, size_capacity.first, size_capacity.second))
{
    _capacityBits = computeCapacity(_capacityBits, _alloc.size());
    init(_alloc.get(), 0, size_capacity.first);
    initialize_from(rhs);
    setGuardBit();
    updateCount();
}

//////////////////////////////////////////////////////////////////////
// Destructor
//////////////////////////////////////////////////////////////////////
AllocatedBitVector::~AllocatedBitVector() = default;

void
AllocatedBitVector::resize(Index newLength)
{
    _alloc = allocatePaddedAndAligned(0, newLength, newLength, &_alloc);
    _capacityBits = computeCapacity(newLength, _alloc.size());
    init(_alloc.get(), 0, newLength);
    clear();
}

size_t
AllocatedBitVector::get_allocated_bytes(bool include_self) const noexcept
{
    size_t result = extraByteSize();
    if (include_self) {
        result += sizeof(AllocatedBitVector);
    }
    return result;
}

} // namespace search
