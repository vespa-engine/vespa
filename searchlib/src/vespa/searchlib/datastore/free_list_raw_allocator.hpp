// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "free_list_raw_allocator.h"

namespace search::datastore {

template <typename EntryT, typename RefT>
FreeListRawAllocator<EntryT, RefT>::FreeListRawAllocator(DataStoreBase &store, uint32_t typeId)
    : ParentType(store, typeId)
{
}

template <typename EntryT, typename RefT>
typename FreeListRawAllocator<EntryT, RefT>::HandleType
FreeListRawAllocator<EntryT, RefT>::alloc(size_t numElems)
{
    BufferState::FreeListList &freeListList = _store.getFreeList(_typeId);
    if (freeListList._head == nullptr) {
        return ParentType::alloc(numElems);
    }
    BufferState &state = *freeListList._head;
    assert(state.isActive());
    assert(state.getArraySize() == numElems);
    RefT ref = state.popFreeList();
    // If entry ref is not aligned we must scale the offset according to array size as it was divided when the entry ref was created.
    EntryT *entry = !RefT::isAlignedType ?
                    _store.template getEntryArray<EntryT>(ref, state.getArraySize()) :
                    _store.template getEntry<EntryT>(ref);
    return HandleType(ref, entry);
}

}

