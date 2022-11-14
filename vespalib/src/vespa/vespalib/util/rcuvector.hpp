// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "rcuvector.h"
#include <vespa/vespalib/util/array.hpp>
#include <cassert>

namespace vespalib {

template <typename T>
RcuVectorHeld<T>::RcuVectorHeld(size_t size, T&& data)
    : GenerationHeldBase(size),
      _data(std::move(data))
{ }

template <typename T>
RcuVectorHeld<T>::~RcuVectorHeld() = default;

template <typename T>
size_t RcuVectorBase<T>::calcNewSize(size_t baseSize) const {
    return _growStrategy.calc_new_size(baseSize);
}

template <typename T>
size_t RcuVectorBase<T>::calcNewSize() const {
    return calcNewSize(_data.capacity());
}

template <typename T>
void
RcuVectorBase<T>::unsafe_resize(size_t n) {
    _data.resize(n);
    update_vector_start();
}

template <typename T>
void
RcuVectorBase<T>::unsafe_reserve(size_t n) {
    _data.reserve(n);
    update_vector_start();
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
    _data.reset();
    _data.reserve(16);
}

template <typename T>
RcuVectorBase<T>::~RcuVectorBase() = default;

template <typename T>
void
RcuVectorBase<T>::expand(size_t newCapacity) {
    auto tmpData = create_replacement_vector();
    tmpData.reserve(newCapacity);
    for (const T & v : _data) {
        tmpData.push_back_fast(v);
    }
    replaceVector(std::move(tmpData));
}

template <typename T>
void
RcuVectorBase<T>::replaceVector(ArrayType replacement) {
    std::atomic_thread_fence(std::memory_order_release);
    replacement.swap(_data); // atomic switch of underlying data
    size_t holdSize = replacement.capacity() * sizeof(T);
    auto hold = std::make_unique<RcuVectorHeld<ArrayType>>(holdSize, std::move(replacement));
    _genHolder.insert(std::move(hold));
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
        auto tmpData = create_replacement_vector();
        tmpData.reserve(wantedCapacity);
        tmpData.resize(newSize);
        for (uint32_t i = 0; i < newSize; ++i) {
            tmpData[i] = _data[i];
        }
        std::atomic_thread_fence(std::memory_order_release);
        // Users of RCU vector must ensure that no readers use old size
        // after swap.  Attribute vectors uses _committedDocIdLimit for this.
        tmpData.swap(_data); // atomic switch of underlying data
        size_t holdSize = tmpData.capacity() * sizeof(T);
        auto hold = std::make_unique<RcuVectorHeld<ArrayType>>(holdSize, std::move(tmpData));
        _genHolder.insert(std::move(hold));
        onReallocation();
    }
}

template <typename T>
RcuVectorBase<T>::RcuVectorBase(GrowStrategy growStrategy,
                                GenerationHolderType &genHolder,
                                const Alloc &initialAlloc)
    : _data(initialAlloc),
      _vector_start(nullptr),
      _growStrategy(growStrategy),
      _genHolder(genHolder)
{
    _data.reserve(_growStrategy.getInitialCapacity());
    update_vector_start();
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
RcuVectorBase<T>::update_vector_start()
{
    _vector_start.store(_data.data(), std::memory_order_release);
}

template <typename T>
void
RcuVectorBase<T>::onReallocation()
{
    update_vector_start();
}

template <typename T>
void
RcuVector<T>::onReallocation() {
    RcuVectorBase<T>::onReallocation();
    _genHolderStore.assign_generation(_generation);
}

template <typename T>
RcuVector<T>::RcuVector()
    : RcuVectorBase<T>(GrowStrategy(16, 1.0, 0, 0), _genHolderStore),
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
    _genHolderStore.reclaim_all();
}

template <typename T>
void
RcuVector<T>::reclaim_memory(generation_t oldest_used_gen)
{
    _genHolderStore.reclaim(oldest_used_gen);
}

template <typename T>
MemoryUsage
RcuVector<T>::getMemoryUsage() const
{
    MemoryUsage retval(RcuVectorBase<T>::getMemoryUsage());
    retval.mergeGenerationHeldBytes(_genHolderStore.get_held_bytes());
    return retval;
}

}
