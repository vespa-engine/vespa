// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "changevector.h"
#include <vespa/vespalib/util/memoryusage.h>
#include <vespa/vespalib/util/alloc.h>

namespace search {

namespace {

// This number is selected to be large enough to hold bursts between commits
constexpr size_t NUM_ELEMS_TO_RESERVE = 200;

}

template <typename T>
ChangeVectorT<T>::ChangeVectorT()
    : _v()
{
    _v.reserve(vespalib::roundUp2inN(NUM_ELEMS_TO_RESERVE, sizeof(T)));
}

template <typename T>
ChangeVectorT<T>::~ChangeVectorT() = default;

template <typename T>
void
ChangeVectorT<T>::clear() {
    _v.clear();
}

template <typename T>
void
ChangeVectorT<T>::push_back(const T & c)
{
    _v.push_back(c);
}

template <typename T>
template <typename Accessor>
void
ChangeVectorT<T>::push_back(uint32_t doc, Accessor & ac)
{
    if (ac.size() <= 0) { return; }

    _v.reserve(vespalib::roundUp2inN(size() + ac.size(), sizeof(T)));
    for (size_t i(0), m(ac.size()); i < m; i++, ac.next()) {
        _v.push_back(T(ChangeBase::APPEND, doc, typename T::DataType(ac.value()), ac.weight()));
    }
}

template <typename T>
vespalib::MemoryUsage
ChangeVectorT<T>::getMemoryUsage() const
{
    size_t usedBytes = _v.size() * sizeof(T);
    size_t allocBytes = _v.capacity() * sizeof(T);
    return vespalib::MemoryUsage(allocBytes, usedBytes, 0, 0);
}

template <typename T>
ChangeVectorT<T>::DocIdInsertOrder::DocIdInsertOrder(const Vector & v)
    : _v(v),
      _adjacent()
{
    _adjacent.reserve(v.size());
    uint32_t index(0);
    for (const auto & c : _v) {
        _adjacent.push_back((uint64_t(c._doc) << 32) | index++);
    }
    std::sort(_adjacent.begin(), _adjacent.end());
}

} // namespace search

