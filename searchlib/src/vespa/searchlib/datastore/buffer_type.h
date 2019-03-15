// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <cstddef>

namespace search::datastore {

/**
 * Abstract class used to manage allocation and de-allocation of a specific data type in underlying memory buffers in a data store.
 * Each buffer is owned by an instance of BufferState.
 *
 * This class handles allocation of both single elements (_arraySize = 1) and array of elements (_arraySize > 1).
 * The strategy for how to grow buffers is specified as well.
 */
class BufferTypeBase
{
protected:
    uint32_t _arraySize;  // Number of elements in an allocation unit
    uint32_t _minArrays;  // Minimum number of arrays to allocate in a buffer
    uint32_t _maxArrays;  // Maximum number of arrays to allocate in a buffer
    // Number of arrays needed before allocating a new buffer instead of just resizing the first one
    uint32_t _numArraysForNewBuffer;
    float _allocGrowFactor;
    uint32_t _activeBuffers;
    uint32_t _holdBuffers;
    size_t _activeUsedElems;    // used elements in all but last active buffer
    size_t _holdUsedElems;  // used elements in all held buffers
    const size_t *_lastUsedElems; // used elements in last active buffer

public:
    class CleanContext {
    private:
        uint64_t &_extraBytes;
    public:
        CleanContext(uint64_t &extraBytes) : _extraBytes(extraBytes) {}
        void extraBytesCleaned(uint64_t value);
    };
    
    BufferTypeBase(const BufferTypeBase &rhs) = delete;
    BufferTypeBase & operator=(const BufferTypeBase &rhs) = delete;
    BufferTypeBase(uint32_t arraySize, uint32_t minArrays, uint32_t maxArrays);
    BufferTypeBase(uint32_t arraySize, uint32_t minArrays, uint32_t maxArrays,
                   uint32_t numArraysForNewBuffer, float allocGrowFactor);
    virtual ~BufferTypeBase();
    virtual void destroyElements(void *buffer, size_t numElements) = 0;
    virtual void fallbackCopy(void *newBuffer, const void *oldBuffer, size_t numElements) = 0;
    // Return number of reserved elements at start of buffer, to avoid
    // invalid reference and handle data at negative offset (alignment
    // hacks) as used by dense tensor store.
    virtual size_t getReservedElements(uint32_t bufferId) const;
    // Initialize reserved elements at start of buffer.
    virtual void initializeReservedElements(void *buffer, size_t reservedElements) = 0;
    virtual size_t elementSize() const = 0;
    virtual void cleanHold(void *buffer, uint64_t offset, uint64_t len, CleanContext cleanCtx) = 0;
    size_t getArraySize() const { return _arraySize; }
    void flushLastUsed();
    virtual void onActive(uint32_t bufferId, size_t *usedElems, size_t &deadElems, void *buffer);
    void onHold(const size_t *usedElems);
    virtual void onFree(size_t usedElems);

    /**
     * Calculate number of arrays to allocate for new buffer given how many elements are needed.
     */
    virtual size_t calcArraysToAlloc(uint32_t bufferId, size_t elementsNeeded, bool resizing) const;

    void clampMaxArrays(uint32_t maxArrays);

    uint32_t getActiveBuffers() const { return _activeBuffers; }
    size_t getMaxArrays() const { return _maxArrays; }
    uint32_t getNumArraysForNewBuffer() const { return _numArraysForNewBuffer; }
};

/**
 * Concrete class used to manage allocation and de-allocation of elements of type EntryType in data store buffers.
 */
template <typename EntryType>
class BufferType : public BufferTypeBase
{
protected:
    EntryType _emptyEntry;

public:
    BufferType(const BufferType &rhs) = delete;
    BufferType & operator=(const BufferType &rhs) = delete;
    BufferType(uint32_t arraySize, uint32_t minArrays, uint32_t maxArrays);
    BufferType(uint32_t arraySize, uint32_t minArrays, uint32_t maxArrays,
               uint32_t numArraysForNewBuffer, float allocGrowFactor);
    ~BufferType();
    void destroyElements(void *buffer, size_t numElements) override;
    void fallbackCopy(void *newBuffer, const void *oldBuffer, size_t numElements) override;
    void initializeReservedElements(void *buffer, size_t reservedElements) override;
    void cleanHold(void *buffer, uint64_t offset, uint64_t len, CleanContext cleanCxt) override;
    size_t elementSize() const override { return sizeof(EntryType); }
};

template <typename EntryType>
BufferType<EntryType>::BufferType(uint32_t arraySize, uint32_t minArrays, uint32_t maxArrays)
    : BufferTypeBase(arraySize, minArrays, maxArrays),
      _emptyEntry()
{ }

template <typename EntryType>
BufferType<EntryType>::BufferType(uint32_t arraySize, uint32_t minArrays, uint32_t maxArrays,
                                  uint32_t numArraysForNewBuffer, float allocGrowFactor)
    : BufferTypeBase(arraySize, minArrays, maxArrays, numArraysForNewBuffer, allocGrowFactor),
      _emptyEntry()
{ }

template <typename EntryType>
BufferType<EntryType>::~BufferType() { }

template <typename EntryType>
void
BufferType<EntryType>::destroyElements(void *buffer, size_t numElements)
{
    EntryType *e = static_cast<EntryType *>(buffer);
    for (size_t j = numElements; j != 0; --j) {
        e->~EntryType();
        ++e;
    }
}

template <typename EntryType>
void
BufferType<EntryType>::fallbackCopy(void *newBuffer,
                                    const void *oldBuffer,
                                    size_t numElements)
{
    EntryType *d = static_cast<EntryType *>(newBuffer);
    const EntryType *s = static_cast<const EntryType *>(oldBuffer);
    for (size_t j = numElements; j != 0; --j) {
        new (static_cast<void *>(d)) EntryType(*s);
        ++s;
        ++d;
    }
}

template <typename EntryType>
void
BufferType<EntryType>::initializeReservedElements(void *buffer, size_t reservedElems)
{
    EntryType *e = static_cast<EntryType *>(buffer);
    for (size_t j = reservedElems; j != 0; --j) {
        new (static_cast<void *>(e)) EntryType(_emptyEntry);
        ++e;
    }
}

template <typename EntryType>
void
BufferType<EntryType>::cleanHold(void *buffer, uint64_t offset, uint64_t len, CleanContext)
{
    EntryType *e = static_cast<EntryType *>(buffer) + offset;
    for (size_t j = len; j != 0; --j) {
        *e = _emptyEntry;
        ++e;
    }
}

}
