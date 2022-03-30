// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/growablebitvector.h>

namespace proton {

class LidStateVector
{
    search::GrowableBitVector _bv;
    uint32_t _lowest;
    uint32_t _highest;
    bool     _trackLowest;
    bool     _trackHighest;

    void updateLowest();
    void updateHighest(); 
    void maybeUpdateLowest() {
        if (_trackLowest && _lowest < _bv.size() && !_bv.testBit(_lowest))
            updateLowest();
    }

    void maybeUpdateHighest() {
        if (_trackHighest && _highest != 0 && !_bv.testBit(_highest))
            updateHighest();
    }
    template <bool do_set>
    uint32_t assert_is_not_set_then_set_bits_helper(const std::vector<uint32_t>& idxs);
    template <bool do_assert>
    void assert_is_set_then_clear_bits_helper(const std::vector<uint32_t>& idxs);
public:
    
    LidStateVector(unsigned int newSize, unsigned int newCapacity,
                   vespalib::GenerationHolder &generationHolder,
                   bool trackLowest, bool trackHighest);

    ~LidStateVector();

    void resizeVector(uint32_t newSize, uint32_t newCapacity);
    void setBit(unsigned int idx);
    uint32_t assert_not_set_bits(const std::vector<uint32_t>& idxs);
    uint32_t set_bits(const std::vector<uint32_t>& idxs);
    void clearBit(unsigned int idx);
    void consider_clear_bits(const std::vector<uint32_t>& idxs);
    void clear_bits(const std::vector<uint32_t>& idxs);
    bool testBit(unsigned int idx) const { return _bv.testBit(idx); }
    bool testBitSafe(unsigned int idx) const { return _bv.testBitSafe(idx); }
    unsigned int size() const { return _bv.size(); }
    unsigned int byteSize() const {
        return _bv.extraByteSize() + sizeof(LidStateVector);
    }
    bool empty() const { return count() == 0u; }
    unsigned int getLowest() const { return _lowest; }
    unsigned int getHighest() const { return _highest; }

    /**
     * Get cached number of bits set in vector.  Called by read or
     * write thread.  Write thread must updated cached number as needed.
     */
    uint32_t count() const {
        // Called by document db executor thread or metrics related threads
        return _bv.countTrueBits();
    }

    unsigned int getNextTrueBit(unsigned int idx) const {
        return _bv.getNextTrueBit(idx);
    }

    const search::GrowableBitVector &getBitVector() const { return _bv; }
}; 

}
