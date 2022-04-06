// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "buffer_type.h"

namespace vespalib::datastore {

template <typename EntryType, typename EmptyType>
BufferType<EntryType, EmptyType>::BufferType(uint32_t arraySize, uint32_t minArrays, uint32_t maxArrays) noexcept
    : BufferTypeBase(arraySize, minArrays, maxArrays)
{ }

template <typename EntryType, typename EmptyType>
BufferType<EntryType, EmptyType>::BufferType(uint32_t arraySize, uint32_t minArrays, uint32_t maxArrays,
                                  uint32_t numArraysForNewBuffer, float allocGrowFactor) noexcept
    : BufferTypeBase(arraySize, minArrays, maxArrays, numArraysForNewBuffer, allocGrowFactor)
{ }

template <typename EntryType, typename EmptyType>
BufferType<EntryType, EmptyType>::~BufferType() = default;

template <typename EntryType, typename EmptyType>
void
BufferType<EntryType, EmptyType>::destroyElements(void *buffer, ElemCount numElems)
{
    EntryType *e = static_cast<EntryType *>(buffer);
    for (size_t j = numElems; j != 0; --j) {
        e->~EntryType();
        ++e;
    }
}

template <typename EntryType, typename EmptyType>
void
BufferType<EntryType, EmptyType>::fallbackCopy(void *newBuffer,
                                    const void *oldBuffer,
                                    ElemCount numElems)
{
    EntryType *d = static_cast<EntryType *>(newBuffer);
    const EntryType *s = static_cast<const EntryType *>(oldBuffer);
    for (size_t j = numElems; j != 0; --j) {
        new (static_cast<void *>(d)) EntryType(*s);
        ++s;
        ++d;
    }
}

template <typename EntryType, typename EmptyType>
void
BufferType<EntryType, EmptyType>::initializeReservedElements(void *buffer, ElemCount reservedElems)
{
    EntryType *e = static_cast<EntryType *>(buffer);
    const auto& empty = empty_entry();
    for (size_t j = reservedElems; j != 0; --j) {
        new (static_cast<void *>(e)) EntryType(empty);
        ++e;
    }
}

template <typename EntryType, typename EmptyType>
void
BufferType<EntryType, EmptyType>::cleanHold(void *buffer, size_t offset, ElemCount numElems, CleanContext)
{
    EntryType *e = static_cast<EntryType *>(buffer) + offset;
    const auto& empty = empty_entry();
    for (size_t j = numElems; j != 0; --j) {
        *e = empty;
        ++e;
    }
}

template <typename EntryType, typename EmptyType>
const EntryType&
BufferType<EntryType, EmptyType>::empty_entry() noexcept
{
    // It's possible for EntryType to wrap e.g. an Alloc instance, which has a transitive
    // dependency on globally constructed allocator object(s). To avoid issues with global
    // construction order, initialize the sentinel on the first access.
    static EntryType empty = EmptyType();
    return empty;
}

}
