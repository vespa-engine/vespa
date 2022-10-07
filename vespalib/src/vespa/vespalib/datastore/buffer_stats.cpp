// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "buffer_stats.h"
#include <cassert>

namespace vespalib::datastore {

BufferStats::BufferStats()
    : _alloc_elems(0),
      _used_elems(0),
      _hold_elems(0),
      _dead_elems(0),
      _extra_used_bytes(0),
      _extra_hold_bytes(0)
{
}

void
BufferStats::add_to_mem_stats(size_t element_size, MemoryStats& stats) const
{
    size_t extra_used = extra_used_bytes();
    stats._allocElems += capacity();
    stats._usedElems += size();
    stats._deadElems += dead_elems();
    stats._holdElems += hold_elems();
    stats._allocBytes += (capacity() * element_size) + extra_used;
    stats._usedBytes += (size() * element_size) + extra_used;
    stats._deadBytes += dead_elems() * element_size;
    stats._holdBytes += (hold_elems() * element_size) + extra_hold_bytes();
}

InternalBufferStats::InternalBufferStats()
    : BufferStats()
{
}

void
InternalBufferStats::clear()
{
    _alloc_elems.store(0, std::memory_order_relaxed);
    _used_elems.store(0, std::memory_order_relaxed);
    _hold_elems.store(0, std::memory_order_relaxed);
    _dead_elems.store(0, std::memory_order_relaxed);
    _extra_used_bytes.store(0, std::memory_order_relaxed);
    _extra_hold_bytes.store(0, std::memory_order_relaxed);
}

void
InternalBufferStats::dec_hold_elems(size_t value)
{
    ElemCount elems = hold_elems();
    assert(elems >= value);
    _hold_elems.store(elems - value, std::memory_order_relaxed);
}

}

