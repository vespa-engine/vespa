// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bitvector.h"

namespace search {

class GrowableBitVector;
class BitVectorTest;

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
     */
    AllocatedBitVector(Index numberOfElements, Alloc buffer, size_t offset);

    /**
     * Creates a new bitvector with size of numberOfElements bits and at least a capacity of capacity.
     * Copies what it can from the original vector. This is used for extending vector.
     */
    AllocatedBitVector(Index numberOfElements, Index capacity, const void * rhsBuf, size_t rhsSize, const Alloc* init_alloc);

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

protected:
    Index          _capacityBits;
    Alloc          _alloc;

private:
    friend class BitVectorTest;
    friend class GrowableBitVector;

    AllocatedBitVector(const BitVector &other, std::pair<Index, Index> size_capacity);
};

} // namespace search
