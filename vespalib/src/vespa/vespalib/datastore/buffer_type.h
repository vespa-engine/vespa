// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "atomic_entry_ref.h"
#include <string>
#include <vector>

namespace vespalib::alloc { class MemoryAllocator; }

namespace vespalib::datastore {

using ElemCount = uint64_t;

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
    using ElemCount = vespalib::datastore::ElemCount;
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
    BufferTypeBase(uint32_t arraySize, uint32_t minArrays, uint32_t maxArrays) noexcept;
    BufferTypeBase(uint32_t arraySize, uint32_t minArrays, uint32_t maxArrays,
                   uint32_t numArraysForNewBuffer, float allocGrowFactor) noexcept;
    virtual ~BufferTypeBase();
    virtual void destroyElements(void *buffer, ElemCount numElems) = 0;
    virtual void fallbackCopy(void *newBuffer, const void *oldBuffer, ElemCount numElems) = 0;

    /**
     * Return number of reserved elements at start of buffer, to avoid
     * invalid reference and handle data at negative offset (alignment
     * hacks) as used by dense tensor store.
     */
    virtual ElemCount getReservedElements(uint32_t bufferId) const;

    /**
     * Initialize reserved elements at start of buffer.
     */
    virtual void initializeReservedElements(void *buffer, ElemCount reservedElements) = 0;
    virtual size_t elementSize() const = 0;
    virtual void cleanHold(void *buffer, size_t offset, ElemCount numElems, CleanContext cleanCtx) = 0;
    size_t getArraySize() const { return _arraySize; }
    virtual void onActive(uint32_t bufferId, std::atomic<ElemCount>* usedElems, std::atomic<ElemCount>* deadElems, void* buffer);
    void onHold(uint32_t buffer_id, const std::atomic<ElemCount>* usedElems, const std::atomic<ElemCount>* deadElems);
    virtual void onFree(ElemCount usedElems);
    void resume_primary_buffer(uint32_t buffer_id, std::atomic<ElemCount>* used_elems, std::atomic<ElemCount>* dead_elems);
    virtual const alloc::MemoryAllocator* get_memory_allocator() const;

    /**
     * Calculate number of arrays to allocate for new buffer given how many elements are needed.
     */
    virtual size_t calcArraysToAlloc(uint32_t bufferId, ElemCount elementsNeeded, bool resizing) const;

    void clampMaxArrays(uint32_t maxArrays);

    uint32_t get_active_buffers_count() const { return _active_buffers.size(); }
    const std::vector<uint32_t>& get_active_buffers() const noexcept { return _active_buffers; }
    size_t getMaxArrays() const { return _maxArrays; }
    uint32_t get_scaled_num_arrays_for_new_buffer() const;
    uint32_t get_num_arrays_for_new_buffer() const noexcept { return _numArraysForNewBuffer; }
protected:

    struct BufferCounts {
        ElemCount used_elems;
        ElemCount dead_elems;
        BufferCounts() : used_elems(0), dead_elems(0) {}
        BufferCounts(ElemCount used_elems_in, ElemCount dead_elems_in)
                : used_elems(used_elems_in), dead_elems(dead_elems_in)
        {}
    };

    /**
     * Tracks aggregated counts for all buffers that are in state ACTIVE.
     */
    class AggregatedBufferCounts {
    private:
        struct Element {
            const std::atomic<ElemCount>* used_ptr;
            const std::atomic<ElemCount>* dead_ptr;
            Element() noexcept : used_ptr(nullptr), dead_ptr(nullptr) {}
            Element(const std::atomic<ElemCount>* used_ptr_in, const std::atomic<ElemCount>* dead_ptr_in) noexcept
                    : used_ptr(used_ptr_in), dead_ptr(dead_ptr_in)
            {}
        };
        std::vector<Element> _counts;

    public:
        AggregatedBufferCounts();
        void add_buffer(const std::atomic<ElemCount>* used_elems, const std::atomic<ElemCount>* dead_elems);
        void remove_buffer(const std::atomic<ElemCount>* used_elems, const std::atomic<ElemCount>* dead_elems);
        BufferCounts last_buffer() const;
        BufferCounts all_buffers() const;
        bool empty() const { return _counts.empty(); }
    };

    uint32_t _arraySize;  // Number of elements in an allocation unit
    uint32_t _minArrays;  // Minimum number of arrays to allocate in a buffer
    uint32_t _maxArrays;  // Maximum number of arrays to allocate in a buffer
    // Number of arrays needed before allocating a new buffer instead of just resizing the first one
    uint32_t _numArraysForNewBuffer;
    float    _allocGrowFactor;
    uint32_t _activeBuffers;
    uint32_t _holdBuffers;
    size_t   _holdUsedElems;  // Number of used elements in all held buffers for this type.
    AggregatedBufferCounts _aggr_counts;
    std::vector<uint32_t>  _active_buffers;
};

/**
 * Concrete class used to manage allocation and de-allocation of elements of type EntryType in data store buffers.
 */
template <typename EntryType, typename EmptyType = EntryType>
class BufferType : public BufferTypeBase
{
protected:
    static const EntryType& empty_entry() noexcept;

public:
    BufferType() noexcept : BufferType(1,1,1) {}
    BufferType(const BufferType &rhs) = delete;
    BufferType & operator=(const BufferType &rhs) = delete;
    BufferType(BufferType && rhs) noexcept = default;
    BufferType & operator=(BufferType && rhs) noexcept = default;
    BufferType(uint32_t arraySize, uint32_t minArrays, uint32_t maxArrays) noexcept;
    BufferType(uint32_t arraySize, uint32_t minArrays, uint32_t maxArrays,
               uint32_t numArraysForNewBuffer, float allocGrowFactor) noexcept;
    ~BufferType() override;
    void destroyElements(void *buffer, ElemCount numElems) override;
    void fallbackCopy(void *newBuffer, const void *oldBuffer, ElemCount numElems) override;
    void initializeReservedElements(void *buffer, ElemCount reservedElements) override;
    void cleanHold(void *buffer, size_t offset, ElemCount numElems, CleanContext cleanCxt) override;
    size_t elementSize() const override { return sizeof(EntryType); }
};

extern template class BufferType<char>;
extern template class BufferType<uint8_t>;
extern template class BufferType<uint32_t>;
extern template class BufferType<uint64_t>;
extern template class BufferType<int32_t>;
extern template class BufferType<std::string>;
extern template class BufferType<AtomicEntryRef>;

}
