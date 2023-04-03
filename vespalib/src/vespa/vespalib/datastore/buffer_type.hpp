// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "buffer_type.h"

namespace vespalib::datastore {

template <typename ElemT, typename EmptyT>
BufferType<ElemT, EmptyT>::BufferType(uint32_t arraySize, uint32_t minArrays, uint32_t maxArrays) noexcept
    : BufferTypeBase(arraySize, minArrays, maxArrays)
{ }

template <typename ElemT, typename EmptyT>
BufferType<ElemT, EmptyT>::BufferType(uint32_t arraySize, uint32_t minArrays, uint32_t maxArrays,
                                  uint32_t numArraysForNewBuffer, float allocGrowFactor) noexcept
    : BufferTypeBase(arraySize, minArrays, maxArrays, numArraysForNewBuffer, allocGrowFactor)
{ }

template <typename ElemT, typename EmptyT>
BufferType<ElemT, EmptyT>::~BufferType() = default;

template <typename ElemT, typename EmptyT>
void
BufferType<ElemT, EmptyT>::destroyElements(void *buffer, ElemCount numElems)
{
    ElemType *e = static_cast<ElemType *>(buffer);
    for (size_t j = numElems; j != 0; --j) {
        e->~ElemType();
        ++e;
    }
}

template <typename ElemT, typename EmptyT>
void
BufferType<ElemT, EmptyT>::fallbackCopy(void *newBuffer, const void *oldBuffer, ElemCount numElems)
{
    ElemType *d = static_cast<ElemType *>(newBuffer);
    const ElemType *s = static_cast<const ElemType *>(oldBuffer);
    for (size_t j = numElems; j != 0; --j) {
        new (static_cast<void *>(d)) ElemType(*s);
        ++s;
        ++d;
    }
}

template <typename ElemT, typename EmptyT>
void
BufferType<ElemT, EmptyT>::initializeReservedElements(void *buffer, ElemCount reservedElems)
{
    ElemType *e = static_cast<ElemType *>(buffer);
    const auto& empty = empty_entry();
    for (size_t j = reservedElems; j != 0; --j) {
        new (static_cast<void *>(e)) ElemType(empty);
        ++e;
    }
}

template <typename ElemT, typename EmptyT>
void
BufferType<ElemT, EmptyT>::cleanHold(void *buffer, size_t offset, ElemCount numElems, CleanContext)
{
    ElemType *e = static_cast<ElemType *>(buffer) + offset;
    const auto& empty = empty_entry();
    for (size_t j = numElems; j != 0; --j) {
        *e = empty;
        ++e;
    }
}

template <typename ElemT, typename EmptyT>
const ElemT&
BufferType<ElemT, EmptyT>::empty_entry() noexcept
{
    // It's possible for ElemType to wrap e.g. an Alloc instance, which has a transitive
    // dependency on globally constructed allocator object(s). To avoid issues with global
    // construction order, initialize the sentinel on the first access.
    static ElemType empty = EmptyType();
    return empty;
}

}
