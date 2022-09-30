// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "small_array_buffer_type.h"

namespace vespalib::datastore {

template <typename EntryT>
SmallArrayBufferType<EntryT>::SmallArrayBufferType(uint32_t array_size, const AllocSpec& spec, std::shared_ptr<alloc::MemoryAllocator> memory_allocator, ArrayStoreTypeMapper<EntryT>&) noexcept
    : BufferType<EntryT>(array_size, spec.minArraysInBuffer, spec.maxArraysInBuffer, spec.numArraysForNewBuffer, spec.allocGrowFactor),
      _memory_allocator(std::move(memory_allocator))
{
}

template <typename EntryT>
SmallArrayBufferType<EntryT>::~SmallArrayBufferType() = default;

template <typename EntryT>
const vespalib::alloc::MemoryAllocator*
SmallArrayBufferType<EntryT>::get_memory_allocator() const
{
    return _memory_allocator.get();
}

}
