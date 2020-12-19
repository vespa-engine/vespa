// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "atomic_entry_ref.h"
#include <string>

namespace vespalib::datastore {

using ElemCount = uint32_t;
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
        size_t &_extraUsedBytes;
        size_t &_extraHoldBytes;
    public:
        CleanContext(size_t &extraUsedBytes, size_t &extraHoldBytes)
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
    // Return number of reserved elements at start of buffer, to avoid
    // invalid reference and handle data at negative offset (alignment
    // hacks) as used by dense tensor store.
    virtual ElemCount getReservedElements(uint32_t bufferId) const;
    // Initialize reserved elements at start of buffer.
    virtual void initializeReservedElements(void *buffer, ElemCount reservedElements) = 0;
    virtual size_t elementSize() const = 0;
    virtual void cleanHold(void *buffer, size_t offset, ElemCount numElems, CleanContext cleanCtx) = 0;
    size_t getArraySize() const { return _arraySize; }
    void flushLastUsed();
    virtual void onActive(uint32_t bufferId, ElemCount *usedElems, ElemCount &deadElems, void *buffer);
    void onHold(const ElemCount *usedElems);
    virtual void onFree(ElemCount usedElems);

    /**
     * Calculate number of arrays to allocate for new buffer given how many elements are needed.
     */
    virtual size_t calcArraysToAlloc(uint32_t bufferId, ElemCount elementsNeeded, bool resizing) const;

    void clampMaxArrays(uint32_t maxArrays);

    uint32_t getActiveBuffers() const { return _activeBuffers; }
    size_t getMaxArrays() const { return _maxArrays; }
    uint32_t getNumArraysForNewBuffer() const { return _numArraysForNewBuffer; }
protected:
    uint32_t _arraySize;  // Number of elements in an allocation unit
    uint32_t _minArrays;  // Minimum number of arrays to allocate in a buffer
    uint32_t _maxArrays;  // Maximum number of arrays to allocate in a buffer
    // Number of arrays needed before allocating a new buffer instead of just resizing the first one
    uint32_t _numArraysForNewBuffer;
    float    _allocGrowFactor;
    uint32_t _activeBuffers;
    uint32_t _holdBuffers;
    size_t   _activeUsedElems;    // used elements in all but last active buffer
    size_t   _holdUsedElems;  // used elements in all held buffers
    const ElemCount *_lastUsedElems; // used elements in last active buffer
};

/**
 * Concrete class used to manage allocation and de-allocation of elements of type EntryType in data store buffers.
 */
template <typename EntryType, typename EmptyType = EntryType>
class BufferType : public BufferTypeBase
{
protected:
    static EntryType _emptyEntry;

public:
    BufferType() noexcept : BufferType(1,1,1) {}
    BufferType(const BufferType &rhs) = delete;
    BufferType & operator=(const BufferType &rhs) = delete;
    BufferType(BufferType && rhs) noexcept = default;
    BufferType & operator=(BufferType && rhs) noexcept = default;
    BufferType(uint32_t arraySize, uint32_t minArrays, uint32_t maxArrays) noexcept;
    BufferType(uint32_t arraySize, uint32_t minArrays, uint32_t maxArrays,
               uint32_t numArraysForNewBuffer, float allocGrowFactor) noexcept;
    ~BufferType();
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
