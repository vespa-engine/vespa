// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bufferstate.h"
#include <limits>

using vespalib::alloc::Alloc;

namespace search::datastore {

BufferState::FreeListList::~FreeListList()
{
    assert(_head == NULL);  // Owner should have disabled free lists
}


BufferState::BufferState()
    : _usedElems(0),
      _allocElems(0),
      _deadElems(0u),
      _state(FREE),
      _disableElemHoldList(false),
      _holdElems(0u),
      _extraUsedBytes(0),
      _extraHoldBytes(0),
      _freeList(),
      _freeListList(NULL),
      _nextHasFree(NULL),
      _prevHasFree(NULL),
      _typeHandler(NULL),
      _typeId(0),
      _clusterSize(0),
      _compacting(false),
      _buffer(Alloc::alloc())
{
}


BufferState::~BufferState()
{
    assert(_state == FREE);
    assert(_freeListList == NULL);
    assert(_nextHasFree == NULL);
    assert(_prevHasFree == NULL);
    assert(_holdElems == 0);
    assert(_freeList.empty());
}

namespace {

struct AllocResult {
    size_t elements;
    size_t bytes;
    AllocResult(size_t elements_, size_t bytes_) : elements(elements_), bytes(bytes_) {}
};

AllocResult
calcAllocation(uint32_t bufferId,
               BufferTypeBase &typeHandler,
               size_t elementsNeeded,
               bool resizing)
{
    size_t allocClusters = typeHandler.calcClustersToAlloc(bufferId, elementsNeeded, resizing);
    size_t allocElements = allocClusters * typeHandler.getClusterSize();
    size_t allocBytes = allocElements * typeHandler.elementSize();
    return AllocResult(allocElements, allocBytes);
}

}

void
BufferState::onActive(uint32_t bufferId, uint32_t typeId,
                      BufferTypeBase *typeHandler,
                      size_t elementsNeeded,
                      void *&buffer)
{
    assert(buffer == NULL);
    assert(_buffer.get() == NULL);
    assert(_state == FREE);
    assert(_typeHandler == NULL);
    assert(_allocElems == 0);
    assert(_usedElems == 0);
    assert(_deadElems == 0u);
    assert(_holdElems == 0);
    assert(_extraUsedBytes == 0);
    assert(_extraHoldBytes == 0);
    assert(_freeList.empty());
    assert(_nextHasFree == NULL);
    assert(_prevHasFree == NULL);
    assert(_freeListList == NULL || _freeListList->_head != this);

    size_t reservedElements = typeHandler->getReservedElements(bufferId);
    (void) reservedElements;
    AllocResult alloc = calcAllocation(bufferId, *typeHandler, elementsNeeded, false);
    assert(alloc.elements >= reservedElements + elementsNeeded);
    _buffer.create(alloc.bytes).swap(_buffer);
    buffer = _buffer.get();
    assert(buffer != NULL || alloc.elements == 0u);
    _allocElems = alloc.elements;
    _state = ACTIVE;
    _typeHandler = typeHandler;
    _typeId = typeId;
    _clusterSize = _typeHandler->getClusterSize();
    typeHandler->onActive(bufferId, &_usedElems, _deadElems, buffer);
}


void
BufferState::onHold()
{
    assert(_state == ACTIVE);
    assert(_typeHandler != NULL);
    _state = HOLD;
    _compacting = false;
    assert(_deadElems <= _usedElems);
    assert(_holdElems <= (_usedElems - _deadElems));
    _holdElems = _usedElems - _deadElems; // Put everyting not dead on hold
    _typeHandler->onHold(&_usedElems);
    if (!_freeList.empty()) {
        removeFromFreeListList();
        FreeList().swap(_freeList);
    }
    assert(_nextHasFree == NULL);
    assert(_prevHasFree == NULL);
    assert(_freeListList == NULL || _freeListList->_head != this);
    setFreeListList(NULL);
}


void
BufferState::onFree(void *&buffer)
{
    assert(buffer == _buffer.get());
    assert(_state == HOLD);
    assert(_typeHandler != NULL);
    assert(_deadElems <= _usedElems);
    assert(_holdElems == _usedElems - _deadElems);
    _typeHandler->destroyElements(buffer, _usedElems);
    Alloc::alloc().swap(_buffer);
    _typeHandler->onFree(_usedElems);
    buffer = NULL;
    _usedElems = 0;
    _allocElems = 0;
    _deadElems = 0u;
    _holdElems = 0u;
    _extraUsedBytes = 0;
    _extraHoldBytes = 0;
    _state = FREE;
    _typeHandler = NULL;
    _clusterSize = 0;
    assert(_freeList.empty());
    assert(_nextHasFree == NULL);
    assert(_prevHasFree == NULL);
    assert(_freeListList == NULL || _freeListList->_head != this);
    setFreeListList(NULL);
    _disableElemHoldList = false;
}


void
BufferState::dropBuffer(void *&buffer)
{
    if (_state == FREE) {
        assert(buffer == NULL);
        return;
    }
    assert(buffer != NULL || _allocElems == 0);
    if (_state == ACTIVE) {
        onHold();
    }
    if (_state == HOLD) {
        onFree(buffer);
    }
    assert(_state == FREE);
    assert(buffer == NULL);
}


void
BufferState::setFreeListList(FreeListList *freeListList)
{
    if (_state == FREE && freeListList != NULL) {
        return;
    }
    if (freeListList == _freeListList) {
        return; // No change
    }
    if (_freeListList != NULL && !_freeList.empty()) {
        removeFromFreeListList(); // Remove from old free list
    }
    _freeListList = freeListList;
    if (!_freeList.empty()) {
        if (freeListList != NULL) {
            addToFreeListList(); // Changed free list list
        } else {
            FreeList().swap(_freeList); // Free lists have been disabled
        }
    }
}


void
BufferState::addToFreeListList()
{
    assert(_freeListList != NULL && _freeListList->_head != this);
    assert(_nextHasFree == NULL);
    assert(_prevHasFree == NULL);
    if (_freeListList->_head != NULL) {
        _nextHasFree = _freeListList->_head;
        _prevHasFree = _nextHasFree->_prevHasFree;
        _nextHasFree->_prevHasFree = this;
        _prevHasFree->_nextHasFree = this;
    } else {
        _nextHasFree = this;
        _prevHasFree = this;
    }
    _freeListList->_head = this;
}


void
BufferState::removeFromFreeListList()
{
    assert(_freeListList != NULL);
    assert(_nextHasFree != NULL);
    assert(_prevHasFree != NULL);
    if (_nextHasFree == this) {
        assert(_prevHasFree == this);
        assert(_freeListList->_head == this);
        _freeListList->_head = NULL;
    } else {
        assert(_prevHasFree != this);
        _freeListList->_head = _nextHasFree;
        _nextHasFree->_prevHasFree = _prevHasFree;
        _prevHasFree->_nextHasFree = _nextHasFree;
    }
    _nextHasFree = NULL;
    _prevHasFree = NULL;
}


void
BufferState::disableElemHoldList()
{
    _disableElemHoldList = true;
}


void
BufferState::fallbackResize(uint32_t bufferId,
                            uint64_t elementsNeeded,
                            void *&buffer,
                            Alloc &holdBuffer)
{
    assert(_state == ACTIVE);
    assert(_typeHandler != NULL);
    assert(holdBuffer.get() == NULL);
    AllocResult alloc = calcAllocation(bufferId, *_typeHandler, elementsNeeded, true);
    assert(alloc.elements >= _usedElems + elementsNeeded);
    assert(alloc.elements > _allocElems);
    Alloc newBuffer = _buffer.create(alloc.bytes);
    _typeHandler->fallbackCopy(newBuffer.get(), buffer, _usedElems);
    holdBuffer.swap(_buffer);
    std::atomic_thread_fence(std::memory_order_release);
    _buffer = std::move(newBuffer);
    buffer = _buffer.get();
    _allocElems = alloc.elements;
    std::atomic_thread_fence(std::memory_order_release);
}

}

