// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bufferstate.h"

#include <vespa/check_require.h>
#include <vespa/vespalib/util/memory_allocator.h>

#include <limits>

using vespalib::alloc::Alloc;
using vespalib::alloc::MemoryAllocator;

namespace vespalib::datastore {

BufferState::BufferState() noexcept
    : _stats(),
      _free_list(_stats.dead_entries_ref()),
      _typeHandler(nullptr),
      _buffer(Alloc::alloc(0, MemoryAllocator::HUGEPAGE_SIZE)),
      _arraySize(0),
      _typeId(0),
      _state(State::FREE),
      _disable_entry_hold_list(false),
      _compacting(false) {
}

BufferState::~BufferState() {
    CHECK(getState() == State::FREE);
    CHECK(!_free_list.enabled());
    CHECK(_free_list.empty());
    CHECK(_stats.hold_entries() == 0);
}

namespace {

struct AllocResult {
    size_t entries;
    size_t bytes;
    AllocResult(size_t entries_, size_t bytes_) noexcept : entries(entries_), bytes(bytes_) {}
};

size_t roundUpToMatchAllocator(size_t sz) noexcept {
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

AllocResult calc_allocation(uint32_t bufferId, BufferTypeBase& typeHandler, size_t free_entries_needed,
                            bool resizing) {
    size_t alloc_entries = typeHandler.calc_entries_to_alloc(bufferId, free_entries_needed, resizing);
    size_t entry_size = typeHandler.entry_size();
    auto   buffer_underflow_size = typeHandler.buffer_underflow_size();
    size_t allocBytes = roundUpToMatchAllocator(alloc_entries * entry_size + buffer_underflow_size);
    size_t maxAllocBytes = typeHandler.get_max_entries() * entry_size + buffer_underflow_size;
    if (allocBytes > maxAllocBytes) {
        // Ensure that allocated bytes does not exceed the maximum handled by this type.
        allocBytes = maxAllocBytes;
    }
    size_t adjusted_alloc_entries = (allocBytes - buffer_underflow_size) / entry_size;
    return {adjusted_alloc_entries, allocBytes};
}

} // namespace

void BufferState::on_active(uint32_t bufferId, uint32_t typeId, BufferTypeBase* typeHandler,
                            size_t free_entries_needed, std::atomic<void*>& buffer) {
    CHECK(buffer.load(std::memory_order_relaxed) == nullptr);
    CHECK(_buffer.get() == nullptr);
    CHECK(getState() == State::FREE);
    CHECK(_typeHandler == nullptr);
    CHECK(capacity() == 0);
    CHECK(size() == 0);
    CHECK(_stats.dead_entries() == 0u);
    CHECK(_stats.hold_entries() == 0);
    CHECK(_stats.extra_used_bytes() == 0);
    CHECK(_stats.extra_hold_bytes() == 0);
    CHECK(_free_list.empty());

    size_t reserved_entries = typeHandler->get_reserved_entries(bufferId);
    (void)reserved_entries;
    AllocResult alloc = calc_allocation(bufferId, *typeHandler, free_entries_needed, false);
    CHECK(alloc.entries >= reserved_entries + free_entries_needed);
    auto allocator = typeHandler->get_memory_allocator();
    _buffer = (allocator != nullptr) ? Alloc::alloc_with_allocator(allocator)
                                     : Alloc::alloc(0, MemoryAllocator::HUGEPAGE_SIZE);
    _buffer.create(alloc.bytes).swap(_buffer);
    CHECK(_buffer.get() != nullptr || alloc.entries == 0u);
    auto buffer_underflow_size = typeHandler->buffer_underflow_size();
    buffer.store(get_buffer(buffer_underflow_size), std::memory_order_release);
    _stats.set_alloc_entries(alloc.entries);
    _typeHandler.store(typeHandler, std::memory_order_release);
    CHECK(typeId <= std::numeric_limits<uint16_t>::max());
    _typeId = typeId;
    _arraySize = typeHandler->getArraySize();
    _state.store(State::ACTIVE, std::memory_order_release);
    typeHandler->on_active(bufferId, &_stats.used_entries_ref(), &_stats.dead_entries_ref(),
                           buffer.load(std::memory_order::relaxed));
}

void BufferState::onHold(uint32_t buffer_id) {
    CHECK(getState() == State::ACTIVE);
    auto type_handler = getTypeHandler();
    CHECK(type_handler != nullptr);
    _state.store(State::HOLD, std::memory_order_release);
    _compacting = false;
    CHECK(_stats.dead_entries() <= size());
    CHECK(_stats.hold_entries() <= (size() - _stats.dead_entries()));
    _stats.set_dead_entries(0);
    _stats.set_hold_entries(size());
    type_handler->on_hold(buffer_id, &_stats.used_entries_ref(), &_stats.dead_entries_ref());
    _free_list.disable();
}

void BufferState::onFree(std::atomic<void*>& buffer) {
    CHECK(getState() == State::HOLD);
    auto type_handler = getTypeHandler();
    CHECK(type_handler != nullptr);
    CHECK(buffer.load(std::memory_order_relaxed) == get_buffer(type_handler->buffer_underflow_size()));
    CHECK(_stats.dead_entries() <= size());
    CHECK(_stats.hold_entries() == (size() - _stats.dead_entries()));
    type_handler->destroy_entries(buffer, size());
    Alloc::alloc().swap(_buffer);
    type_handler->on_free(size());
    buffer.store(nullptr, std::memory_order_release);
    _stats.clear();
    _state.store(State::FREE, std::memory_order_release);
    _typeHandler = nullptr;
    _arraySize = 0;
    CHECK(!_free_list.enabled());
    CHECK(_free_list.empty());
    _disable_entry_hold_list = false;
}

void BufferState::dropBuffer(uint32_t buffer_id, std::atomic<void*>& buffer) {
    if (getState() == State::FREE) {
        CHECK(buffer.load(std::memory_order_relaxed) == nullptr);
        return;
    }
    CHECK(buffer.load(std::memory_order_relaxed) != nullptr || capacity() == 0);
    if (getState() == State::ACTIVE) {
        onHold(buffer_id);
    }
    if (getState() == State::HOLD) {
        onFree(buffer);
    }
    CHECK(getState() == State::FREE);
    CHECK(buffer.load(std::memory_order_relaxed) == nullptr);
}

void BufferState::disable_entry_hold_list() {
    _disable_entry_hold_list = true;
}

bool BufferState::hold_entries(size_t num_entries, size_t extra_bytes) {
    CHECK(isActive());
    if (_disable_entry_hold_list) {
        // The elements are directly marked as dead as they are not put on hold.
        _stats.inc_dead_entries(num_entries);
        return true;
    }
    _stats.inc_hold_entries(num_entries);
    _stats.inc_extra_hold_bytes(extra_bytes);
    return false;
}

void BufferState::free_entries(EntryRef ref, size_t num_entries, size_t ref_offset) {
    if (isActive()) {
        if (_free_list.enabled() && (num_entries == 1)) {
            _free_list.push_entry(ref);
        }
    } else {
        CHECK(isOnHold());
    }
    _stats.inc_dead_entries(num_entries);
    _stats.dec_hold_entries(num_entries);
    auto type_handler = getTypeHandler();
    auto buffer_underflow_size = type_handler->buffer_underflow_size();
    type_handler->clean_hold(
        get_buffer(buffer_underflow_size), ref_offset, num_entries,
        BufferTypeBase::CleanContext(_stats.extra_used_bytes_ref(), _stats.extra_hold_bytes_ref()));
}

void BufferState::fallback_resize(uint32_t bufferId, size_t free_entries_needed, std::atomic<void*>& buffer,
                                  Alloc& holdBuffer) {
    CHECK(getState() == State::ACTIVE);
    auto type_handler = getTypeHandler();
    CHECK(type_handler != nullptr);
    CHECK(holdBuffer.get() == nullptr);
    auto        buffer_underflow_size = type_handler->buffer_underflow_size();
    AllocResult alloc = calc_allocation(bufferId, *type_handler, free_entries_needed, true);
    CHECK(alloc.entries >= size() + free_entries_needed);
    CHECK(alloc.entries > capacity());
    Alloc newBuffer = _buffer.create(alloc.bytes);
    type_handler->fallback_copy(get_buffer(newBuffer, buffer_underflow_size), buffer.load(std::memory_order_relaxed),
                                size());
    holdBuffer.swap(_buffer);
    std::atomic_thread_fence(std::memory_order_release);
    _buffer = std::move(newBuffer);
    buffer.store(get_buffer(buffer_underflow_size), std::memory_order_release);
    _stats.set_alloc_entries(alloc.entries);
}

void BufferState::resume_primary_buffer(uint32_t buffer_id) {
    getTypeHandler()->resume_primary_buffer(buffer_id, &_stats.used_entries_ref(), &_stats.dead_entries_ref());
}

} // namespace vespalib::datastore
