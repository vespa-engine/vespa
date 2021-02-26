// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "buffer_type.h"
#include "entryref.h"
#include <vespa/vespalib/util/generationhandler.h>
#include <vespa/vespalib/util/alloc.h>
#include <vespa/vespalib/util/array.h>

namespace vespalib::datastore {

/**
 * Represents a memory allocated buffer (used in a data store) with its state.
 *
 * This class has no direct knowledge of what kind of data is stored in the buffer.
 * It uses a type handler (BufferTypeBase) to manage allocation and de-allocation of a specific data type.
 *
 * A newly allocated buffer starts in state FREE where no memory is allocated.
 * It then transitions to state ACTIVE via onActive(), where memory is allocated based on calculation from BufferTypeBase.
 * It then transitions to state HOLD via onHold() when the buffer is no longer needed.
 * It is kept in this state until all reader threads are no longer accessing the buffer.
 * Finally, it transitions back to FREE via onFree() and memory is de-allocated.
 *
 * This class also supports use of free lists, where previously allocated elements in the buffer can be re-used.
 * First the element is put on hold, then on the free list (counted as dead) to be re-used.
 */
class BufferState
{
public:
    using Alloc = vespalib::alloc::Alloc;

    class FreeListList
    {
    public:
        BufferState *_head;

        FreeListList() : _head(nullptr) { }
        ~FreeListList();
    };

    using FreeList = vespalib::Array<EntryRef>;

    enum State : uint8_t {
        FREE,
        ACTIVE,
        HOLD
    };

private:
    ElemCount     _usedElems;
    ElemCount     _allocElems;
    ElemCount     _deadElems;
    ElemCount     _holdElems;
    // Number of bytes that are heap allocated by elements that are stored in this buffer.
    // For simple types this is 0.
    size_t        _extraUsedBytes;
    // Number of bytes that are heap allocated by elements that are stored in this buffer and is now on hold.
    // For simple types this is 0.
    size_t        _extraHoldBytes;
    FreeList      _freeList;
    FreeListList *_freeListList;    // non-nullptr if free lists are enabled

    // nullptr if not on circular list of buffer states with free elems
    BufferState    *_nextHasFree;
    BufferState    *_prevHasFree;

    BufferTypeBase *_typeHandler;
    Alloc           _buffer;
    uint32_t        _arraySize;
    uint16_t        _typeId;
    State           _state : 8;
    bool            _disableElemHoldList : 1;
    bool            _compacting : 1;

public:
    /**
     * TODO: Check if per-buffer free lists are useful, or if
     * compaction should always be used to free up whole buffers.
     */

    BufferState();
    BufferState(const BufferState &) = delete;
    BufferState & operator=(const BufferState &) = delete;
    ~BufferState();

    /**
     * Transition from FREE to ACTIVE state.
     *
     * @param bufferId       Id of buffer to be active.
     * @param typeId         Registered data type id for buffer.
     * @param typeHandler    Type handler for registered data type.
     * @param elementsNeeded Number of elements needed to be free in the memory allocated.
     * @param buffer         Start of allocated buffer return value.
     */
    void onActive(uint32_t bufferId, uint32_t typeId, BufferTypeBase *typeHandler,
                  size_t elementsNeeded, void *&buffer);

    /**
     * Transition from ACTIVE to HOLD state.
     */
    void onHold();

    /**
     * Transition from HOLD to FREE state.
     */
    void onFree(void *&buffer);

    /**
     * Set list of buffer states with nonempty free lists.
     *
     * @param freeListList  List of buffer states. If nullptr then free lists are disabled.
     */
    void setFreeListList(FreeListList *freeListList);

    void disableFreeList() { setFreeListList(nullptr); }

    /**
     * Add buffer state to list of buffer states with nonempty free lists.
     */
    void addToFreeListList();

    /**
     * Remove buffer state from list of buffer states with nonempty free lists.
     */
    void removeFromFreeListList();

    /**
     * Disable hold of elements, just mark then as dead without cleanup.
     * Typically used when tearing down data structure in a controlled manner.
     */
    void disableElemHoldList();

    /**
     * Pop element from free list.
     */
    EntryRef popFreeList() {
        EntryRef ret = _freeList.back();
        _freeList.pop_back();
        if (isFreeListEmpty()) {
            removeFromFreeListList();
        }
        _deadElems -= _arraySize;
        return ret;
    }

    size_t size() const { return _usedElems; }
    size_t capacity() const { return _allocElems; }
    size_t remaining() const { return _allocElems - _usedElems; }
    void pushed_back(size_t numElems) {
        pushed_back(numElems, 0);
    }
    void pushed_back(size_t numElems, size_t extraBytes) {
        _usedElems += numElems;
        _extraUsedBytes += extraBytes;
    }
    void cleanHold(void *buffer, size_t offset, ElemCount numElems) {
        _typeHandler->cleanHold(buffer, offset, numElems, BufferTypeBase::CleanContext(_extraUsedBytes, _extraHoldBytes));
    }
    void dropBuffer(void *&buffer);
    uint32_t getTypeId() const { return _typeId; }
    uint32_t getArraySize() const { return _arraySize; }
    size_t getDeadElems() const { return _deadElems; }
    size_t getHoldElems() const { return _holdElems; }
    size_t getExtraUsedBytes() const { return _extraUsedBytes; }
    size_t getExtraHoldBytes() const { return _extraHoldBytes; }
    bool getCompacting() const { return _compacting; }
    void setCompacting() { _compacting = true; }
    void fallbackResize(uint32_t bufferId, size_t elementsNeeded, void *&buffer, Alloc &holdBuffer);

    bool isActive(uint32_t typeId) const {
        return ((_state == ACTIVE) && (_typeId == typeId));
    }
    bool isActive() const { return (_state == ACTIVE); }
    bool isOnHold() const { return (_state == HOLD); }
    bool isFree() const { return (_state == FREE); }
    State getState() const { return _state; }
    const BufferTypeBase *getTypeHandler() const { return _typeHandler; }
    BufferTypeBase *getTypeHandler() { return _typeHandler; }

    void incDeadElems(size_t value) { _deadElems += value; }
    void incHoldElems(size_t value) { _holdElems += value; }
    void decHoldElems(size_t value);
    void incExtraUsedBytes(size_t value) { _extraUsedBytes += value; }
    void incExtraHoldBytes(size_t value) {
        _extraHoldBytes += value;
    }

    bool hasDisabledElemHoldList() const { return _disableElemHoldList; }
    bool isFreeListEmpty() const { return _freeList.empty();}
    FreeList &freeList() { return _freeList; }
    const FreeListList *freeListList() const { return _freeListList; }
    FreeListList *freeListList() { return _freeListList; }

};

}
