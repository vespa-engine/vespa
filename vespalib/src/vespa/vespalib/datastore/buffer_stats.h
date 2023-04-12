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
    // The number of entries that are allocated in the buffer.
    std::atomic<EntryCount> _alloc_entries;
    // The number of entries (of the allocated) that are used: _used_entries <= _alloc_entries.
    std::atomic<EntryCount> _used_entries;
    // The number of entries (of the used) that are on hold: _hold_entries <= _used_entries.
    // "On hold" is a transitionary state used when removing entries.
    std::atomic<EntryCount> _hold_entries;
    // The number of entries (of the used) that are dead: _dead_entries <= _used_entries.
    // A dead entry was first on hold, and is now available for reuse in the free list (if enabled).
    std::atomic<EntryCount> _dead_entries;

    // Number of bytes that are heap allocated (and used) by elements that are stored in this buffer.
    // For simple types this is always 0.
    std::atomic<size_t> _extra_used_bytes;
    // Number of bytes that are heap allocated (and used) by elements that are stored in this buffer and is now on hold.
    // For simple types this is always 0.
    std::atomic<size_t> _extra_hold_bytes;

public:
    BufferStats();

    size_t size() const { return _used_entries.load(std::memory_order_relaxed); }
    size_t capacity() const { return _alloc_entries.load(std::memory_order_relaxed); }
    size_t remaining() const { return capacity() - size(); }

    void pushed_back(size_t num_entries) {
        _used_entries.store(size() + num_entries, std::memory_order_relaxed);
    }

    size_t dead_entries() const { return _dead_entries.load(std::memory_order_relaxed); }
    size_t hold_entries() const { return _hold_entries.load(std::memory_order_relaxed); }
    size_t extra_used_bytes() const { return _extra_used_bytes.load(std::memory_order_relaxed); }
    size_t extra_hold_bytes() const { return _extra_hold_bytes.load(std::memory_order_relaxed); }

    void inc_extra_used_bytes(size_t value) { _extra_used_bytes.store(extra_used_bytes() + value, std::memory_order_relaxed); }

    void add_to_mem_stats(size_t entry_size, MemoryStats& stats) const;
};

/**
 * Provides low-level access to buffer stats for integration in BufferState.
 */
class InternalBufferStats : public BufferStats {
public:
    InternalBufferStats();
    void clear();
    void set_alloc_entries(size_t value) { _alloc_entries.store(value, std::memory_order_relaxed); }
    void set_dead_entries(size_t value) { _dead_entries.store(value, std::memory_order_relaxed); }
    void set_hold_entries(size_t value) { _hold_entries.store(value, std::memory_order_relaxed); }
    void inc_dead_entries(size_t value) { _dead_entries.store(dead_entries() + value, std::memory_order_relaxed); }
    void inc_hold_entries(size_t value) { _hold_entries.store(hold_entries() + value, std::memory_order_relaxed); }
    void dec_hold_entries(size_t value);
    void inc_extra_hold_bytes(size_t value) { _extra_hold_bytes.store(extra_hold_bytes() + value, std::memory_order_relaxed); }
    std::atomic<EntryCount>& used_entries_ref() { return _used_entries; }
    std::atomic<EntryCount>& dead_entries_ref() { return _dead_entries; }
    std::atomic<size_t>& extra_used_bytes_ref() { return _extra_used_bytes; }
    std::atomic<size_t>& extra_hold_bytes_ref() { return _extra_hold_bytes; }
};

}

