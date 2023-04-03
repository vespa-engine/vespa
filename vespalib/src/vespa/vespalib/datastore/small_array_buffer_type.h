// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "array_store_config.h"
#include "buffer_type.h"
#include <memory>

namespace vespalib::alloc { class MemoryAllocator; }

namespace vespalib::datastore {

/*
 * Class representing buffer type for small arrays in ArrayStore
 */
template <typename ElemT>
class SmallArrayBufferType : public BufferType<ElemT>
{
    using AllocSpec = ArrayStoreConfig::AllocSpec;
    std::shared_ptr<alloc::MemoryAllocator> _memory_allocator;
public:
    SmallArrayBufferType(const SmallArrayBufferType&) = delete;
    SmallArrayBufferType& operator=(const SmallArrayBufferType&) = delete;
    SmallArrayBufferType(SmallArrayBufferType&&) noexcept = default;
    SmallArrayBufferType& operator=(SmallArrayBufferType&&) noexcept = default;
    SmallArrayBufferType(uint32_t array_size, const AllocSpec& spec, std::shared_ptr<alloc::MemoryAllocator> memory_allocator) noexcept;
    template <typename TypeMapper>
    SmallArrayBufferType(uint32_t array_size, const AllocSpec& spec, std::shared_ptr<alloc::MemoryAllocator> memory_allocator, TypeMapper&) noexcept
        : SmallArrayBufferType(array_size, spec, std::move(memory_allocator))
    {
    }
    ~SmallArrayBufferType() override;
    const vespalib::alloc::MemoryAllocator* get_memory_allocator() const override;
};

extern template class SmallArrayBufferType<uint8_t>;
extern template class SmallArrayBufferType<uint32_t>;
extern template class SmallArrayBufferType<int32_t>;
extern template class SmallArrayBufferType<std::string>;
extern template class SmallArrayBufferType<AtomicEntryRef>;

}
