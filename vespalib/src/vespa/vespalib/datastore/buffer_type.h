// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "atomic_entry_ref.h"
#include <string>
#include <vector>

namespace vespalib::alloc { class MemoryAllocator; }

namespace vespalib::datastore {

using EntryCount = uint32_t;

/**
 * Abstract class used to manage allocation and de-allocation of a specific data type in underlying memory buffers in a data store.
 * Each buffer is owned by an instance of BufferState.
 *
 * This class handles allocation of both single elements (_arraySize = 1) and array of elements (_arraySize > 1).
 * The strategy for how to grow buffers is specified as well.
 */
class BufferTypeBase
{
public:
    using EntryCount = vespalib::datastore::EntryCount;
    class CleanContext {
    private:
        std::atomic<size_t> &_extraUsedBytes;
        std::atomic<size_t> &_extraHoldBytes;
    public:
        CleanContext(std::atomic<size_t>& extraUsedBytes, std::atomic<size_t>& extraHoldBytes)
            : _extraUsedBytes(extraUsedBytes),
              _extraHoldBytes(extraHoldBytes)
        {}
        void extraBytesCleaned(size_t value);
    };

    BufferTypeBase(const BufferTypeBase &rhs) = delete;
    BufferTypeBase & operator=(const BufferTypeBase &rhs) = delete;
    BufferTypeBase(BufferTypeBase &&rhs) noexcept = default;
    BufferTypeBase & operator=(BufferTypeBase &&rhs) noexcept = default;
    BufferTypeBase(uint32_t arraySize, uint32_t min_entries, uint32_t max_entries) noexcept;
    BufferTypeBase(uint32_t arraySize, uint32_t min_entries, uint32_t max_entries,
                   uint32_t num_entries_for_new_buffer, float allocGrowFactor) noexcept;
    virtual ~BufferTypeBase();
    virtual void destroy_entries(void *buffer, EntryCount num_entries) = 0;
    virtual void fallback_copy(void *newBuffer, const void *oldBuffer, EntryCount num_entries) = 0;

    /**
     * Return number of reserved entries at start of buffer, to avoid
     * invalid reference.
     */
    virtual EntryCount get_reserved_entries(uint32_t bufferId) const;

    /**
     * Initialize reserved elements at start of buffer.
     */
    virtual void initialize_reserved_entries(void *buffer, EntryCount reserved_entries) = 0;
    virtual size_t entry_size() const = 0; // Size of entry measured in bytes
    virtual void clean_hold(void *buffer, size_t offset, EntryCount num_entries, CleanContext cleanCtx) = 0;
    size_t getArraySize() const { return _arraySize; }
    virtual void on_active(uint32_t bufferId, std::atomic<EntryCount>* used_entries, std::atomic<EntryCount>* dead_entries, void* buffer);
    void on_hold(uint32_t buffer_id, const std::atomic<EntryCount>* used_entries, const std::atomic<EntryCount>* dead_entries);
    virtual void on_free(EntryCount used_entries);
    void resume_primary_buffer(uint32_t buffer_id, std::atomic<EntryCount>* used_entries, std::atomic<EntryCount>* dead_entries);
    virtual const alloc::MemoryAllocator* get_memory_allocator() const;

    /**
     * Calculate number of entries to allocate for new buffer given how many free entries are needed.
     */
    virtual size_t calc_entries_to_alloc(uint32_t bufferId, EntryCount free_entries_needed, bool resizing) const;

    void clamp_max_entries(uint32_t max_entries);

    uint32_t get_active_buffers_count() const { return _active_buffers.size(); }
    const std::vector<uint32_t>& get_active_buffers() const noexcept { return _active_buffers; }
    size_t get_max_entries() const { return _max_entries; }
    uint32_t get_scaled_num_entries_for_new_buffer() const;
    uint32_t get_num_entries_for_new_buffer() const noexcept { return _num_entries_for_new_buffer; }
protected:

    struct BufferCounts {
        EntryCount used_entries;
        EntryCount dead_entries;
        BufferCounts() : used_entries(0), dead_entries(0) {}
        BufferCounts(EntryCount used_entries_in, EntryCount dead_entries_in)
            : used_entries(used_entries_in), dead_entries(dead_entries_in)
        {}
    };

    /**
     * Tracks aggregated counts for all buffers that are in state ACTIVE.
     */
    class AggregatedBufferCounts {
    private:
        struct ActiveBufferCounts {
            const std::atomic<EntryCount>* used_ptr;
            const std::atomic<EntryCount>* dead_ptr;
            ActiveBufferCounts() noexcept : used_ptr(nullptr), dead_ptr(nullptr) {}
            ActiveBufferCounts(const std::atomic<EntryCount>* used_ptr_in, const std::atomic<EntryCount>* dead_ptr_in) noexcept
                : used_ptr(used_ptr_in), dead_ptr(dead_ptr_in)
            {}
        };
        std::vector<ActiveBufferCounts> _counts;

    public:
        AggregatedBufferCounts();
        void add_buffer(const std::atomic<EntryCount>* used_entries, const std::atomic<EntryCount>* dead_entries);
        void remove_buffer(const std::atomic<EntryCount>* used_entries, const std::atomic<EntryCount>* dead_entries);
        BufferCounts last_buffer() const;
        BufferCounts all_buffers() const;
        bool empty() const { return _counts.empty(); }
    };

    uint32_t _arraySize;  // Number of elements in an allocation unit
    uint32_t _min_entries;  // Minimum number of entries to allocate in a buffer
    uint32_t _max_entries;  // Maximum number of entries to allocate in a buffer
    // Number of entries needed before allocating a new buffer instead of just resizing the first one
    uint32_t _num_entries_for_new_buffer;
    float    _allocGrowFactor;
    uint32_t _holdBuffers;
    size_t   _hold_used_entries;  // Number of used entries in all held buffers for this type.
    AggregatedBufferCounts _aggr_counts;
    std::vector<uint32_t>  _active_buffers;
};

/**
 * Concrete class used to manage allocation and de-allocation of elements of type ElemType in data store buffers.
 */
template <typename ElemT, typename EmptyT = ElemT>
class BufferType : public BufferTypeBase
{
public:
    using ElemType = ElemT;
    using EmptyType = EmptyT;
protected:
    static const ElemType& empty_entry() noexcept;

public:
    BufferType() noexcept : BufferType(1,1,1) {}
    BufferType(const BufferType &rhs) = delete;
    BufferType & operator=(const BufferType &rhs) = delete;
    BufferType(BufferType && rhs) noexcept = default;
    BufferType & operator=(BufferType && rhs) noexcept = default;
    BufferType(uint32_t arraySize, uint32_t min_entries, uint32_t max_entries) noexcept;
    BufferType(uint32_t arraySize, uint32_t min_entries, uint32_t max_entries,
               uint32_t num_entries_for_new_buffer, float allocGrowFactor) noexcept;
    ~BufferType() override;
    void destroy_entries(void *buffer, EntryCount num_entries) override;
    void fallback_copy(void *newBuffer, const void *oldBuffer, EntryCount num_entries) override;
    void initialize_reserved_entries(void *buffer, EntryCount reserved_entries) override;
    void clean_hold(void *buffer, size_t offset, EntryCount num_entries, CleanContext cleanCxt) override;
    size_t entry_size() const override { return sizeof(ElemType) * _arraySize; }
};

extern template class BufferType<char>;
extern template class BufferType<uint8_t>;
extern template class BufferType<uint32_t>;
extern template class BufferType<uint64_t>;
extern template class BufferType<int32_t>;
extern template class BufferType<std::string>;
extern template class BufferType<AtomicEntryRef>;

}
