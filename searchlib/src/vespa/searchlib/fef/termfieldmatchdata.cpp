// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "termfieldmatchdata.h"
#include <limits>

namespace search::fef {

TermFieldMatchData::TermFieldMatchData() :
    _docId(invalidId()),
    _fieldId(FIELDID_MASK),
    _sz(0)
{
    memset(&_data, 0, sizeof(_data));
}

TermFieldMatchData::TermFieldMatchData(const TermFieldMatchData & rhs) :
    _docId(rhs._docId),
    _fieldId(rhs._fieldId),
    _sz(0)
{
    memset(&_data, 0, sizeof(_data));
    if (isRawScore()) {
        _data._rawScore = rhs._data._rawScore;
    } else {
        for (auto it(rhs.begin()), mt(rhs.end()); it != mt; it++) {
            appendPosition(*it);
        }
    }
}

TermFieldMatchData & TermFieldMatchData::operator = (const TermFieldMatchData & rhs)
{
    if (this != & rhs) {
        TermFieldMatchData tmp(rhs);
        swap(tmp);
    }
    return *this;
}

TermFieldMatchData::~TermFieldMatchData()
{
    if (isRawScore()) {
    } else if (allocated()) {
        delete [] _data._positions._positions;
    } else {
        getFixed()->~TermFieldMatchDataPosition();
    }
}

namespace {

template <typename T>
void sswap(T * a, T * b) {
    T tmp(*a);
    *a = *b;
    *b = tmp;
}

}

void
TermFieldMatchData::swap(TermFieldMatchData &rhs)
{
    sswap(&_docId, &rhs._docId);
    sswap(&_fieldId, &rhs._fieldId);
    sswap(&_sz, &rhs._sz);
    char tmp[sizeof(_data)];
    memcpy(tmp, &rhs._data, sizeof(_data));
    memcpy(&rhs._data, &_data, sizeof(_data));
    memcpy(&_data, tmp, sizeof(_data));
}

namespace {

constexpr size_t MAX_ELEMS =  std::numeric_limits<uint16_t>::max();

}

void
TermFieldMatchData::resizePositionVector(size_t sz)
{
    assert(allocated());
    assert(_sz >= 2);
    size_t newSize(std::min(MAX_ELEMS, sz));
    TermFieldMatchDataPosition * n = new TermFieldMatchDataPosition[newSize];
    for (size_t i(0); i < _data._positions._allocated; i++) {
        n[i] = _data._positions._positions[i];
    }
    delete [] _data._positions._positions;
    _data._positions._allocated = newSize;
    _data._positions._positions = n;
}

void
TermFieldMatchData::allocateVectorAndAppend(const TermFieldMatchDataPosition &pos)
{
    assert(_sz == 1);
    assert(!allocated());
    size_t newSize = 2;
    TermFieldMatchDataPosition * n = new TermFieldMatchDataPosition[newSize];
    n[0] = *getFixed();
    n[1] = pos;
    _data._positions._maxElementLength = std::max(getFixed()->getElementLen(), pos.getElementLen());
    _data._positions._allocated = newSize;
    _data._positions._positions = n;
    _fieldId = _fieldId | 0x4000; // set allocated() flag
    _sz++;
}

void
TermFieldMatchData::appendPositionToAllocatedVector(const TermFieldMatchDataPosition &pos)
{
    assert(allocated());
    if (__builtin_expect(_sz >= _data._positions._allocated, false)) {
        resizePositionVector(_sz*2);
    }
    if (__builtin_expect(pos.getElementLen() > _data._positions._maxElementLength, false)) {
        _data._positions._maxElementLength = pos.getElementLen();
    }
    if (__builtin_expect(_sz < MAX_ELEMS, true)) {
        _data._positions._positions[_sz++] = pos;
    }
}

} // namespace
