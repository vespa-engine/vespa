// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "buffer_type.h"
#include "entryref.h"
#include <vespa/vespalib/util/generationhandler.h>
#include <vespa/vespalib/util/alloc.h>
#include <vespa/vespalib/util/array.h>

namespace search::datastore {

/**
 * Represents a memory allocated buffer (used in a data store) with its state.
 */
class BufferState
{
public:
    typedef vespalib::alloc::Alloc Alloc;

    class FreeListList
    {
    public:
        BufferState *_head;

        FreeListList() : _head(NULL) { }
        ~FreeListList();
    };

    typedef vespalib::Array<EntryRef> FreeList;

    enum State {
        FREE,
        ACTIVE,
        HOLD
    };

private:
    size_t        _usedElems;
    size_t        _allocElems;
    uint64_t      _deadElems;
    State         _state;
    bool          _disableElemHoldList;
    uint64_t      _holdElems;
    // Number of bytes that are heap allocated by elements that are stored in this buffer.
    // For simple types this is 0.
    size_t        _extraUsedBytes;
    // Number of bytes that are heap allocated by elements that are stored in this buffer and is now on hold.
    // For simple types this is 0.
    size_t        _extraHoldBytes;
    FreeList      _freeList;
    FreeListList *_freeListList;    // non-NULL if free lists are enabled

    // NULL pointers if not on circular list of buffer states with free elems
    BufferState    *_nextHasFree;
    BufferState    *_prevHasFree;

    BufferTypeBase *_typeHandler;
    uint32_t        _typeId;
    uint32_t        _clusterSize;
    bool            _compacting;
    Alloc           _buffer;

public:
    /*
     * TODO: Check if per-buffer free lists are useful, or if
     *compaction should always be used to free up whole buffers.
     */

    BufferState();
    ~BufferState();

    /**
     * Transition from FREE to ACTIVE state.
     *
     * @param bufferId       Id of buffer to be active.
     * @param typeId         registered data type for buffer.
     * @param typeHandler    type handler for registered data type.
     * @param elementsNeeded Number of elements needed to be free
     * @param buffer         start of buffer.
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
     * @param freeListList  List of buffer states.  If NULL then free lists
     *              are disabled.
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
     * Disable hold of elements, just mark then as dead without
     * cleanup.  Typically used when tearing down data structure in a
     * controlled manner.
     */
    void disableElemHoldList();

    /**
     * Pop element from free list.
     */
    EntryRef popFreeList() {
        EntryRef ret = _freeList.back();
        _freeList.pop_back();
        if (_freeList.empty()) {
            removeFromFreeListList();
        }
        _deadElems -= _clusterSize;
        return ret;
    }

    size_t size() const { return _usedElems; }
    size_t capacity() const { return _allocElems; }
    size_t remaining() const { return _allocElems - _usedElems; }
    void pushed_back(uint64_t numElems, size_t extraBytes = 0) {
        _usedElems += numElems;
        _extraUsedBytes += extraBytes;
    }
    void cleanHold(void *buffer, uint64_t offset, uint64_t len) {
        _typeHandler->cleanHold(buffer, offset, len, BufferTypeBase::CleanContext(_extraHoldBytes));
    }
    void dropBuffer(void *&buffer);
    uint32_t getTypeId() const { return _typeId; }
    uint32_t getClusterSize() const { return _clusterSize; }
    uint64_t getDeadElems() const { return _deadElems; }
    uint64_t getHoldElems() const { return _holdElems; }
    size_t getExtraUsedBytes() const { return _extraUsedBytes; }
    size_t getExtraHoldBytes() const { return _extraHoldBytes; }
    bool getCompacting() const { return _compacting; }
    void setCompacting() { _compacting = true; }
    void fallbackResize(uint32_t bufferId, uint64_t elementsNeeded, void *&buffer, Alloc &holdBuffer);

    bool isActive(uint32_t typeId) const {
        return ((_state == ACTIVE) && (_typeId == typeId));
    }
    bool isActive() const { return (_state == ACTIVE); }
    bool isOnHold() const { return (_state == HOLD); }
    bool isFree() const { return (_state == FREE); }
    State getState() const { return _state; }
    const BufferTypeBase *getTypeHandler() const { return _typeHandler; }
    BufferTypeBase *getTypeHandler() { return _typeHandler; }

    void incDeadElems(uint64_t value) { _deadElems += value; }
    void incHoldElems(uint64_t value) { _holdElems += value; }
    void decHoldElems(uint64_t value) {
        assert(_holdElems >= value);
        _holdElems -= value;
    }
    void incExtraHoldBytes(size_t value) {
        _extraHoldBytes += value;
    }

    bool hasDisabledElemHoldList() const { return _disableElemHoldList; }
    const FreeList &freeList() const { return _freeList; }
    FreeList &freeList() { return _freeList; }
    const FreeListList *freeListList() const { return _freeListList; }
    FreeListList *freeListList() { return _freeListList; }

};

}
