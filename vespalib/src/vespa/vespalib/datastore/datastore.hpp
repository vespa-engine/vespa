// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "allocator.hpp"
#include "datastore.h"
#include "free_list_allocator.hpp"
#include "free_list_raw_allocator.hpp"
#include "raw_allocator.hpp"
#include <vespa/vespalib/util/array.hpp>

namespace vespalib::datastore {

template <typename RefT>
DataStoreT<RefT>::DataStoreT()
    : DataStoreBase(RefType::numBuffers(),
                    RefType::unscaled_offset_size())
{
}

template <typename RefT>
DataStoreT<RefT>::~DataStoreT() = default;

template <typename RefT>
void
DataStoreT<RefT>::freeElem(EntryRef ref, size_t numElems)
{
    RefType intRef(ref);
    BufferState &state = getBufferState(intRef.bufferId());
    if (state.isActive()) {
        if (state.freeListList() != nullptr && numElems == state.getArraySize()) {
            if (state.isFreeListEmpty()) {
                state.addToFreeListList();
            }
            state.freeList().push_back(ref);
        }
    } else {
        assert(state.isOnHold());
    }
    state.incDeadElems(numElems);
    state.cleanHold(getBuffer(intRef.bufferId()),
                    intRef.unscaled_offset() * state.getArraySize(), numElems);
}

template <typename RefT>
void
DataStoreT<RefT>::holdElem(EntryRef ref, size_t numElems, size_t extraBytes)
{
    RefType intRef(ref);
    size_t alignedLen = RefType::align(numElems);
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

template <typename RefT>
template <typename EntryT>
FreeListRawAllocator<EntryT, RefT>
DataStoreT<RefT>::freeListRawAllocator(uint32_t typeId)
{
    return FreeListRawAllocator<EntryT, RefT>(*this, typeId);
}

template <typename EntryType, typename RefT>
DataStore<EntryType, RefT>::DataStore()
    : DataStore(std::make_unique<BufferType<EntryType>>(1, RefType::offsetSize(), RefType::offsetSize()))
{
}

template <typename EntryType, typename RefT>
DataStore<EntryType, RefT>::DataStore(uint32_t min_arrays)
    : DataStore(std::make_unique<BufferType<EntryType>>(1, min_arrays, RefType::offsetSize()))
{
}

template <typename EntryType, typename RefT>
DataStore<EntryType, RefT>::DataStore(BufferTypeUP type)
    : ParentType(),
      _type(std::move(type))
{
    addType(_type.get());
    init_primary_buffers();
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
    using NoOpReclaimer = DefaultReclaimer<EntryType>;
    // Note: This will fallback to regular allocation if free lists are not enabled.
    return FreeListAllocator<EntryType, RefT, NoOpReclaimer>(*this, 0).alloc(e).ref;
}

template <typename EntryType, typename RefT>
const EntryType &
DataStore<EntryType, RefT>::getEntry(EntryRef ref) const
{
    RefType intRef(ref);
    const EntryType *be = this->template getEntry<EntryType>(intRef);
    return *be;
}

extern template class DataStoreT<EntryRefT<22> >;

}

