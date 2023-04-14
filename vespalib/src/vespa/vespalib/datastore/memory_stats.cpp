// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memory_stats.h"

namespace vespalib::datastore {

MemoryStats::MemoryStats()
    : _alloc_entries(0),
      _used_entries(0),
      _dead_entries(0),
      _hold_entries(0),
      _allocBytes(0),
      _usedBytes(0),
      _deadBytes(0),
      _holdBytes(0),
      _freeBuffers(0),
      _activeBuffers(0),
      _holdBuffers(0)
{
}

MemoryStats&
MemoryStats::operator+=(const MemoryStats& rhs)
{
    _alloc_entries += rhs._alloc_entries;
    _used_entries += rhs._used_entries;
    _dead_entries += rhs._dead_entries;
    _hold_entries += rhs._hold_entries;
    _allocBytes += rhs._allocBytes;
    _usedBytes += rhs._usedBytes;
    _deadBytes += rhs._deadBytes;
    _holdBytes += rhs._holdBytes;
    _freeBuffers += rhs._freeBuffers;
    _activeBuffers += rhs._activeBuffers;
    _holdBuffers += rhs._holdBuffers;
    return *this;
}

}

