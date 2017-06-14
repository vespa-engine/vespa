// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "changevector.h"
#include <vespa/vespalib/util/array.hpp>
#include <vespa/searchlib/util/memoryusage.h>

namespace search {

template <typename T>
ChangeVectorT<T>::ChangeVectorT() : _tail(0) { }
    
template <typename T>
ChangeVectorT<T>::~ChangeVectorT() { }

template <typename T>
void
ChangeVectorT<T>::clear() {
    _v.clear();
    _docs.clear();
}

template <typename T>
void
ChangeVectorT<T>::push_back(const T & c)
{
    size_t index(size());
    _v.push_back(c);
    linkIn(c._doc, index, index);
}

template <typename T>
template <typename Accessor>
void
ChangeVectorT<T>::push_back(uint32_t doc, Accessor & ac)
{
    if (ac.size() <= 0) { return; }

    size_t index(size());
    _v.reserve(vespalib::roundUp2inN(index + ac.size()));
    for (size_t i(0), m(ac.size()); i < m; i++, ac.next()) {
        _v.push_back(T(ChangeBase::APPEND, doc, typename T::DataType(ac.value()), ac.weight()));
        _v.back().setNext(index + i + 1);
    }
    linkIn(doc, index, size() - 1);
}

template <typename T>
void
ChangeVectorT<T>::linkIn(uint32_t doc, size_t first, size_t last)
{
    if (first != 0 && (_v[_tail]._doc == doc)) {
        _v[_tail].setNext(first);
        _tail = last;
    } else {
        Map::iterator found(_docs.find(doc));
        if (found == _docs.end()) {
            _docs[doc] = last;
            if (_tail != first) {
                _v[_tail].setNext(first);
            }
            _tail = last;
        } else {
            uint32_t prev(found->second);
            for (; _v[_v[prev].getNext()]._doc == doc; prev = _v[prev].getNext());
            _v[last].setNext(_v[prev].getNext());
            _v[prev].setNext(first);
            found->second = last;
        }
    }
    _v[_tail].setNext(size());
}

template <typename T>
MemoryUsage
ChangeVectorT<T>::getMemoryUsage() const
{
    size_t usedBytes = _v.size() * sizeof(T) + _docs.getMemoryUsed();
    size_t allocBytes = _v.capacity() * sizeof(T) + _docs.getMemoryConsumption();
    return MemoryUsage(allocBytes, usedBytes, 0, 0);
}

} // namespace search

