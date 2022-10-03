// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/datastore/array_store_config.h>
#include <vespa/vespalib/datastore/buffer_type.h>
#include <vespa/vespalib/util/array.h>
#include <memory>

namespace vespalib::alloc { class MemoryAllocator; }

namespace search::tensor {

class TensorBufferOperations;
class TensorBufferTypeMapper;

/*
 * Class representing buffer type for tensors with a large number of
 * subspaces in array store. Tensor buffers are externally allocated
 * (cf. vespalib::Array).
 */
class LargeSubspacesBufferType : public vespalib::datastore::BufferType<vespalib::Array<char>>
{
    using AllocSpec = vespalib::datastore::ArrayStoreConfig::AllocSpec;
    using ArrayType = vespalib::Array<char>;
    using ParentType = vespalib::datastore::BufferType<ArrayType>;
    using CleanContext = typename ParentType::CleanContext;
    std::shared_ptr<vespalib::alloc::MemoryAllocator> _memory_allocator;
    TensorBufferOperations& _ops;
public:
    LargeSubspacesBufferType(const AllocSpec& spec, std::shared_ptr<vespalib::alloc::MemoryAllocator> memory_allocator, TensorBufferTypeMapper& type_mapper) noexcept;
    ~LargeSubspacesBufferType() override;
    void cleanHold(void* buffer, size_t offset, ElemCount numElems, CleanContext cleanCtx) override;
    void destroyElements(void *buffer, ElemCount numElems) override;
    void fallbackCopy(void *newBuffer, const void *oldBuffer, ElemCount numElems) override;
    void initializeReservedElements(void *buffer, ElemCount reservedElements) override;
    const vespalib::alloc::MemoryAllocator* get_memory_allocator() const override;
};

}
