// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "large_array_buffer_type.h"
#include <vespa/vespalib/util/array.hpp>

namespace vespalib::datastore {

template <typename ElemT>
LargeArrayBufferType<ElemT>::LargeArrayBufferType(const AllocSpec& spec, std::shared_ptr<alloc::MemoryAllocator> memory_allocator) noexcept
    : BufferType<Array<ElemT>>(1u, spec.minArraysInBuffer, spec.maxArraysInBuffer, spec.numArraysForNewBuffer, spec.allocGrowFactor),
      _memory_allocator(std::move(memory_allocator))
{
}

template <typename ElemT>
LargeArrayBufferType<ElemT>::~LargeArrayBufferType() = default;

template <typename ElemT>
void
LargeArrayBufferType<ElemT>::cleanHold(void* buffer, size_t offset, ElemCount numElems, CleanContext cleanCtx)
{
    ArrayType* elem = static_cast<ArrayType*>(buffer) + offset;
    const auto& empty = empty_entry();
    for (size_t i = 0; i < numElems; ++i) {
        cleanCtx.extraBytesCleaned(sizeof(ElemT) * elem->size());
        *elem = empty;
        ++elem;
    }
}

template <typename ElemT>
const vespalib::alloc::MemoryAllocator*
LargeArrayBufferType<ElemT>::get_memory_allocator() const
{
    return _memory_allocator.get();
}

}
