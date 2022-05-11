// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/growablebitvector.h>
#include <atomic>

namespace proton {

class LidStateVector
{
    search::GrowableBitVector _bv;
    std::atomic<uint32_t> _lowest;
    std::atomic<uint32_t> _highest;
    bool     _trackLowest;
    bool     _trackHighest;

    void updateLowest(uint32_t lowest);
    void updateHighest(uint32_t highest);
    void maybeUpdateLowest() {
        uint32_t lowest = getLowest();
        if (_trackLowest && lowest < _bv.writer().size() && !_bv.writer().testBit(lowest)) {
            updateLowest(lowest);
        }
    }
    void maybeUpdateHighest() {
        uint32_t highest = getHighest();
        if (_trackHighest && highest != 0 && !_bv.writer().testBit(highest)) {
            updateHighest(highest);
        }
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
    bool testBit(unsigned int idx) const { return _bv.reader().testBit(idx); }
    bool testBitAcquire(unsigned int idx) const { return _bv.reader().testBitAcquire(idx); }
    unsigned int size() const { return _bv.reader().size(); }
    unsigned int byteSize() const {
        return _bv.extraByteSize() + sizeof(LidStateVector);
    }
    bool empty() const { return count() == 0u; }
    unsigned int getLowest() const { return _lowest.load(std::memory_order_relaxed); }
    unsigned int getHighest() const { return _highest.load(std::memory_order_relaxed); }

    /**
     * Get cached number of bits set in vector.  Called by read or
     * write thread.  Write thread must updated cached number as needed.
     */
    uint32_t count() const {
        // Called by document db executor thread or metrics related threads
        return _bv.reader().countTrueBits();
    }

    unsigned int getNextTrueBit(unsigned int idx) const {
        return _bv.reader().getNextTrueBit(idx);
    }

    const search::BitVector &getBitVector() const { return _bv.reader(); }
}; 

}
