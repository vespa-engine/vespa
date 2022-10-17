// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bufferstate.h"
#include <vespa/vespalib/util/memory_allocator.h>
#include <limits>
#include <cassert>

using vespalib::alloc::Alloc;
using vespalib::alloc::MemoryAllocator;

namespace vespalib::datastore {

BufferState::BufferState()
    : _stats(),
      _free_list(_stats.dead_elems_ref()),
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
    assert(!_free_list.enabled());
    assert(_free_list.empty());
    assert(_stats.hold_elems() == 0);
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
    assert(_stats.dead_elems() == 0u);
    assert(_stats.hold_elems() == 0);
    assert(_stats.extra_used_bytes() == 0);
    assert(_stats.extra_hold_bytes() == 0);
    assert(_free_list.empty());

    size_t reservedElements = typeHandler->getReservedElements(bufferId);
    (void) reservedElements;
    AllocResult alloc = calcAllocation(bufferId, *typeHandler, elementsNeeded, false);
    assert(alloc.elements >= reservedElements + elementsNeeded);
    auto allocator = typeHandler->get_memory_allocator();
    _buffer = (allocator != nullptr) ? Alloc::alloc_with_allocator(allocator) : Alloc::alloc(0, MemoryAllocator::HUGEPAGE_SIZE);
    _buffer.create(alloc.bytes).swap(_buffer);
    assert(_buffer.get() != nullptr || alloc.elements == 0u);
    buffer.store(_buffer.get(), std::memory_order_release);
    _stats.set_alloc_elems(alloc.elements);
    _typeHandler.store(typeHandler, std::memory_order_release);
    assert(typeId <= std::numeric_limits<uint16_t>::max());
    _typeId = typeId;
    _arraySize = typeHandler->getArraySize();
    _free_list.set_array_size(_arraySize);
    _state.store(State::ACTIVE, std::memory_order_release);
    typeHandler->onActive(bufferId, &_stats.used_elems_ref(), &_stats.dead_elems_ref(),
                          buffer.load(std::memory_order::relaxed));
}

void
BufferState::onHold(uint32_t buffer_id)
{
    assert(getState() == State::ACTIVE);
    assert(getTypeHandler() != nullptr);
    _state.store(State::HOLD, std::memory_order_release);
    _compacting = false;
    assert(_stats.dead_elems() <= size());
    assert(_stats.hold_elems() <= (size() - _stats.dead_elems()));
    _stats.set_dead_elems(0);
    _stats.set_hold_elems(size());
    getTypeHandler()->onHold(buffer_id, &_stats.used_elems_ref(), &_stats.dead_elems_ref());
    _free_list.disable();
}

void
BufferState::onFree(std::atomic<void*>& buffer)
{
    assert(buffer.load(std::memory_order_relaxed) == _buffer.get());
    assert(getState() == State::HOLD);
    assert(_typeHandler != nullptr);
    assert(_stats.dead_elems() <= size());
    assert(_stats.hold_elems() == (size() - _stats.dead_elems()));
    getTypeHandler()->destroyElements(buffer, size());
    Alloc::alloc().swap(_buffer);
    getTypeHandler()->onFree(size());
    buffer.store(nullptr, std::memory_order_release);
    _stats.clear();
    _state.store(State::FREE, std::memory_order_release);
    _typeHandler = nullptr;
    _arraySize = 0;
    _free_list.set_array_size(_arraySize);
    assert(!_free_list.enabled());
    assert(_free_list.empty());
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
BufferState::disableElemHoldList()
{
    _disableElemHoldList = true;
}

bool
BufferState::hold_elems(size_t num_elems, size_t extra_bytes)
{
    assert(isActive());
    if (_disableElemHoldList) {
        // The elements are directly marked as dead as they are not put on hold.
        _stats.inc_dead_elems(num_elems);
        return true;
    }
    _stats.inc_hold_elems(num_elems);
    _stats.inc_extra_hold_bytes(extra_bytes);
    return false;
}

void
BufferState::free_elems(EntryRef ref, size_t num_elems, size_t ref_offset)
{
    if (isActive()) {
        if (_free_list.enabled() && (num_elems == getArraySize())) {
            _free_list.push_entry(ref);
        }
    } else {
        assert(isOnHold());
    }
    _stats.inc_dead_elems(num_elems);
    _stats.dec_hold_elems(num_elems);
    getTypeHandler()->cleanHold(_buffer.get(), (ref_offset * _arraySize), num_elems,
                                BufferTypeBase::CleanContext(_stats.extra_used_bytes_ref(),
                                                             _stats.extra_hold_bytes_ref()));
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
    _stats.set_alloc_elems(alloc.elements);
}

void
BufferState::resume_primary_buffer(uint32_t buffer_id)
{
    getTypeHandler()->resume_primary_buffer(buffer_id, &_stats.used_elems_ref(), &_stats.dead_elems_ref());
}

}

