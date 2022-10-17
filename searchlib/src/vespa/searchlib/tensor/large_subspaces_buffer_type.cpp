// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "large_subspaces_buffer_type.h"
#include "tensor_buffer_operations.h"
#include "tensor_buffer_type_mapper.h"
#include <vespa/vespalib/datastore/buffer_type.hpp>
#include <vespa/vespalib/util/array.hpp>
#include <vespa/vespalib/util/arrayref.h>

using vespalib::alloc::MemoryAllocator;

namespace search::tensor {

LargeSubspacesBufferType::LargeSubspacesBufferType(const AllocSpec& spec, std::shared_ptr<MemoryAllocator> memory_allocator, TensorBufferTypeMapper& type_mapper) noexcept
    : ParentType(1u, spec.minArraysInBuffer, spec.maxArraysInBuffer, spec.numArraysForNewBuffer, spec.allocGrowFactor),
      _memory_allocator(std::move(memory_allocator)),
      _ops(type_mapper.get_tensor_buffer_operations())
{
}

LargeSubspacesBufferType::~LargeSubspacesBufferType() = default;

void
LargeSubspacesBufferType::cleanHold(void* buffer, size_t offset, ElemCount numElems, CleanContext cleanCtx)
{
    auto elem = static_cast<ArrayType*>(buffer) + offset;
    for (size_t i = 0; i < numElems; ++i) {
        if (!elem->empty()) {
            cleanCtx.extraBytesCleaned(elem->size());
            _ops.reclaim_labels({elem->data(), elem->size()});
            ArrayType().swap(*elem);
        }
        ++elem;
    }
}

void
LargeSubspacesBufferType::destroyElements(void *buffer, ElemCount numElems)
{
    auto elem = static_cast<ArrayType*>(buffer);
    for (size_t i = 0; i < numElems; ++i) {
        if (!elem->empty()) {
            _ops.reclaim_labels({elem->data(), elem->size()});
            ArrayType().swap(*elem);
        }
        ++elem;
    }
}

void
LargeSubspacesBufferType::fallbackCopy(void *newBuffer, const void *oldBuffer, ElemCount numElems)
{
    auto old_elems = static_cast<const ArrayType*>(oldBuffer);
    auto new_elems = static_cast<ArrayType*>(newBuffer);
    for (size_t i = 0; i < numElems; ++i) {
        auto& old_elem = old_elems[i];
        new (new_elems + i) ArrayType(old_elem);
        if (!old_elem.empty()) {
            _ops.copied_labels(unconstify(vespalib::ConstArrayRef<char>(old_elem.data(), old_elem.size())));
        }
    }
}

void
LargeSubspacesBufferType::initializeReservedElements(void *buffer, ElemCount reservedElements)
{
    auto new_elems = static_cast<ArrayType*>(buffer);
    const auto& empty = empty_entry();
    for (size_t i = 0; i < reservedElements; ++i) {
        new (new_elems + i) ArrayType(empty);
    }
}

const vespalib::alloc::MemoryAllocator*
LargeSubspacesBufferType::get_memory_allocator() const
{
    return _memory_allocator.get();
}

}

namespace vespalib::datastore {

template class BufferType<Array<char>>;

}
