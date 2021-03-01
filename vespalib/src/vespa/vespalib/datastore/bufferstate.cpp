// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bufferstate.h"
#include <vespa/vespalib/util/memory_allocator.h>
#include <limits>
#include <cassert>

using vespalib::alloc::Alloc;
using vespalib::alloc::MemoryAllocator;

namespace vespalib::datastore {

BufferState::FreeListList::~FreeListList()
{
    assert(_head == nullptr);  // Owner should have disabled free lists
}

BufferState::BufferState()
    : _usedElems(0),
      _allocElems(0),
      _deadElems(0u),
      _holdElems(0u),
      _extraUsedBytes(0),
      _extraHoldBytes(0),
      _freeList(),
      _freeListList(nullptr),
      _nextHasFree(nullptr),
      _prevHasFree(nullptr),
      _typeHandler(nullptr),
      _buffer(Alloc::alloc(0, MemoryAllocator::HUGEPAGE_SIZE)),
      _arraySize(0),
      _typeId(0),
      _state(FREE),
      _disableElemHoldList(false),
      _compacting(false)
{
}

BufferState::~BufferState()
{
    assert(_state == FREE);
    assert(_freeListList == nullptr);
    assert(_nextHasFree == nullptr);
    assert(_prevHasFree == nullptr);
    assert(_holdElems == 0);
    assert(isFreeListEmpty());
}

void
BufferState::decHoldElems(size_t value) {
    assert(_holdElems >= value);
    _holdElems -= value;
}

namespace {

struct AllocResult {
    size_t elements;
    size_t bytes;
    AllocResult(size_t elements_, size_t bytes_) : elements(elements_), bytes(bytes_) {}
};

size_t
roundUpToMatchAllocator(size_t sz)
{
    if (sz == 0) {
        return 0;
    }
    // We round up the wanted number of bytes to allocate to match
    // the underlying allocator to ensure little to no waste of allocated memory.
    if (sz < MemoryAllocator::HUGEPAGE_SIZE) {
        // Match heap allocator in vespamalloc.
        return vespalib::roundUp2inN(sz);
    } else {
        // Match mmap allocator.
        return MemoryAllocator::roundUpToHugePages(sz);
    }
}

AllocResult
calcAllocation(uint32_t bufferId,
               BufferTypeBase &typeHandler,
               size_t elementsNeeded,
               bool resizing)
{
    size_t allocArrays = typeHandler.calcArraysToAlloc(bufferId, elementsNeeded, resizing);
    size_t allocElements = allocArrays * typeHandler.getArraySize();
    size_t allocBytes = roundUpToMatchAllocator(allocElements * typeHandler.elementSize());
    size_t maxAllocBytes = typeHandler.getMaxArrays() * typeHandler.getArraySize() * typeHandler.elementSize();
    if (allocBytes > maxAllocBytes) {
        // Ensure that allocated bytes does not exceed the maximum handled by this type.
        allocBytes = maxAllocBytes;
    }
    size_t adjustedAllocElements = (allocBytes / typeHandler.elementSize());
    return AllocResult(adjustedAllocElements, allocBytes);
}

}

void
BufferState::onActive(uint32_t bufferId, uint32_t typeId,
                      BufferTypeBase *typeHandler,
                      size_t elementsNeeded,
                      void *&buffer)
{
    assert(buffer == nullptr);
    assert(_buffer.get() == nullptr);
    assert(_state == FREE);
    assert(_typeHandler == nullptr);
    assert(_allocElems == 0);
    assert(_usedElems == 0);
    assert(_deadElems == 0u);
    assert(_holdElems == 0);
    assert(_extraUsedBytes == 0);
    assert(_extraHoldBytes == 0);
    assert(isFreeListEmpty());
    assert(_nextHasFree == nullptr);
    assert(_prevHasFree == nullptr);
    assert(_freeListList == nullptr || _freeListList->_head != this);

    size_t reservedElements = typeHandler->getReservedElements(bufferId);
    (void) reservedElements;
    AllocResult alloc = calcAllocation(bufferId, *typeHandler, elementsNeeded, false);
    assert(alloc.elements >= reservedElements + elementsNeeded);
    auto allocator = typeHandler->get_memory_allocator();
    _buffer = (allocator != nullptr) ? Alloc::alloc_with_allocator(allocator) : Alloc::alloc(0, MemoryAllocator::HUGEPAGE_SIZE);
    _buffer.create(alloc.bytes).swap(_buffer);
    buffer = _buffer.get();
    assert(buffer != nullptr || alloc.elements == 0u);
    _allocElems = alloc.elements;
    _state = ACTIVE;
    _typeHandler = typeHandler;
    assert(typeId <= std::numeric_limits<uint16_t>::max());
    _typeId = typeId;
    _arraySize = _typeHandler->getArraySize();
    typeHandler->onActive(bufferId, &_usedElems, &_deadElems, buffer);
}


void
BufferState::onHold()
{
    assert(_state == ACTIVE);
    assert(_typeHandler != nullptr);
    _state = HOLD;
    _compacting = false;
    assert(_deadElems <= _usedElems);
    assert(_holdElems <= (_usedElems - _deadElems));
    _deadElems = 0;
    _holdElems = _usedElems; // Put everyting on hold
    _typeHandler->onHold(&_usedElems, &_deadElems);
    if ( ! isFreeListEmpty()) {
        removeFromFreeListList();
        FreeList().swap(_freeList);
    }
    assert(_nextHasFree == nullptr);
    assert(_prevHasFree == nullptr);
    assert(_freeListList == nullptr || _freeListList->_head != this);
    setFreeListList(nullptr);
}


void
BufferState::onFree(void *&buffer)
{
    assert(buffer == _buffer.get());
    assert(_state == HOLD);
    assert(_typeHandler != nullptr);
    assert(_deadElems <= _usedElems);
    assert(_holdElems == _usedElems - _deadElems);
    _typeHandler->destroyElements(buffer, _usedElems);
    Alloc::alloc().swap(_buffer);
    _typeHandler->onFree(_usedElems);
    buffer = nullptr;
    _usedElems = 0;
    _allocElems = 0;
    _deadElems = 0u;
    _holdElems = 0u;
    _extraUsedBytes = 0;
    _extraHoldBytes = 0;
    _state = FREE;
    _typeHandler = nullptr;
    _arraySize = 0;
    assert(isFreeListEmpty());
    assert(_nextHasFree == nullptr);
    assert(_prevHasFree == nullptr);
    assert(_freeListList == nullptr || _freeListList->_head != this);
    setFreeListList(nullptr);
    _disableElemHoldList = false;
}


void
BufferState::dropBuffer(void *&buffer)
{
    if (_state == FREE) {
        assert(buffer == nullptr);
        return;
    }
    assert(buffer != nullptr || _allocElems == 0);
    if (_state == ACTIVE) {
        onHold();
    }
    if (_state == HOLD) {
        onFree(buffer);
    }
    assert(_state == FREE);
    assert(buffer == nullptr);
}


void
BufferState::setFreeListList(FreeListList *freeListList)
{
    if (_state == FREE && freeListList != nullptr) {
        return;
    }
    if (freeListList == _freeListList) {
        return; // No change
    }
    if (_freeListList != nullptr && ! isFreeListEmpty()) {
        removeFromFreeListList(); // Remove from old free list
    }
    _freeListList = freeListList;
    if ( ! isFreeListEmpty() ) {
        if (freeListList != nullptr) {
            addToFreeListList(); // Changed free list list
        } else {
            FreeList().swap(_freeList);; // Free lists have been disabled
        }
    }
}


void
BufferState::addToFreeListList()
{
    assert(_freeListList != nullptr && _freeListList->_head != this);
    assert(_nextHasFree == nullptr);
    assert(_prevHasFree == nullptr);
    if (_freeListList->_head != nullptr) {
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
    assert(_freeListList != nullptr);
    assert(_nextHasFree != nullptr);
    assert(_prevHasFree != nullptr);
    if (_nextHasFree == this) {
        assert(_prevHasFree == this);
        assert(_freeListList->_head == this);
        _freeListList->_head = nullptr;
    } else {
        assert(_prevHasFree != this);
        _freeListList->_head = _nextHasFree;
        _nextHasFree->_prevHasFree = _prevHasFree;
        _prevHasFree->_nextHasFree = _nextHasFree;
    }
    _nextHasFree = nullptr;
    _prevHasFree = nullptr;
}


void
BufferState::disableElemHoldList()
{
    _disableElemHoldList = true;
}


void
BufferState::fallbackResize(uint32_t bufferId,
                            size_t elementsNeeded,
                            void *&buffer,
                            Alloc &holdBuffer)
{
    assert(_state == ACTIVE);
    assert(_typeHandler != nullptr);
    assert(holdBuffer.get() == nullptr);
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

