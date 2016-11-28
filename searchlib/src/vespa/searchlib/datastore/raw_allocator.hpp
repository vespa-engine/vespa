// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "raw_allocator.h"
#include "bufferstate.h"

namespace search {
namespace datastore {

template <typename RefT>
RawAllocator<RefT>::RawAllocator(DataStoreBase &store, uint32_t typeId)
    : _store(store),
      _typeId(typeId)
{
}

template <typename RefT>
typename RawAllocator<RefT>::HandleType
RawAllocator<RefT>::alloc(size_t numBytes)
{
    _store.ensureBufferCapacity(_typeId, numBytes);
    uint32_t activeBufferId = _store.getActiveBufferId(_typeId);
    BufferState &state = _store.getBufferState(activeBufferId);
    assert(state.isActive());
    size_t oldBufferSize = state.size();
    char *buffer = _store.getBufferEntry<char>(activeBufferId, oldBufferSize);
    state.pushed_back(numBytes);
    return HandleType(RefT(oldBufferSize, activeBufferId), buffer);
}

}
}
