// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    for (size_t j = reservedElems; j != 0; --j) {
        new (static_cast<void *>(e)) EntryType(_emptyEntry);
        ++e;
    }
}

template <typename EntryType, typename EmptyType>
void
BufferType<EntryType, EmptyType>::cleanHold(void *buffer, size_t offset, ElemCount numElems, CleanContext)
{
    EntryType *e = static_cast<EntryType *>(buffer) + offset;
    for (size_t j = numElems; j != 0; --j) {
        *e = _emptyEntry;
        ++e;
    }
}

template <typename EntryType, typename EmptyType>
EntryType BufferType<EntryType, EmptyType>::_emptyEntry = EmptyType();

}
