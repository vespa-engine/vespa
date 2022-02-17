// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "buffer_type.h"
#include <memory>

namespace vespalib::alloc { class MemoryAllocator; }

namespace vespalib::datastore {

/*
 * Class representing buffer type for a normal unique store allocator.
 */
template <typename WrappedEntry>
class UniqueStoreBufferType : public BufferType<WrappedEntry>
{
    std::shared_ptr<alloc::MemoryAllocator> _memory_allocator;
public:
    UniqueStoreBufferType(uint32_t min_arrays, uint32_t max_arrays,
                          uint32_t num_arrays_for_new_buffer, float alloc_grow_factor,
                          std::shared_ptr<alloc::MemoryAllocator> memory_allocator) noexcept;
    ~UniqueStoreBufferType() override;
    const vespalib::alloc::MemoryAllocator* get_memory_allocator() const override;
};

}
