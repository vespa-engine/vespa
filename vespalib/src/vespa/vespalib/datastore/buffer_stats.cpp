// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "buffer_stats.h"
#include <cassert>

namespace vespalib::datastore {

BufferStats::BufferStats()
    : _alloc_entries(0),
      _used_entries(0),
      _hold_entries(0),
      _dead_entries(0),
      _extra_used_bytes(0),
      _extra_hold_bytes(0)
{
}

void
BufferStats::add_to_mem_stats(size_t entry_size, MemoryStats& stats) const
{
    size_t extra_used = extra_used_bytes();
    stats._alloc_entries += capacity();
    stats._used_entries += size();
    stats._dead_entries += dead_entries();
    stats._hold_entries += hold_entries();
    stats._allocBytes += (capacity() * entry_size) + extra_used;
    stats._usedBytes += (size() * entry_size) + extra_used;
    stats._deadBytes += dead_entries() * entry_size;
    stats._holdBytes += (hold_entries() * entry_size) + extra_hold_bytes();
}

InternalBufferStats::InternalBufferStats()
    : BufferStats()
{
}

void
InternalBufferStats::clear()
{
    _alloc_entries.store(0, std::memory_order_relaxed);
    _used_entries.store(0, std::memory_order_relaxed);
    _hold_entries.store(0, std::memory_order_relaxed);
    _dead_entries.store(0, std::memory_order_relaxed);
    _extra_used_bytes.store(0, std::memory_order_relaxed);
    _extra_hold_bytes.store(0, std::memory_order_relaxed);
}

void
InternalBufferStats::dec_hold_entries(size_t value)
{
    EntryCount elems = hold_entries();
    assert(elems >= value);
    _hold_entries.store(elems - value, std::memory_order_relaxed);
}

}

