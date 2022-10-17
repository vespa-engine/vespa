// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memory_stats.h"

namespace vespalib::datastore {

MemoryStats::MemoryStats()
    : _allocElems(0),
      _usedElems(0),
      _deadElems(0),
      _holdElems(0),
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
    _allocElems += rhs._allocElems;
    _usedElems += rhs._usedElems;
    _deadElems += rhs._deadElems;
    _holdElems += rhs._holdElems;
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

