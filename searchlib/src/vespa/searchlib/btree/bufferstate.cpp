// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bufferstate.h"
#include <limits>

namespace search
{

namespace btree
{


BufferTypeBase::BufferTypeBase(uint32_t clusterSize,
                               uint32_t minClusters,
                               uint32_t maxClusters)
    : _clusterSize(clusterSize),
      _minClusters(std::min(minClusters, maxClusters)),
      _maxClusters(maxClusters),
      _activeBuffers(0),
      _holdBuffers(0),
      _activeUsedElems(0),
      _holdUsedElems(0),
      _lastUsedElems(NULL)
{
}


BufferTypeBase::~BufferTypeBase(void)
{
    assert(_activeBuffers == 0);
    assert(_holdBuffers == 0);
    assert(_activeUsedElems == 0);
    assert(_holdUsedElems == 0);
    assert(_lastUsedElems == NULL);
}


void
BufferTypeBase::flushLastUsed(void)
{
    if (_lastUsedElems != NULL) {
        _activeUsedElems += *_lastUsedElems;
        _lastUsedElems = NULL;
    }
}


void
BufferTypeBase::onActive(const size_t *usedElems)
{
    flushLastUsed();
    ++_activeBuffers;
    _lastUsedElems = usedElems;
}


void
BufferTypeBase::onHold(const size_t *usedElems)
{
    if (usedElems == _lastUsedElems)
        flushLastUsed();
    --_activeBuffers;
    ++_holdBuffers;
    assert(_activeUsedElems >= *usedElems);
    _activeUsedElems -= *usedElems;
    _holdUsedElems += *usedElems;
}


void
BufferTypeBase::onFree(size_t usedElems)
{
    --_holdBuffers;
    assert(_holdUsedElems >= usedElems);
    _holdUsedElems -= usedElems;
}


size_t
BufferTypeBase::calcClustersToAlloc(size_t sizeNeeded,
                                    uint64_t clusterRefSize) const
{
    size_t usedElems = _activeUsedElems;
    if (_lastUsedElems != NULL)
        usedElems += *_lastUsedElems;
    assert((usedElems % _clusterSize) == 0);
    uint64_t maxClusters = std::numeric_limits<size_t>::max() / _clusterSize;
    uint64_t maxClusters2 = clusterRefSize;
    if (maxClusters > maxClusters2)
        maxClusters = maxClusters2;
    if (maxClusters > _maxClusters)
        maxClusters = _maxClusters;
    uint32_t minClusters = _minClusters;
    if (minClusters > maxClusters)
        minClusters = maxClusters;
    size_t usedClusters = usedElems / _clusterSize;
    size_t needClusters = (sizeNeeded + _clusterSize - 1) / _clusterSize;
    uint64_t wantClusters = usedClusters + minClusters;
    if (wantClusters < needClusters)
        wantClusters = needClusters;
    if (wantClusters > maxClusters)
        wantClusters = maxClusters;
    return wantClusters;
}


BufferState::FreeListList::~FreeListList(void)
{
    assert(_head == NULL);	// Owner should have disabled free lists
}


BufferState::BufferState(void)
    : _usedElems(0),
      _allocElems(0),
      _deadElems(0u),
      _state(FREE),
      _disableElemHoldList(false),
      _holdElems(0u),
      _freeList(),
      _freeListList(NULL),
      _nextHasFree(NULL),
      _prevHasFree(NULL),
      _typeHandler(NULL),
      _typeId(0),
      _clusterSize(0),
      _compacting(false),
      _buffer()
{
      _buffer.reset(new Alloc());
}


BufferState::~BufferState(void)
{
    assert(_state == FREE);
    assert(_freeListList == NULL);
    assert(_nextHasFree == NULL);
    assert(_prevHasFree == NULL);
    assert(_holdElems == 0);
    assert(_freeList.empty());
}


void
BufferState::onActive(uint32_t bufferId, uint32_t typeId,
                      BufferTypeBase *typeHandler,
                      size_t sizeNeeded,
                      size_t maxClusters,
                      void *&buffer)
{
    assert(buffer == NULL);
    assert(_buffer->get() == NULL);
    assert(_state == FREE);
    assert(_typeHandler == NULL);
    assert(_allocElems == 0);
    assert(_usedElems == 0);
    assert(_deadElems == 0u);
    assert(_holdElems == 0);
    assert(_freeList.empty());
    assert(_nextHasFree == NULL);
    assert(_prevHasFree == NULL);
    assert(_freeListList == NULL || _freeListList->_head != this);

    size_t initialSizeNeeded = 0;
    if (bufferId == 0)
        initialSizeNeeded = typeHandler->getClusterSize();
    size_t allocClusters =
        typeHandler->calcClustersToAlloc(initialSizeNeeded + sizeNeeded,
                maxClusters);
    size_t allocSize = allocClusters * typeHandler->getClusterSize();
    assert(allocSize >= initialSizeNeeded + sizeNeeded);
    _buffer.reset(new Alloc(allocSize * typeHandler->elementSize()));
    buffer = _buffer->get();
    typeHandler->onActive(&_usedElems);
    assert(buffer != NULL);
    _allocElems = allocSize;
    _state = ACTIVE;
    _typeHandler = typeHandler;
    _typeId = typeId;
    _clusterSize = _typeHandler->getClusterSize();
    if (bufferId == 0) {
        typeHandler->cleanInitialElements(buffer);
        pushed_back(_clusterSize);
        _deadElems = _clusterSize;
    }
}


void
BufferState::onHold(void)
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
    assert(buffer == _buffer->get());
    assert(_state == HOLD);
    assert(_typeHandler != NULL);
    assert(_deadElems <= _usedElems);
    assert(_holdElems == _usedElems - _deadElems);
    _typeHandler->destroyElements(buffer, _usedElems);
    Alloc().swap(*_buffer);
    _typeHandler->onFree(_usedElems);
    buffer = NULL;
    _usedElems = 0;
    _allocElems = 0;
    _deadElems = 0u;
    _holdElems = 0u;
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
    assert(buffer != NULL);
    if (_state == ACTIVE)
        onHold();
    if (_state == HOLD)
        onFree(buffer);
    assert(_state == FREE);
    assert(buffer == NULL);
}


void
BufferState::setFreeListList(FreeListList *freeListList)
{
    if (_state == FREE && freeListList != NULL)
        return;
    if (freeListList == _freeListList)
        return;				// No change
    if (_freeListList != NULL && !_freeList.empty())
        removeFromFreeListList();	// Remove from old free list	
    _freeListList = freeListList;
    if (!_freeList.empty()) {
        if (freeListList != NULL)
            addToFreeListList();	// Changed free list list
        else
            FreeList().swap(_freeList);	// Free lists have been disabled
    }
}


void
BufferState::addToFreeListList(void)
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
BufferState::removeFromFreeListList(void)
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
BufferState::disableElemHoldList(void)
{
    _disableElemHoldList = true;
}


void
BufferState::fallbackResize(uint64_t newSize,
                            size_t maxClusters,
                            void *&buffer,
                            Alloc &holdBuffer)
{
    assert(_state == ACTIVE);
    assert(_typeHandler != NULL);
    assert(holdBuffer.get() == NULL);
    size_t allocClusters = _typeHandler->calcClustersToAlloc(newSize,
            maxClusters);
    size_t allocSize = allocClusters * _typeHandler->getClusterSize();
    assert(allocSize >= newSize);
    assert(allocSize > _allocElems);
    Alloc::UP newBuffer(std::make_unique<Alloc>
                        (allocSize * _typeHandler->elementSize()));
    _typeHandler->fallbackCopy(newBuffer->get(), buffer, _usedElems);
    holdBuffer.swap(*_buffer);
    std::atomic_thread_fence(std::memory_order_release);
    _buffer = std::move(newBuffer);
    buffer = _buffer->get();
    _allocElems = allocSize;
    std::atomic_thread_fence(std::memory_order_release);
}

} // namespace btree

} // namespace search

