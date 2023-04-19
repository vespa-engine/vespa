// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "free_list_raw_allocator.h"

namespace vespalib::datastore {

template <typename EntryT, typename RefT>
FreeListRawAllocator<EntryT, RefT>::FreeListRawAllocator(DataStoreBase &store, uint32_t typeId)
    : ParentType(store, typeId)
{
}

template <typename EntryT, typename RefT>
typename FreeListRawAllocator<EntryT, RefT>::HandleType
FreeListRawAllocator<EntryT, RefT>::alloc(size_t num_entries)
{
    auto& free_list = _store.getFreeList(_typeId);
    if (free_list.empty()) {
        return ParentType::alloc(num_entries);
    }
    assert(num_entries == 1);
    RefT ref = free_list.pop_entry();
    auto& state = _store.getBufferState(ref.bufferId());
    auto array_size = state.getArraySize();
    // We must scale the offset according to array size as it was divided when the entry ref was created.
    EntryT *entry = _store.template getEntryArray<EntryT>(ref, array_size);
    return HandleType(ref, entry);
}

}

