// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "small_subspaces_buffer_type.h"
#include "tensor_buffer_operations.h"
#include "tensor_buffer_type_mapper.h"
#include <vespa/vespalib/util/arrayref.h>

using vespalib::alloc::MemoryAllocator;

namespace search::tensor {

SmallSubspacesBufferType::SmallSubspacesBufferType(uint32_t array_size, const AllocSpec& spec, std::shared_ptr<MemoryAllocator> memory_allocator, TensorBufferTypeMapper& type_mapper) noexcept
    : ParentType(array_size, spec.min_entries_in_buffer, spec.max_entries_in_buffer, spec.num_entries_for_new_buffer, spec.allocGrowFactor),
      _memory_allocator(std::move(memory_allocator)),
      _ops(type_mapper.get_tensor_buffer_operations())
{
}

SmallSubspacesBufferType::~SmallSubspacesBufferType() = default;

void
SmallSubspacesBufferType::clean_hold(void* buffer, size_t offset, EntryCount num_entries, CleanContext)
{
    char* elem = static_cast<char *>(buffer) + offset * getArraySize();
    while (num_entries >= 1) {
        _ops.reclaim_labels(vespalib::ArrayRef<char>(elem, getArraySize()));
        elem += getArraySize();
        --num_entries;
    }
}

void
SmallSubspacesBufferType::destroy_entries(void *buffer, EntryCount num_entries)
{
    char* elem = static_cast<char *>(buffer);
    while (num_entries >= 1) {
        _ops.reclaim_labels(vespalib::ArrayRef<char>(elem, getArraySize()));
        elem += getArraySize();
        --num_entries;
    }
}

void
SmallSubspacesBufferType::fallback_copy(void *newBuffer, const void *oldBuffer, EntryCount num_entries)
{
    if (num_entries > 0) {
        memcpy(newBuffer, oldBuffer, num_entries * getArraySize());
    }
    const char *elem = static_cast<const char *>(oldBuffer);
    while (num_entries >= 1) {
        _ops.copied_labels(unconstify(vespalib::ConstArrayRef<char>(elem, getArraySize())));
        elem += getArraySize();
        --num_entries;
    }
}

void
SmallSubspacesBufferType::initialize_reserved_entries(void *buffer, EntryCount reserved_entries)
{
    memset(buffer, 0, reserved_entries * getArraySize());
}

const vespalib::alloc::MemoryAllocator*
SmallSubspacesBufferType::get_memory_allocator() const
{
    return _memory_allocator.get();
}

}
