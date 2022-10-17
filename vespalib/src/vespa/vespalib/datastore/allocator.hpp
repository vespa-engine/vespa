// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "allocator.h"
#include "bufferstate.h"
#include <cassert>

namespace vespalib::datastore {

template <typename EntryT, typename RefT>
Allocator<EntryT, RefT>::Allocator(DataStoreBase &store, uint32_t typeId)
    : _store(store),
      _typeId(typeId)
{
}

template <typename EntryT, typename RefT>
template <typename ... Args>
typename Allocator<EntryT, RefT>::HandleType
Allocator<EntryT, RefT>::alloc(Args && ... args)
{
    _store.ensureBufferCapacity(_typeId, 1);
    uint32_t buffer_id = _store.get_primary_buffer_id(_typeId);
    BufferState &state = _store.getBufferState(buffer_id);
    assert(state.isActive());
    size_t oldBufferSize = state.size();
    RefT ref(oldBufferSize, buffer_id);
    EntryT *entry = _store.getEntry<EntryT>(ref);
    new (static_cast<void *>(entry)) EntryT(std::forward<Args>(args)...);
    state.stats().pushed_back(1);
    return HandleType(ref, entry);
}

template <typename EntryT, typename RefT>
typename Allocator<EntryT, RefT>::HandleType
Allocator<EntryT, RefT>::allocArray(ConstArrayRef array)
{
    _store.ensureBufferCapacity(_typeId, array.size());
    uint32_t buffer_id = _store.get_primary_buffer_id(_typeId);
    BufferState &state = _store.getBufferState(buffer_id);
    assert(state.isActive());
    assert(state.getArraySize() == array.size());
    size_t oldBufferSize = state.size();
    assert((oldBufferSize % array.size()) == 0);
    RefT ref((oldBufferSize / array.size()), buffer_id);
    EntryT *buf = _store.template getEntryArray<EntryT>(ref, array.size());
    for (size_t i = 0; i < array.size(); ++i) {
        new (static_cast<void *>(buf + i)) EntryT(array[i]);
    }
    state.stats().pushed_back(array.size());
    return HandleType(ref, buf);
}

template <typename EntryT, typename RefT>
typename Allocator<EntryT, RefT>::HandleType
Allocator<EntryT, RefT>::allocArray(size_t size)
{
    _store.ensureBufferCapacity(_typeId, size);
    uint32_t buffer_id = _store.get_primary_buffer_id(_typeId);
    BufferState &state = _store.getBufferState(buffer_id);
    assert(state.isActive());
    assert(state.getArraySize() == size);
    size_t oldBufferSize = state.size();
    assert((oldBufferSize % size) == 0);
    RefT ref((oldBufferSize / size), buffer_id);
    EntryT *buf = _store.template getEntryArray<EntryT>(ref, size);
    for (size_t i = 0; i < size; ++i) {
        new (static_cast<void *>(buf + i)) EntryT();
    }
    state.stats().pushed_back(size);
    return HandleType(ref, buf);
}

}

