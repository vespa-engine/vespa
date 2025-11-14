// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bitvector.h"

namespace search {

class GrowableBitVector;

/**
 * search::AllocatedBitVector provides an interface to a bit vector
 * internally implemented as an array of words.
 */
class AllocatedBitVector : public BitVector
{
public:
    /**
     * Class constructor specifying size but not content.  New bitvector
     * is cleared.
     *
     * @param numberOfElements  The size of the bit vector in bits.
     *
     */
    explicit AllocatedBitVector(Index numberOfElements);
    /**
     *
     * @param numberOfElements  The size of the bit vector in bits.
     * @param buffer            The buffer backing the bit vector.
     * @param offset            Where bitvector image is located in the buffer.
     * @param entry_size        The size of the bitvector image in the buffer.
     * @param true_bits         The number of bits set in the bitvector.
     */
    AllocatedBitVector(Index numberOfElements, Alloc buffer, size_t offset, size_t entry_size, Index true_bits);

    /**
     * Creates a new bitvector with size of numberOfElements bits and at least a capacity of capacity.
     * Copies what it can from the original vector. This is used for extending vector.
     *
     * When dynamic_guard_bits is true:
     *   Even guard bits are set to 1 and odd guard bits are set to 0 when using multiple guard bits.
     *   This avoids conflict between old and new guard bits when changing bitvector size by 1 and when
     *   bit vector size is 1 less than capacity.
     * When dynamic_guard_bits is false:
     *   First guard bit is set to 1. A guard bit set to 0 follows when using multiple guard bits.
     */
    AllocatedBitVector(Index numberOfElements, Index capacity, const BitVector* org, const Alloc* init_alloc,
                       bool dynamic_guard_bits);

    AllocatedBitVector(const BitVector &other);
    AllocatedBitVector(const AllocatedBitVector &other);
    ~AllocatedBitVector() override;

    /**
     * Query the size of the bit vector.
     *
     * @return number of legal index positions (bits).
     */
    Index capacity() const { return _capacityBits; }

    Index extraByteSize() const { return _alloc.size(); }

    /**
     * Set new length of bit vector, possibly destroying content.
     *
     * @param newLength the new length of the bit vector (in bits)
     */
    void resize(Index newLength);
    void fixup_after_load(); // set guard bits

    size_t get_allocated_bytes(bool include_self) const noexcept override;

protected:
    Index          _capacityBits;
    Alloc          _alloc;

private:
    friend class GrowableBitVector;

    AllocatedBitVector(const BitVector &other, std::pair<Index, Index> size_capacity);
};

} // namespace search
