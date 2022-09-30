// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "large_array_buffer_type.h"
#include <vespa/vespalib/util/array.hpp>

namespace vespalib::datastore {

template <typename EntryT>
LargeArrayBufferType<EntryT>::LargeArrayBufferType(const AllocSpec& spec, std::shared_ptr<alloc::MemoryAllocator> memory_allocator, ArrayStoreTypeMapper<EntryT>&) noexcept
    : BufferType<Array<EntryT>>(1u, spec.minArraysInBuffer, spec.maxArraysInBuffer, spec.numArraysForNewBuffer, spec.allocGrowFactor),
      _memory_allocator(std::move(memory_allocator))
{
}

template <typename EntryT>
LargeArrayBufferType<EntryT>::~LargeArrayBufferType() = default;

template <typename EntryT>
void
LargeArrayBufferType<EntryT>::cleanHold(void* buffer, size_t offset, ElemCount numElems, CleanContext cleanCtx)
{
    ArrayType* elem = static_cast<ArrayType*>(buffer) + offset;
    const auto& empty = empty_entry();
    for (size_t i = 0; i < numElems; ++i) {
        cleanCtx.extraBytesCleaned(sizeof(EntryT) * elem->size());
        *elem = empty;
        ++elem;
    }
}

template <typename EntryT>
const vespalib::alloc::MemoryAllocator*
LargeArrayBufferType<EntryT>::get_memory_allocator() const
{
    return _memory_allocator.get();
}

}
