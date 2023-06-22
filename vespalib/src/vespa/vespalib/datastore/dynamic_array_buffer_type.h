// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "buffer_type.h"
#include "aligner.h"
#include "array_store_config.h"
#include <algorithm>
#include <memory>

namespace vespalib::datastore {

/**
 * Concrete class used to manage allocation and de-allocation of
 * elements of type ElemType in data store buffers.
 *
 * Layout of each entry is:
 *
 * elements[array_size]                - array of elements in entry
 * padding                             - to align entries
 * dynamic_array_size                  - number of array elements that should
 *                                       be visible to reader.
 */
template <typename ElemT>
class DynamicArrayBufferType : public BufferTypeBase
{
    using AllocSpec = ArrayStoreConfig::AllocSpec;
    std::shared_ptr<alloc::MemoryAllocator> _memory_allocator;

public:
    using ElemType = ElemT;

    static constexpr size_t entry_min_align = std::max(alignof(uint32_t), alignof(ElemT));
    using EntryMinAligner = Aligner<entry_min_align>;
    static constexpr uint32_t dynamic_array_buffer_underflow_size = 64u;
protected:
    static const ElemType& empty_entry() noexcept;
    ElemType* get_entry(void *buffer, size_t offset) noexcept { return get_entry(buffer, offset, entry_size()); }
    const ElemType* get_entry(const void *buffer, size_t offset) const noexcept { return get_entry(buffer, offset, entry_size()); }
public:
    DynamicArrayBufferType(const DynamicArrayBufferType &rhs) = delete;
    DynamicArrayBufferType & operator=(const DynamicArrayBufferType &rhs) = delete;
    DynamicArrayBufferType(DynamicArrayBufferType&& rhs) noexcept;
    DynamicArrayBufferType & operator=(DynamicArrayBufferType && rhs) noexcept = default;
    DynamicArrayBufferType(uint32_t array_size, const AllocSpec& spec, std::shared_ptr<alloc::MemoryAllocator> memory_allocator) noexcept;
    template <typename TypeMapper>
    DynamicArrayBufferType(uint32_t array_size, const AllocSpec& spec, std::shared_ptr<alloc::MemoryAllocator> memory_allocator, TypeMapper&) noexcept
        : DynamicArrayBufferType(array_size, spec, std::move(memory_allocator))
    {
    }
    ~DynamicArrayBufferType() override;
    void destroy_entries(void* buffer, EntryCount num_entries) override;
    void fallback_copy(void* new_buffer, const void* old_buffer, EntryCount num_entries) override;
    void initialize_reserved_entries(void* buffer, EntryCount reserved_entries) override;
    void clean_hold(void* buffer, size_t offset, EntryCount num_entries, CleanContext cleanCxt) override;
    const vespalib::alloc::MemoryAllocator* get_memory_allocator() const override;
    static size_t calc_entry_size(size_t array_size) noexcept;
    static size_t calc_array_size(size_t entry_size) noexcept;
    static ElemType* get_entry(void* buffer, size_t offset, uint32_t entry_size) noexcept { return reinterpret_cast<ElemType*>(static_cast<char*>(buffer) + offset * entry_size); }
    static const ElemType* get_entry(const void* buffer, size_t offset, uint32_t entry_size) noexcept { return reinterpret_cast<const ElemType*>(static_cast<const char*>(buffer) + offset * entry_size); }
    static uint32_t get_dynamic_array_size(const ElemType* buffer) noexcept { return *(reinterpret_cast<const uint32_t*>(buffer) - 1); }
    static void set_dynamic_array_size(ElemType* buffer, uint32_t array_size) noexcept { *(reinterpret_cast<uint32_t*>(buffer) - 1) = array_size; }
    bool is_dynamic_array_buffer_type() const noexcept override;
};

extern template class DynamicArrayBufferType<char>;
extern template class DynamicArrayBufferType<int8_t>;
extern template class DynamicArrayBufferType<int16_t>;
extern template class DynamicArrayBufferType<int32_t>;
extern template class DynamicArrayBufferType<int64_t>;
extern template class DynamicArrayBufferType<float>;
extern template class DynamicArrayBufferType<double>;
extern template class DynamicArrayBufferType<AtomicEntryRef>;

}
