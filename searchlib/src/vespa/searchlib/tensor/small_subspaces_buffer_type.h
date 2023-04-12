// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/datastore/array_store_config.h>
#include <vespa/vespalib/datastore/buffer_type.h>
#include <memory>

namespace vespalib::alloc { class MemoryAllocator; }

namespace search::tensor {

class TensorBufferOperations;
class TensorBufferTypeMapper;

/*
 * Class representing buffer type for tensors with a small number of
 * subspaces in array store. Tensor buffers are internal in data store buffer.
 */
class SmallSubspacesBufferType : public vespalib::datastore::BufferType<char>
{
    using AllocSpec = vespalib::datastore::ArrayStoreConfig::AllocSpec;
    using ParentType = vespalib::datastore::BufferType<char>;
    std::shared_ptr<vespalib::alloc::MemoryAllocator> _memory_allocator;
    TensorBufferOperations& _ops;
public:
    SmallSubspacesBufferType(const SmallSubspacesBufferType&) = delete;
    SmallSubspacesBufferType& operator=(const SmallSubspacesBufferType&) = delete;
    SmallSubspacesBufferType(SmallSubspacesBufferType&&) noexcept = default;
    SmallSubspacesBufferType& operator=(SmallSubspacesBufferType&&) noexcept = delete;
    SmallSubspacesBufferType(uint32_t array_size, const AllocSpec& spec, std::shared_ptr<vespalib::alloc::MemoryAllocator> memory_allocator, TensorBufferTypeMapper& type_mapper) noexcept;
    ~SmallSubspacesBufferType() override;
    void clean_hold(void* buffer, size_t offset, EntryCount num_entries, CleanContext cleanCtx) override;
    void destroy_entries(void *buffer, EntryCount num_entries) override;
    void fallback_copy(void *newBuffer, const void *oldBuffer, EntryCount num_entries) override;
    void initialize_reserved_entries(void *buffer, EntryCount reserved_entries) override;
    const vespalib::alloc::MemoryAllocator* get_memory_allocator() const override;
};

}
