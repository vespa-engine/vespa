// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "postingdata.h"
#include <vespa/vespalib/btree/minmaxaggregated.h>
#include <algorithm>

namespace search {

/**
 * Inner attribute iterator used for temporary posting lists (range searches).
 */
template <typename P>
class ArrayIterator
{
public:
    ArrayIterator() : _cur(nullptr), _end(nullptr), _begin(nullptr) { }

    const P * operator->() const { return _cur; }

    ArrayIterator & operator++() {
        ++_cur;
        return *this;
    }

    bool valid() const { return _cur != _end; }

    void linearSeek(uint32_t docId) {
        while (_cur != _end && _cur->_key < docId) {
            ++_cur;
        }
    }

    uint32_t getKey() const { return _cur->_key; }
    inline int32_t getData() const { return _cur->getData(); }

    void set(const P *begin, const P *end) {
        _cur = begin;
        _end = end;
        _begin = begin;
    }

    void lower_bound(uint32_t docId) {
        P keyWrap;
        keyWrap._key = docId;
        _cur = std::lower_bound<const P *, P>(_begin, _end, keyWrap);
    }

    void swap(ArrayIterator &rhs) {
        std::swap(_cur, rhs._cur);
        std::swap(_end, rhs._end);
        std::swap(_begin, rhs._begin);
    }
protected:
    const P *_cur;
    const P *_end;
    const P *_begin;
};

template <>
inline int32_t
ArrayIterator<AttributePosting>::getData() const
{
    return 1;   // default weight 1 for single value attributes
}


/**
 * Inner attribute iterator used for short posting lists (8 or less
 * documents).
 */

template <typename P>
class DocIdMinMaxIterator : public ArrayIterator<P>
{
public:
    DocIdMinMaxIterator()
        : ArrayIterator<P>()
    { }
    inline vespalib::btree::MinMaxAggregated getAggregated() const { return vespalib::btree::MinMaxAggregated(1, 1); }
};


template<>
inline vespalib::btree::MinMaxAggregated
DocIdMinMaxIterator<AttributeWeightPosting>::getAggregated() const
{
    vespalib::btree::MinMaxAggregated a;
    for (const AttributeWeightPosting *cur = _cur, *end = _end; cur != end; ++cur) {
        a.add(cur->getData());
    }
    return a;
};

} // namespace search

