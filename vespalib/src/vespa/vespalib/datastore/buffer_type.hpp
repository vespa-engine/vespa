// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "buffer_type.h"

namespace vespalib::datastore {

template <typename ElemT, typename EmptyT>
BufferType<ElemT, EmptyT>::BufferType(uint32_t arraySize, uint32_t min_entries, uint32_t max_entries) noexcept
    : BufferTypeBase(arraySize * sizeof(ElemT), 0u, arraySize, min_entries, max_entries)
{ }

template <typename ElemT, typename EmptyT>
BufferType<ElemT, EmptyT>::BufferType(uint32_t arraySize, uint32_t min_entries, uint32_t max_entries,
                                  uint32_t num_entries_for_new_buffer, float allocGrowFactor) noexcept
    : BufferTypeBase(arraySize * sizeof(ElemT), 0u, arraySize, min_entries, max_entries, num_entries_for_new_buffer, allocGrowFactor)
{ }

template <typename ElemT, typename EmptyT>
BufferType<ElemT, EmptyT>::~BufferType() = default;

template <typename ElemT, typename EmptyT>
void
BufferType<ElemT, EmptyT>::destroy_entries(void *buffer, EntryCount num_entries)
{
    auto num_elems = num_entries * getArraySize();
    ElemType *e = static_cast<ElemType *>(buffer);
    for (size_t j = num_elems; j != 0; --j) {
        e->~ElemType();
        ++e;
    }
}

template <typename ElemT, typename EmptyT>
void
BufferType<ElemT, EmptyT>::fallback_copy(void *newBuffer, const void *oldBuffer, EntryCount num_entries)
{
    auto num_elems = num_entries * getArraySize();
    ElemType *d = static_cast<ElemType *>(newBuffer);
    const ElemType *s = static_cast<const ElemType *>(oldBuffer);
    for (size_t j = num_elems; j != 0; --j) {
        new (static_cast<void *>(d)) ElemType(*s);
        ++s;
        ++d;
    }
}

template <typename ElemT, typename EmptyT>
void
BufferType<ElemT, EmptyT>::initialize_reserved_entries(void *buffer, EntryCount reserved_entries)
{
    auto reserved_elems = reserved_entries * getArraySize();
    ElemType *e = static_cast<ElemType *>(buffer);
    const auto& empty = empty_entry();
    for (size_t j = reserved_elems; j != 0; --j) {
        new (static_cast<void *>(e)) ElemType(empty);
        ++e;
    }
}

template <typename ElemT, typename EmptyT>
void
BufferType<ElemT, EmptyT>::clean_hold(void *buffer, size_t offset, EntryCount num_entries, CleanContext)
{
    auto num_elems = num_entries * getArraySize();
    ElemType *e = static_cast<ElemType *>(buffer) + offset * getArraySize();
    const auto& empty = empty_entry();
    for (size_t j = num_elems; j != 0; --j) {
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
