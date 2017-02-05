// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "postinglisttraits.h"

namespace search {

/**
 * Inner attribute iterator used for temporary posting lists (range
 * searches).
 */

template <typename P>
class DocIdIterator
{
public:
    DocIdIterator() : _cur(nullptr), _end(nullptr), _begin(nullptr) { }

    const P * operator->() const { return _cur; }

    DocIdIterator & operator++() {
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
        if (valid() && (docId > getKey())) {
            linearSeek(docId);
        } else {
            _cur = _begin;
            linearSeek(docId);
        }
    }

    void swap(DocIdIterator &rhs) {
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
DocIdIterator<AttributePosting>::getData(void) const
{
    return 1;	// default weight 1 for single value attributes
}


/**
 * Inner attribute iterator used for short posting lists (8 or less
 * documents).
 */

template <typename P>
class DocIdMinMaxIterator : public DocIdIterator<P>
{
public:
    DocIdMinMaxIterator(void)
        : DocIdIterator<P>()
    { }
    
    inline btree::MinMaxAggregated
    getAggregated() const {
        return btree::MinMaxAggregated(1, 1);
    }
};


template<>
inline btree::MinMaxAggregated
DocIdMinMaxIterator<AttributeWeightPosting>::getAggregated(void) const
{
    btree::MinMaxAggregated a;
    for (const AttributeWeightPosting *cur = _cur, *end = _end; cur != end; ++cur) {
        a.add(cur->getData());
    }
    return a;
};

} // namespace search

