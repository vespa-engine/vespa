// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "raw_allocator.h"
#include "bufferstate.h"

namespace search {
namespace datastore {

template <typename EntryT, typename RefT>
RawAllocator<EntryT, RefT>::RawAllocator(DataStoreBase &store, uint32_t typeId)
    : _store(store),
      _typeId(typeId)
{
}

template <typename EntryT, typename RefT>
typename RawAllocator<EntryT, RefT>::HandleType
RawAllocator<EntryT, RefT>::alloc(size_t numElems, size_t extraElems)
{
    _store.ensureBufferCapacity(_typeId, numElems + extraElems);
    uint32_t activeBufferId = _store.getActiveBufferId(_typeId);
    BufferState &state = _store.getBufferState(activeBufferId);
    assert(state.isActive());
    size_t oldBufferSize = state.size();
    EntryT *buffer = _store.getBufferEntry<EntryT>(activeBufferId, oldBufferSize);
    state.pushed_back(numElems);
    return HandleType(RefT(oldBufferSize, activeBufferId), buffer);
}

}
}
