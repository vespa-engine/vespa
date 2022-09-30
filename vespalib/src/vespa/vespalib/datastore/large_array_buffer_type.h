// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "array_store_config.h"
#include "buffer_type.h"
#include <vespa/vespalib/util/array.h>
#include <memory>

namespace vespalib::alloc { class MemoryAllocator; }

namespace vespalib::datastore {

template <typename EntryT> class ArrayStoreTypeMapper;

/*
 * Class representing buffer type for large arrays in ArrayStore
 */
template <typename EntryT>
class LargeArrayBufferType : public BufferType<Array<EntryT>>
{
    using AllocSpec = ArrayStoreConfig::AllocSpec;
    using ArrayType = Array<EntryT>;
    using ParentType = BufferType<ArrayType>;
    using ParentType::empty_entry;
    using CleanContext = typename ParentType::CleanContext;
    std::shared_ptr<alloc::MemoryAllocator> _memory_allocator;
public:
    LargeArrayBufferType(const AllocSpec& spec, std::shared_ptr<alloc::MemoryAllocator> memory_allocator, ArrayStoreTypeMapper<EntryT>& mapper) noexcept;
    ~LargeArrayBufferType() override;
    void cleanHold(void* buffer, size_t offset, ElemCount numElems, CleanContext cleanCtx) override;
    const vespalib::alloc::MemoryAllocator* get_memory_allocator() const override;
};

extern template class LargeArrayBufferType<uint8_t>;
extern template class LargeArrayBufferType<uint32_t>;
extern template class LargeArrayBufferType<int32_t>;
extern template class LargeArrayBufferType<std::string>;
extern template class LargeArrayBufferType<AtomicEntryRef>;

}
