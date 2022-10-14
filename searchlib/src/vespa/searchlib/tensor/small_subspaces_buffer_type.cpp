// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "small_subspaces_buffer_type.h"
#include "tensor_buffer_operations.h"
#include "tensor_buffer_type_mapper.h"
#include <vespa/vespalib/util/arrayref.h>

using vespalib::alloc::MemoryAllocator;

namespace search::tensor {

SmallSubspacesBufferType::SmallSubspacesBufferType(uint32_t array_size, const AllocSpec& spec, std::shared_ptr<MemoryAllocator> memory_allocator, TensorBufferTypeMapper& type_mapper) noexcept
    : ParentType(array_size, spec.minArraysInBuffer, spec.maxArraysInBuffer, spec.numArraysForNewBuffer, spec.allocGrowFactor),
      _memory_allocator(std::move(memory_allocator)),
      _ops(type_mapper.get_tensor_buffer_operations())
{
}

SmallSubspacesBufferType::~SmallSubspacesBufferType() = default;

void
SmallSubspacesBufferType::cleanHold(void* buffer, size_t offset, ElemCount numElems, CleanContext)
{
    char* elem = static_cast<char *>(buffer) + offset;
    while (numElems >= getArraySize()) {
        _ops.reclaim_labels(vespalib::ArrayRef<char>(elem, getArraySize()));
        elem += getArraySize();
        numElems -= getArraySize();
    }
}

void
SmallSubspacesBufferType::destroyElements(void *buffer, ElemCount numElems)
{
    char* elem = static_cast<char *>(buffer);
    while (numElems >= getArraySize()) {
        _ops.reclaim_labels(vespalib::ArrayRef<char>(elem, getArraySize()));
        elem += getArraySize();
        numElems -= getArraySize();
    }
}

void
SmallSubspacesBufferType::fallbackCopy(void *newBuffer, const void *oldBuffer, ElemCount numElems)
{
    memcpy(newBuffer, oldBuffer, numElems);
    const char *elem = static_cast<const char *>(oldBuffer);
    while (numElems >= getArraySize()) {
        _ops.copied_labels(unconstify(vespalib::ConstArrayRef<char>(elem, getArraySize())));
        elem += getArraySize();
        numElems -= getArraySize();
    }
}

void
SmallSubspacesBufferType::initializeReservedElements(void *buffer, ElemCount reservedElements)
{
    memset(buffer, 0, reservedElements);
}

const vespalib::alloc::MemoryAllocator*
SmallSubspacesBufferType::get_memory_allocator() const
{
    return _memory_allocator.get();
}

}
