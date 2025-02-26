// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

template <typename EntryT>
class UniqueStoreEntry;

extern template class BufferType<UniqueStoreEntry<int8_t>>;
extern template class BufferType<UniqueStoreEntry<int16_t>>;
extern template class BufferType<UniqueStoreEntry<int32_t>>;
extern template class BufferType<UniqueStoreEntry<int64_t>>;
extern template class BufferType<UniqueStoreEntry<uint32_t>>;
extern template class BufferType<UniqueStoreEntry<float>>;
extern template class BufferType<UniqueStoreEntry<double>>;

extern template class UniqueStoreBufferType<UniqueStoreEntry<int8_t>>;
extern template class UniqueStoreBufferType<UniqueStoreEntry<int16_t>>;
extern template class UniqueStoreBufferType<UniqueStoreEntry<int32_t>>;
extern template class UniqueStoreBufferType<UniqueStoreEntry<int64_t>>;
extern template class UniqueStoreBufferType<UniqueStoreEntry<uint32_t>>;
extern template class UniqueStoreBufferType<UniqueStoreEntry<float>>;
extern template class UniqueStoreBufferType<UniqueStoreEntry<double>>;

}
