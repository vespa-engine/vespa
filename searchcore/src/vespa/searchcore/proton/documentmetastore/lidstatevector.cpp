// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
      _count(0u),
      _trackLowest(trackLowest),
      _trackHighest(trackHighest)
{
}

LidStateVector::~LidStateVector() = default;

void
LidStateVector::resizeVector(uint32_t newSize, uint32_t newCapacity)
{
    assert(!_trackLowest || _lowest <= _bv.size());
    assert(!_trackHighest || _bv.size() == 0 || _highest < _bv.size());
    bool nolowest(_lowest == _bv.size());
    if (_bv.size() > newSize) {
        _bv.shrink(newSize);
        assert(_count >= internalCount());
        _count = internalCount();
    }
    if (_bv.capacity() < newCapacity) {
        _bv.reserve(newCapacity);
        assert(_count == internalCount());
    }
    if (_bv.size() < newSize) {
        _bv.extend(newSize);
        assert(_count == internalCount());
    }
    if (_trackLowest) {
        if (nolowest) {
            _lowest = _bv.size();
        }
        if (_lowest > _bv.size()) {
            _lowest = _bv.size();
        }
    }
    if (_trackHighest) {
        if (_highest >= _bv.size()) {
            _highest = _bv.size() > 0 ? _bv.getPrevTrueBit(_bv.size() - 1) : 0;
        }
    }
    maybeUpdateLowest();
    maybeUpdateHighest();
}


void
LidStateVector::updateLowest()
{
    if (_lowest >= _bv.size())
        return;
    if (_bv.testBit(_lowest))
        return;
    uint32_t lowest = _bv.getNextTrueBit(_lowest);
    assert(lowest <= _bv.size());
    _lowest = lowest;
}


void
LidStateVector::updateHighest()
{
    if (_highest == 0)
        return;
    if (_bv.testBit(_highest))
        return;
    uint32_t highest = _bv.getPrevTrueBit(_highest);
    assert(_bv.size() == 0 || highest < _bv.size());
    _highest = highest;
}


void
LidStateVector::setBit(unsigned int idx)
{
    assert(idx < _bv.size());
    if (_trackLowest && idx < _lowest) {
        _lowest = idx;
    }
    if (_trackHighest && idx > _highest) {
        _highest = idx;
    }
    assert(!_bv.testBit(idx));
    _bv.setBitAndMaintainCount(idx);
    ++_count;
    assert(_count == internalCount());
}


void
LidStateVector::clearBit(unsigned int idx)
{
    assert(idx < _bv.size());
    assert(_bv.testBit(idx));
    _bv.clearBitAndMaintainCount(idx);
    --_count;
    assert(_count == internalCount());
    maybeUpdateLowest();
    maybeUpdateHighest();
}


bool
LidStateVector::empty() const
{
    return _count == 0u;
}


unsigned int
LidStateVector::getLowest() const
{
    return _lowest;
}


unsigned int
LidStateVector::getHighest() const
{
    return _highest;
}


uint32_t
LidStateVector::internalCount()
{
    // Called by document db executor thread.
    return _bv.countTrueBits();
}


uint32_t
LidStateVector::count() const
{
    // Called by document db executor thread or metrics related threads
    return _count;
}


}  // namespace proton
