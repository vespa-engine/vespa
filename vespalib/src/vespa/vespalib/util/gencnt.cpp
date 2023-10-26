// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "gencnt.h"
#include <cassert>

namespace vespalib {

GenCnt &
GenCnt::add(uint32_t n)
{
    uint32_t newVal = _val + n;
    if (newVal < _val) {
        // avoid wrap-around to 0
        newVal++;
    }
    _val = newVal;
    return *this;
}


bool
GenCnt::inRangeInclusive(GenCnt a, GenCnt b) const
{
    if (_val == 0) {
        return (a._val == 0);
    }
    if (b._val >= a._val) {
        //        a--------------|
        // |-------------b
        //        |------|
        return ((_val >= a._val) && (_val <= b._val));
    } else {
        //               a-------|
        // |------b
        // |------|      |-------|
        return ((_val >= a._val) || (_val <= b._val));
    }
}


uint32_t
GenCnt::distance(const GenCnt &other) const
{
    if (other._val == 0) {
        // special case
        assert(_val == 0);
        return 0;
    }
    if (_val <= other._val) {
        // normal case
        return (other._val - _val);
    }
    // wrapped case
    return (other._val - _val - 1);
}


GenCnt &
GenCnt::operator=(const GenCnt &src)
{
    _val = src.getAsInt();
    return *this;
}

} // namespace vespalib
