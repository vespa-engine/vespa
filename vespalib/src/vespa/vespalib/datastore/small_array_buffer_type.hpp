// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "small_array_buffer_type.h"

namespace vespalib::datastore {

template <typename ElemT>
SmallArrayBufferType<ElemT>::SmallArrayBufferType(uint32_t array_size, const AllocSpec& spec, std::shared_ptr<alloc::MemoryAllocator> memory_allocator) noexcept
    : BufferType<ElemT>(array_size, spec.minArraysInBuffer, spec.maxArraysInBuffer, spec.numArraysForNewBuffer, spec.allocGrowFactor),
      _memory_allocator(std::move(memory_allocator))
{
}

template <typename ElemT>
SmallArrayBufferType<ElemT>::~SmallArrayBufferType() = default;

template <typename ElemT>
const vespalib::alloc::MemoryAllocator*
SmallArrayBufferType<ElemT>::get_memory_allocator() const
{
    return _memory_allocator.get();
}

}
