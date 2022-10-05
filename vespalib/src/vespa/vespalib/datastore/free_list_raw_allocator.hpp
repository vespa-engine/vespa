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
FreeListRawAllocator<EntryT, RefT>::alloc(size_t numElems)
{
    auto& free_list = _store.getFreeList(_typeId);
    if (free_list.empty()) {
        return ParentType::alloc(numElems);
    }
    assert(free_list.array_size() == numElems);
    RefT ref = free_list.pop_entry();
    // We must scale the offset according to array size as it was divided when the entry ref was created.
    EntryT *entry = _store.template getEntryArray<EntryT>(ref, numElems);
    return HandleType(ref, entry);
}

}

