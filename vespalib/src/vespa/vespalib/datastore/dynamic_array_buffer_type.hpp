// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "dynamic_array_buffer_type.h"
#include <cassert>

namespace vespalib::datastore {

template <typename ElemT>
DynamicArrayBufferType<ElemT>::DynamicArrayBufferType(uint32_t array_size, const AllocSpec& spec, std::shared_ptr<alloc::MemoryAllocator> memory_allocator) noexcept
    : BufferTypeBase(calc_entry_size(array_size), dynamic_array_buffer_underflow_size, array_size, spec.min_entries_in_buffer, spec.max_entries_in_buffer, spec.num_entries_for_new_buffer, spec.allocGrowFactor),
      _memory_allocator(std::move(memory_allocator))
{
}

template <typename ElemT>
DynamicArrayBufferType<ElemT>::DynamicArrayBufferType(DynamicArrayBufferType&& rhs) noexcept = default;

template <typename ElemT>
DynamicArrayBufferType<ElemT>::~DynamicArrayBufferType() = default;

template <typename ElemT>
size_t
DynamicArrayBufferType<ElemT>::calc_entry_size(size_t array_size) noexcept
{
    auto entry_size = EntryMinAligner::align(sizeof(ElemType) * array_size + sizeof(uint32_t));
    if (entry_size >= 512) {
        entry_size = Aligner<64>::align(entry_size);
    }
    return entry_size;
}

template <typename ElemT>
size_t
DynamicArrayBufferType<ElemT>::calc_array_size(size_t entry_size) noexcept
{
    return (entry_size - sizeof(uint32_t)) / sizeof(ElemType);
}

template <typename ElemT>
void
DynamicArrayBufferType<ElemT>::destroy_entries(void* buffer, EntryCount num_entries)
{
    uint32_t array_size = _arraySize;
    for (uint32_t entry_idx = 0; entry_idx < num_entries; ++entry_idx) {
        auto e = get_entry(buffer, entry_idx);
        for (uint32_t elem_idx = 0; elem_idx < array_size; ++elem_idx) {
            e->~ElemType();
            ++e;
        }
    }
}

template <typename ElemT>
void
DynamicArrayBufferType<ElemT>::fallback_copy(void* new_buffer, const void* old_buffer, EntryCount num_entries)
{
    uint32_t array_size = _arraySize;
    for (uint32_t entry_idx = 0; entry_idx < num_entries; ++entry_idx) {
        auto d = get_entry(new_buffer, entry_idx);
        auto s = get_entry(old_buffer, entry_idx);
        set_dynamic_array_size(d, get_dynamic_array_size(s));
        for (uint32_t elem_idx = 0; elem_idx < array_size; ++elem_idx) {
            new (static_cast<void*>(d)) ElemType(*s);
            ++s;
            ++d;
        }
    }
}

template <typename ElemT>
void
DynamicArrayBufferType<ElemT>::initialize_reserved_entries(void* buffer, EntryCount reserved_entries)
{
    uint32_t array_size = _arraySize;
    const auto& empty = empty_entry();
    for (uint32_t entry_idx = 0; entry_idx < reserved_entries; ++entry_idx) {
        auto e = get_entry(buffer, entry_idx);
        set_dynamic_array_size(e, 0);
        for (uint32_t elem_idx = 0; elem_idx < array_size; ++elem_idx) {
            new (static_cast<void*>(e)) ElemType(empty);
            ++e;
        }
    }
}

template <typename ElemT>
void
DynamicArrayBufferType<ElemT>::clean_hold(void* buffer, size_t offset, EntryCount num_entries, CleanContext)
{
    uint32_t max_array_size = _arraySize;
    const auto& empty = empty_entry();
    for (uint32_t entry_idx = 0; entry_idx < num_entries; ++entry_idx) {
        auto e = get_entry(buffer, offset + entry_idx);
        auto array_size = get_dynamic_array_size(e);
        assert(array_size <= max_array_size);
        for (uint32_t elem_idx = 0; elem_idx < array_size; ++elem_idx) {
            *e = empty;
            ++e;
        }
    }
}

template <typename ElemT>
const ElemT&
DynamicArrayBufferType<ElemT>::empty_entry() noexcept
{
    // It's possible for ElemType to wrap e.g. an Alloc instance, which has a transitive
    // dependency on globally constructed allocator object(s). To avoid issues with global
    // construction order, initialize the sentinel on the first access.
    static ElemType empty = ElemType();
    return empty;
}

template <typename ElemT>
const vespalib::alloc::MemoryAllocator*
DynamicArrayBufferType<ElemT>::get_memory_allocator() const
{
    return _memory_allocator.get();
}

template <typename ElemT>
bool
DynamicArrayBufferType<ElemT>::is_dynamic_array_buffer_type() const noexcept
{
    return true;
}

}
