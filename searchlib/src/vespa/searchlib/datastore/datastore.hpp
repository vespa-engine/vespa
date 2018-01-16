// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "datastore.h"
#include "allocator.hpp"
#include "free_list_allocator.hpp"
#include "raw_allocator.hpp"
#include <vespa/vespalib/util/array.hpp>

namespace search::datastore {

template <typename RefT>
DataStoreT<RefT>::DataStoreT()
    : DataStoreBase(RefType::numBuffers(),
                    RefType::offsetSize() / RefType::align(1))
{
}


template <typename RefT>
DataStoreT<RefT>::~DataStoreT()
{
}


template <typename RefT>
void
DataStoreT<RefT>::freeElem(EntryRef ref, uint64_t len)
{
    RefType intRef(ref);
    BufferState &state = getBufferState(intRef.bufferId());
    if (state.isActive()) {
        if (state.freeListList() != NULL && len == state.getClusterSize()) {
            if (state.freeList().empty()) {
                state.addToFreeListList();
            }
            state.freeList().push_back(ref);
        }
    } else {
        assert(state.isOnHold());
    }
    state.incDeadElems(len);
    state.cleanHold(getBuffer(intRef.bufferId()),
                    (intRef.offset() / RefType::align(1)) *
                    state.getClusterSize(), len);
}


template <typename RefT>
void
DataStoreT<RefT>::holdElem(EntryRef ref, uint64_t len, size_t extraBytes)
{
    RefType intRef(ref);
    uint64_t alignedLen = RefType::align(len);
    BufferState &state = getBufferState(intRef.bufferId());
    assert(state.isActive());
    if (state.hasDisabledElemHoldList()) {
        state.incDeadElems(alignedLen);
        return;
    }
    _elemHold1List.push_back(ElemHold1ListElem(ref, alignedLen));
    state.incHoldElems(alignedLen);
    state.incExtraHoldBytes(extraBytes);
}


template <typename RefT>
void
DataStoreT<RefT>::trimElemHoldList(generation_t usedGen)
{
    ElemHold2List &elemHold2List = _elemHold2List;

    ElemHold2List::iterator it(elemHold2List.begin());
    ElemHold2List::iterator ite(elemHold2List.end());
    uint32_t freed = 0;
    for (; it != ite; ++it) {
        if (static_cast<sgeneration_t>(it->_generation - usedGen) >= 0)
            break;
        RefType intRef(it->_ref);
        BufferState &state = getBufferState(intRef.bufferId());
        freeElem(it->_ref, it->_len);
        state.decHoldElems(it->_len);
        ++freed;
    }
    if (freed != 0) {
        elemHold2List.erase(elemHold2List.begin(), it);
    }
}


template <typename RefT>
void
DataStoreT<RefT>::clearElemHoldList()
{
    ElemHold2List &elemHold2List = _elemHold2List;

    ElemHold2List::iterator it(elemHold2List.begin());
    ElemHold2List::iterator ite(elemHold2List.end());
    for (; it != ite; ++it) {
        RefType intRef(it->_ref);
        BufferState &state = getBufferState(intRef.bufferId());
        freeElem(it->_ref, it->_len);
        state.decHoldElems(it->_len);
    }
    elemHold2List.clear();
}

template <typename RefT>
template <typename EntryT>
Allocator<EntryT, RefT>
DataStoreT<RefT>::allocator(uint32_t typeId)
{
    return Allocator<EntryT, RefT>(*this, typeId);
}

template <typename RefT>
template <typename EntryT, typename ReclaimerT>
FreeListAllocator<EntryT, RefT, ReclaimerT>
DataStoreT<RefT>::freeListAllocator(uint32_t typeId)
{
    return FreeListAllocator<EntryT, RefT, ReclaimerT>(*this, typeId);
}

template <typename RefT>
template <typename EntryT>
RawAllocator<EntryT, RefT>
DataStoreT<RefT>::rawAllocator(uint32_t typeId)
{
    return RawAllocator<EntryT, RefT>(*this, typeId);
}



template <typename EntryType, typename RefT>
DataStore<EntryType, RefT>::DataStore()
    : ParentType(),
      _type(1, RefType::offsetSize(), RefType::offsetSize())
{
    addType(&_type);
    initActiveBuffers();
}

template <typename EntryType, typename RefT>
DataStore<EntryType, RefT>::~DataStore()
{
    dropBuffers();  // Drop buffers before type handlers are dropped
}

template <typename EntryType, typename RefT>
EntryRef
DataStore<EntryType, RefT>::addEntry(const EntryType &e)
{
    ensureBufferCapacity(0, 1);
    uint32_t activeBufferId = _activeBufferIds[0];
    BufferState &state = this->getBufferState(activeBufferId);
    size_t oldSize = state.size();
    EntryType *be = static_cast<EntryType *>(this->getBuffer(activeBufferId)) + oldSize;
    new (static_cast<void *>(be)) EntryType(e);
    RefType ref(oldSize, activeBufferId);
    state.pushed_back(1);
    return ref;
}

template <typename EntryType, typename RefT>
const EntryType &
DataStore<EntryType, RefT>::getEntry(EntryRef ref) const
{
    RefType intRef(ref);
    const EntryType *be =
        this->template
        getBufferEntry<EntryType>(intRef.bufferId(), intRef.offset());
    return *be;
}

template <typename EntryType, typename RefT>
template <typename ReclaimerT>
FreeListAllocator<EntryType, RefT, ReclaimerT>
DataStore<EntryType, RefT>::freeListAllocator()
{
    return FreeListAllocator<EntryType, RefT, ReclaimerT>(*this, 0);
}

extern template class DataStoreT<EntryRefT<22> >;

}

