// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
      _state(State::FREE),
      _disableElemHoldList(false),
      _compacting(false)
{
}

BufferState::~BufferState()
{
    assert(getState() == State::FREE);
    assert(_freeListList == nullptr);
    assert(_nextHasFree == nullptr);
    assert(_prevHasFree == nullptr);
    assert(_holdElems == 0);
    assert(isFreeListEmpty());
}

void
BufferState::decHoldElems(size_t value) {
    ElemCount hold_elems = getHoldElems();
    assert(hold_elems >= value);
    _holdElems.store(hold_elems - value, std::memory_order_relaxed);
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
                      std::atomic<void*>& buffer)
{
    assert(buffer.load(std::memory_order_relaxed) == nullptr);
    assert(_buffer.get() == nullptr);
    assert(getState() == State::FREE);
    assert(_typeHandler == nullptr);
    assert(capacity() == 0);
    assert(size() == 0);
    assert(getDeadElems() == 0u);
    assert(getHoldElems() == 0);
    assert(getExtraUsedBytes() == 0);
    assert(getExtraHoldBytes() == 0);
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
    assert(_buffer.get() != nullptr || alloc.elements == 0u);
    buffer.store(_buffer.get(), std::memory_order_release);
    _allocElems.store(alloc.elements, std::memory_order_relaxed);
    _typeHandler.store(typeHandler, std::memory_order_release);
    assert(typeId <= std::numeric_limits<uint16_t>::max());
    _typeId = typeId;
    _arraySize = typeHandler->getArraySize();
    _state.store(State::ACTIVE, std::memory_order_release);
    typeHandler->onActive(bufferId, &_usedElems, &_deadElems, buffer.load(std::memory_order::relaxed));
}


void
BufferState::onHold(uint32_t buffer_id)
{
    assert(getState() == State::ACTIVE);
    assert(getTypeHandler() != nullptr);
    _state.store(State::HOLD, std::memory_order_release);
    _compacting = false;
    assert(getDeadElems() <= size());
    assert(getHoldElems() <= (size() - getDeadElems()));
    _deadElems.store(0, std::memory_order_relaxed);
    _holdElems.store(size(), std::memory_order_relaxed); // Put everyting on hold
    getTypeHandler()->onHold(buffer_id, &_usedElems, &_deadElems);
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
BufferState::onFree(std::atomic<void*>& buffer)
{
    assert(buffer.load(std::memory_order_relaxed) == _buffer.get());
    assert(getState() == State::HOLD);
    assert(_typeHandler != nullptr);
    assert(getDeadElems() <= size());
    assert(getHoldElems() == size() - getDeadElems());
    getTypeHandler()->destroyElements(buffer, size());
    Alloc::alloc().swap(_buffer);
    getTypeHandler()->onFree(size());
    buffer.store(nullptr, std::memory_order_release);
    _usedElems.store(0, std::memory_order_relaxed);
    _allocElems.store(0, std::memory_order_relaxed);
    _deadElems.store(0, std::memory_order_relaxed);
    _holdElems.store(0, std::memory_order_relaxed);
    _extraUsedBytes.store(0, std::memory_order_relaxed);
    _extraHoldBytes.store(0, std::memory_order_relaxed);
    _state.store(State::FREE, std::memory_order_release);
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
BufferState::dropBuffer(uint32_t buffer_id, std::atomic<void*>& buffer)
{
    if (getState() == State::FREE) {
        assert(buffer.load(std::memory_order_relaxed) == nullptr);
        return;
    }
    assert(buffer.load(std::memory_order_relaxed) != nullptr || capacity() == 0);
    if (getState() == State::ACTIVE) {
        onHold(buffer_id);
    }
    if (getState() == State::HOLD) {
        onFree(buffer);
    }
    assert(getState() == State::FREE);
    assert(buffer.load(std::memory_order_relaxed) == nullptr);
}


void
BufferState::setFreeListList(FreeListList *freeListList)
{
    if (getState() == State::FREE && freeListList != nullptr) {
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
                            std::atomic<void*>& buffer,
                            Alloc &holdBuffer)
{
    assert(getState() == State::ACTIVE);
    assert(_typeHandler != nullptr);
    assert(holdBuffer.get() == nullptr);
    AllocResult alloc = calcAllocation(bufferId, *_typeHandler, elementsNeeded, true);
    assert(alloc.elements >= size() + elementsNeeded);
    assert(alloc.elements > capacity());
    Alloc newBuffer = _buffer.create(alloc.bytes);
    getTypeHandler()->fallbackCopy(newBuffer.get(), buffer.load(std::memory_order_relaxed), size());
    holdBuffer.swap(_buffer);
    std::atomic_thread_fence(std::memory_order_release);
    _buffer = std::move(newBuffer);
    buffer.store(_buffer.get(), std::memory_order_release);
    _allocElems.store(alloc.elements, std::memory_order_relaxed);
}

void
BufferState::resume_primary_buffer(uint32_t buffer_id)
{
    getTypeHandler()->resume_primary_buffer(buffer_id, &_usedElems, &_deadElems);
}

}

