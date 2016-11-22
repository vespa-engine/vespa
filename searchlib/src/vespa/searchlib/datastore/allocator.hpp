// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "allocator.h"
#include "bufferstate.h"

namespace search {
namespace datastore {

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
    uint32_t activeBufferId = _store.getActiveBufferId(_typeId);
    BufferState &state = _store.getBufferState(activeBufferId);
    assert(state.isActive());
    size_t oldSize = state.size();
    EntryT *entry = _store.getBufferEntry<EntryT>(activeBufferId, oldSize);
    new (static_cast<void *>(entry)) EntryT(std::forward<Args>(args)...);
    state.pushed_back(1);
    return HandleType(RefT(oldSize, activeBufferId), entry);
}

}
}
