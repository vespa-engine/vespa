// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "termfieldmatchdata.h"
#include <limits>
#include <cassert>

namespace search::fef {

TermFieldMatchData::TermFieldMatchData() :
    _docId(invalidId()),
    _fieldId(ILLEGAL_FIELD_ID),
    _flags(UNPACK_ALL_FEATURES_MASK),
    _sz(0),
    _numOccs(0),
    _fieldLength(0)
{
    memset(&_data, 0, sizeof(_data));
}

TermFieldMatchData::TermFieldMatchData(const TermFieldMatchData & rhs) :
    _docId(rhs.getDocId()),
    _fieldId(rhs._fieldId),
    _flags(rhs._flags),
    _sz(0),
    _numOccs(0),
    _fieldLength(0)
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

TermFieldMatchData &
TermFieldMatchData::operator = (const TermFieldMatchData & rhs)
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

TermFieldMatchData::MutablePositionsIterator
TermFieldMatchData::populate_fixed() {
    assert(!allocated());
    if (_sz == 0) {
        new (_data._position) TermFieldMatchDataPosition();
        _sz = 1;
    }
    return getFixed();
}

TermFieldMatchData &
TermFieldMatchData::setFieldId(uint32_t fieldId) {
    if (fieldId == IllegalFieldId) {
        fieldId = ILLEGAL_FIELD_ID;
    } else {
        assert(fieldId < ILLEGAL_FIELD_ID);
    }
    _fieldId = fieldId;
    return *this;
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
    sswap(&_flags, &rhs._flags);
    sswap(&_sz, &rhs._sz);
    sswap(&_numOccs, &rhs._numOccs);
    sswap(&_fieldLength, &rhs._fieldLength);
    char tmp[sizeof(_data)];
    memcpy(tmp, &rhs._data, sizeof(_data));
    memcpy(&rhs._data, &_data, sizeof(_data));
    memcpy(&_data, tmp, sizeof(_data));
}

namespace {

constexpr size_t MAX_ELEMS = std::numeric_limits<uint16_t>::max();
constexpr size_t INITIAL_ELEMS = 1024/sizeof(TermFieldMatchDataPosition);

}

void
TermFieldMatchData::resizePositionVector(size_t sz)
{
    assert(allocated());
    assert(sz >= _sz);
    size_t newSize(std::min(MAX_ELEMS, std::max(1ul, sz)));
    TermFieldMatchDataPosition * n = new TermFieldMatchDataPosition[newSize];
    for (size_t i(0); i < _data._positions._allocated; i++) {
        n[i] = _data._positions._positions[i];
    }
    delete [] _data._positions._positions;
    _data._positions._allocated = newSize;
    _data._positions._positions = n;
}

void
TermFieldMatchData::allocateVector()
{
    assert(_sz < 2);
    assert(!allocated());
    size_t newSize = INITIAL_ELEMS;
    TermFieldMatchDataPosition * n = new TermFieldMatchDataPosition[newSize];
    if (_sz > 0) {
        n[0] = *getFixed();
        _data._positions._maxElementLength = getFixed()->getElementLen();
    }
    _flags |= MULTIPOS_FLAG; // set allocated() flag
    _data._positions._allocated = newSize;
    _data._positions._positions = n;
}

void
TermFieldMatchData::appendPositionToAllocatedVector(const TermFieldMatchDataPosition &pos)
{
    if (__builtin_expect(_sz >= MAX_ELEMS, false)) return;
    assert(allocated());
    if (__builtin_expect(_sz >= _data._positions._allocated, false)) {
        resizePositionVector(_sz*2);
    }
    if (__builtin_expect(pos.getElementLen() > _data._positions._maxElementLength, false)) {
        _data._positions._maxElementLength = pos.getElementLen();
    }
    _data._positions._positions[_sz++] = pos;
}

}
