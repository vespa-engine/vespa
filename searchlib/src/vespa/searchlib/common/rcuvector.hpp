// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "rcuvector.h"
#include <vespa/vespalib/util/array.hpp>

namespace search {
namespace attribute {

template <typename T>
RcuVectorHeld<T>::RcuVectorHeld(size_t size, std::unique_ptr<T> data)
    : vespalib::GenerationHeldBase(size),
      _data(std::move(data))
{ }

template <typename T>
RcuVectorHeld<T>::~RcuVectorHeld() { }

template <typename T>
void
RcuVectorBase<T>::unsafe_resize(size_t n) {
    _data.resize(n);
}

template <typename T>
void
RcuVectorBase<T>::unsafe_reserve(size_t n) {
    _data.reserve(n);
}

template <typename T>
void
RcuVectorBase<T>::ensure_size(size_t n, T fill) {
    reserve(n);
    while (size() < n) {
        _data.push_back(fill);
    }
}

template <typename T>
void
RcuVectorBase<T>::reset() {
    // Assumes no readers at this moment
    Array().swap(_data);
    _data.reserve(16);
}

template <typename T>
RcuVectorBase<T>::~RcuVectorBase() { }

template <typename T>
void
RcuVectorBase<T>::expand(size_t newCapacity) {
    std::unique_ptr<Array> tmpData(new Array());
    tmpData->reserve(newCapacity);
    for (const T & v : _data) {
        tmpData->push_back_fast(v);
    }
    tmpData->swap(_data); // atomic switch of underlying data
    size_t holdSize = tmpData->capacity() * sizeof(T);
    vespalib::GenerationHeldBase::UP hold(new RcuVectorHeld<Array>(holdSize, std::move(tmpData)));
    _genHolder.hold(std::move(hold));
    onReallocation();
}

template <typename T>
void
RcuVectorBase<T>::expandAndInsert(const T & v)
{
    expand(calcNewSize());
    assert(_data.size() < _data.capacity());
    _data.push_back(v);
}

template <typename T>
void
RcuVectorBase<T>::shrink(size_t newSize)
{
    assert(newSize <= _data.size());
    _data.resize(newSize);
    size_t wantedCapacity = calcNewSize(newSize);
    if (wantedCapacity >= _data.capacity()) {
        return;
    }
    if (!_data.try_unreserve(wantedCapacity)) {
        std::unique_ptr <Array> tmpData(new Array());
        tmpData->reserve(wantedCapacity);
        tmpData->resize(newSize);
        for (uint32_t i = 0; i < newSize; ++i) {
            (*tmpData)[i] = _data[i];
        }
        // Users of RCU vector must ensure that no readers use old size
        // after swap.  Attribute vectors uses _committedDocIdLimit for this.
        tmpData->swap(_data); // atomic switch of underlying data
        size_t holdSize = tmpData->capacity() * sizeof(T);
        vespalib::GenerationHeldBase::UP hold(new RcuVectorHeld<Array>(holdSize, std::move(tmpData)));
        _genHolder.hold(std::move(hold));
        onReallocation();
    }
}

template <typename T>
RcuVectorBase<T>::RcuVectorBase(GenerationHolder &genHolder,
                                const Alloc &initialAlloc)
    : _data(initialAlloc),
      _growPercent(100),
      _growDelta(0),
      _genHolder(genHolder)
{
    _data.reserve(16);
}

template <typename T>
RcuVectorBase<T>::RcuVectorBase(size_t initialCapacity,
                                size_t growPercent,
                                size_t growDelta,
                                GenerationHolder &genHolder,
                                const Alloc &initialAlloc)
    : _data(initialAlloc),
      _growPercent(growPercent),
      _growDelta(growDelta),
      _genHolder(genHolder)
{
    _data.reserve(initialCapacity);
}

template <typename T>
RcuVectorBase<T>::RcuVectorBase(GrowStrategy growStrategy,
                                GenerationHolder &genHolder,
                                const Alloc &initialAlloc)
    : RcuVectorBase(growStrategy.getDocsInitialCapacity(), growStrategy.getDocsGrowPercent(),
                    growStrategy.getDocsGrowDelta(), genHolder, initialAlloc)
{
}

template <typename T>
MemoryUsage
RcuVectorBase<T>::getMemoryUsage() const
{
    MemoryUsage retval;
    retval.incAllocatedBytes(_data.capacity() * sizeof(T));
    retval.incUsedBytes(_data.size() * sizeof(T));
    return retval;
}

template <typename T>
void
RcuVectorBase<T>::onReallocation() { }

template <typename T>
void
RcuVector<T>::onReallocation() {
    _genHolderStore.transferHoldLists(_generation);
}

template <typename T>
RcuVector<T>::RcuVector()
    : RcuVectorBase<T>(_genHolderStore),
      _generation(0),
      _genHolderStore()
{ }

template <typename T>
RcuVector<T>::RcuVector(size_t initialCapacity, size_t growPercent, size_t growDelta)
    : RcuVectorBase<T>(initialCapacity, growPercent, growDelta, _genHolderStore),
      _generation(0),
      _genHolderStore()
{ }

template <typename T>
RcuVector<T>::RcuVector(GrowStrategy growStrategy)
    : RcuVectorBase<T>(growStrategy, _genHolderStore),
      _generation(0),
      _genHolderStore()
{ }

template <typename T>
RcuVector<T>::~RcuVector()
{
    _genHolderStore.clearHoldLists();
}

template <typename T>
void
RcuVector<T>::removeOldGenerations(generation_t firstUsed)
{
    _genHolderStore.trimHoldLists(firstUsed);
}

template <typename T>
MemoryUsage
RcuVector<T>::getMemoryUsage() const
{
    MemoryUsage retval(RcuVectorBase<T>::getMemoryUsage());
    retval.mergeGenerationHeldBytes(_genHolderStore.getHeldBytes());
    return retval;
}

}
}
