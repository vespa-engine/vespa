// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "buffer_type.h"
#include "memory_stats.h"
#include <atomic>

namespace vespalib::datastore {

/**
 * Represents statistics for a given buffer in a data store.
 */
class BufferStats {
protected:
    // The number of elements that are allocated in the buffer.
    std::atomic<ElemCount> _alloc_elems;
    // The number of elements (of the allocated) that are used: _used_elems <= _alloc_elems.
    std::atomic<ElemCount> _used_elems;
    // The number of elements (of the used) that are on hold: _hold_elems <= _used_elems.
    // "On hold" is a transitionary state used when removing elements.
    std::atomic<ElemCount> _hold_elems;
    // The number of elements (of the used) that are dead: _dead_elems <= _used_elems.
    // A dead element was first on hold, and is now available for reuse in the free list (if enabled).
    std::atomic<ElemCount> _dead_elems;

    // Number of bytes that are heap allocated (and used) by elements that are stored in this buffer.
    // For simple types this is always 0.
    std::atomic<size_t> _extra_used_bytes;
    // Number of bytes that are heap allocated (and used) by elements that are stored in this buffer and is now on hold.
    // For simple types this is always 0.
    std::atomic<size_t> _extra_hold_bytes;

public:
    BufferStats();

    size_t size() const { return _used_elems.load(std::memory_order_relaxed); }
    size_t capacity() const { return _alloc_elems.load(std::memory_order_relaxed); }
    size_t remaining() const { return capacity() - size(); }

    void pushed_back(size_t num_elems) {
        _used_elems.store(size() + num_elems, std::memory_order_relaxed);
    }

    size_t dead_elems() const { return _dead_elems.load(std::memory_order_relaxed); }
    size_t hold_elems() const { return _hold_elems.load(std::memory_order_relaxed); }
    size_t extra_used_bytes() const { return _extra_used_bytes.load(std::memory_order_relaxed); }
    size_t extra_hold_bytes() const { return _extra_hold_bytes.load(std::memory_order_relaxed); }

    void inc_extra_used_bytes(size_t value) { _extra_used_bytes.store(extra_used_bytes() + value, std::memory_order_relaxed); }

    void add_to_mem_stats(size_t element_size, MemoryStats& stats) const;
};

/**
 * Provides low-level access to buffer stats for integration in BufferState.
 */
class InternalBufferStats : public BufferStats {
public:
    InternalBufferStats();
    void clear();
    void set_alloc_elems(size_t value) { _alloc_elems.store(value, std::memory_order_relaxed); }
    void set_dead_elems(size_t value) { _dead_elems.store(value, std::memory_order_relaxed); }
    void set_hold_elems(size_t value) { _hold_elems.store(value, std::memory_order_relaxed); }
    void inc_dead_elems(size_t value) { _dead_elems.store(dead_elems() + value, std::memory_order_relaxed); }
    void inc_hold_elems(size_t value) { _hold_elems.store(hold_elems() + value, std::memory_order_relaxed); }
    void dec_hold_elems(size_t value);
    void inc_extra_hold_bytes(size_t value) { _extra_hold_bytes.store(extra_hold_bytes() + value, std::memory_order_relaxed); }
    std::atomic<ElemCount>& used_elems_ref() { return _used_elems; }
    std::atomic<ElemCount>& dead_elems_ref() { return _dead_elems; }
    std::atomic<size_t>& extra_used_bytes_ref() { return _extra_used_bytes; }
    std::atomic<size_t>& extra_hold_bytes_ref() { return _extra_hold_bytes; }
};

}

