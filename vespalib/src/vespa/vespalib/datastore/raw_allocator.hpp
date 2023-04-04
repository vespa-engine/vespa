// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "raw_allocator.h"
#include "bufferstate.h"

namespace vespalib::datastore {

template <typename EntryT, typename RefT>
RawAllocator<EntryT, RefT>::RawAllocator(DataStoreBase &store, uint32_t typeId)
    : _store(store),
      _typeId(typeId)
{
}

template <typename EntryT, typename RefT>
typename RawAllocator<EntryT, RefT>::HandleType
RawAllocator<EntryT, RefT>::alloc(size_t num_entries, size_t extra_entries)
{
    size_t arraySize = _store.getBufferState(_store.primary_buffer_id(_typeId)).getArraySize();
    _store.ensureBufferCapacity(_typeId, (num_entries + extra_entries) * arraySize);
    uint32_t buffer_id = _store.primary_buffer_id(_typeId);
    BufferState &state = _store.getBufferState(buffer_id);
    assert(state.isActive());
    size_t oldBufferSize = state.size();
    // Must perform scaling ourselves, according to array size
    RefT ref((oldBufferSize / arraySize), buffer_id);
    EntryT *buffer = _store.getEntryArray<EntryT>(ref, arraySize);
    state.stats().pushed_back(num_entries * arraySize);
    return HandleType(ref, buffer);
}

}

