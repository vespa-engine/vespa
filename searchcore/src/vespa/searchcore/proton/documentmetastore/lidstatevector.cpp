// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "lidstatevector.h"
#include <cassert>

namespace proton {

using vespalib::GenerationHolder;

LidStateVector::LidStateVector(unsigned int newSize, unsigned int newCapacity,
                               GenerationHolder &generationHolder,
                               bool trackLowest, bool trackHighest)
    : _bv(newSize, newCapacity, generationHolder),
      _lowest(trackLowest ? newSize : 0u),
      _highest(0),
      _trackLowest(trackLowest),
      _trackHighest(trackHighest)
{
}

LidStateVector::~LidStateVector() = default;

void
LidStateVector::resizeVector(uint32_t newSize, uint32_t newCapacity)
{
    uint32_t lowest = getLowest();
    uint32_t highest = getHighest();
    assert(!_trackLowest || lowest <= _bv.size());
    assert(!_trackHighest || _bv.size() == 0 || highest < _bv.size());
    bool nolowest(lowest == _bv.size());
    if (_bv.size() > newSize) {
        _bv.shrink(newSize);
    }
    if (_bv.capacity() < newCapacity) {
        _bv.reserve(newCapacity);
    }
    if (_bv.size() < newSize) {
        _bv.extend(newSize);
    }
    if (_trackLowest) {
        if (nolowest || lowest > _bv.size()) {
            lowest = _bv.size();
            _lowest.store(lowest, std::memory_order_relaxed);
        }
    }
    if (_trackHighest) {
        if (highest >= _bv.size()) {
            highest = _bv.size() > 0 ? _bv.getPrevTrueBit(_bv.size() - 1) : 0;
            _highest.store(highest, std::memory_order_relaxed);
        }
    }
}

void
LidStateVector::updateLowest(uint32_t lowest)
{
    lowest = _bv.getNextTrueBit(lowest);
    assert(lowest <= _bv.size());
    _lowest.store(lowest, std::memory_order_relaxed);
}

void
LidStateVector::updateHighest(uint32_t highest)
{
    highest = _bv.getPrevTrueBit(highest);
    assert(_bv.size() == 0 || highest < _bv.size());
    _highest.store(highest, std::memory_order_relaxed);
}

void
LidStateVector::setBit(unsigned int idx)
{
    assert(idx < _bv.size());
    if (_trackLowest && idx < getLowest()) {
        _lowest.store(idx, std::memory_order_relaxed);
    }
    if (_trackHighest && idx > getHighest()) {
        _highest.store(idx, std::memory_order_relaxed);
    }
    assert(!_bv.testBit(idx));
    _bv.setBitAndMaintainCount(idx);
}

template <bool do_set>
uint32_t
LidStateVector::assert_is_not_set_then_set_bits_helper(const std::vector<uint32_t>& idxs)
{
    uint32_t size = _bv.size();
    uint32_t high = 0;
    uint32_t low = size;
    for (auto idx : idxs) {
        assert(idx < size);
        if (idx > high) {
            high = idx;
        }
        assert(!_bv.testBit(idx));
        if (do_set) {
            if (idx < low) {
                low = idx;
            }
            _bv.setBitAndMaintainCount(idx);
        }
    }
    if (do_set) {
        if (_trackLowest && low < getLowest()) {
            _lowest.store(low, std::memory_order_relaxed);
        }
        if (_trackHighest && high > getHighest()) {
            _highest.store(high, std::memory_order_relaxed);
        }
    }
    return high;
}

uint32_t
LidStateVector::assert_not_set_bits(const std::vector<uint32_t>& idxs)
{
    return assert_is_not_set_then_set_bits_helper<false>(idxs);
}

uint32_t 
LidStateVector::set_bits(const std::vector<uint32_t>& idxs)
{
    return assert_is_not_set_then_set_bits_helper<true>(idxs);
}


void
LidStateVector::clearBit(unsigned int idx)
{
    assert(idx < _bv.size());
    assert(_bv.testBit(idx));
    _bv.clearBitAndMaintainCount(idx);
    maybeUpdateLowest();
    maybeUpdateHighest();
}

template <bool do_assert>
void
LidStateVector::assert_is_set_then_clear_bits_helper(const std::vector<uint32_t>& idxs)
{
    for (auto idx : idxs) {
        if (do_assert) {
            assert(_bv.testBit(idx));
        }
        _bv.clearBitAndMaintainCount(idx);
    }
    maybeUpdateLowest();
    maybeUpdateHighest();
}

void 
LidStateVector::consider_clear_bits(const std::vector<uint32_t>& idxs)
{
    assert_is_set_then_clear_bits_helper<false>(idxs);
}

void 
LidStateVector::clear_bits(const std::vector<uint32_t>& idxs)
{
    assert_is_set_then_clear_bits_helper<true>(idxs);
}

}  // namespace proton
