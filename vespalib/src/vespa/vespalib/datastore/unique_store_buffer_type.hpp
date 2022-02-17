// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "unique_store_buffer_type.h"
#include "buffer_type.hpp"

namespace vespalib::datastore {

template <typename WrappedEntry>
UniqueStoreBufferType<WrappedEntry>::UniqueStoreBufferType(uint32_t min_arrays, uint32_t max_arrays,
                                                           uint32_t num_arrays_for_new_buffer, float alloc_grow_factor,
                                                           std::shared_ptr<alloc::MemoryAllocator> memory_allocator) noexcept
    : BufferType<WrappedEntry>(1u, min_arrays, max_arrays, num_arrays_for_new_buffer, alloc_grow_factor),
     _memory_allocator(std::move(memory_allocator))
{
}

template <typename WrappedEntry>
UniqueStoreBufferType<WrappedEntry>::~UniqueStoreBufferType() = default;

template <typename WrappedEntry>
const vespalib::alloc::MemoryAllocator*
UniqueStoreBufferType<WrappedEntry>::get_memory_allocator() const
{
    return _memory_allocator.get();
}

}
