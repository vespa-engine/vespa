// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "datastore.h"
#include "allocator.hpp"
#include "free_list_allocator.hpp"
#include "free_list_raw_allocator.hpp"
#include "raw_allocator.hpp"
#include <vespa/vespalib/util/generation_hold_list.hpp>

namespace vespalib::datastore {

template <typename RefT>
DataStoreT<RefT>::DataStoreT()
    : DataStoreBase(RefType::numBuffers(), RefType::offset_bits, RefType::offsetSize())
{
}

template <typename RefT>
DataStoreT<RefT>::~DataStoreT() = default;

template <typename RefT>
void
DataStoreT<RefT>::free_elem_internal(EntryRef ref, size_t numElems)
{
    RefType intRef(ref);
    BufferState &state = getBufferState(intRef.bufferId());
    state.free_elems(ref, numElems, intRef.offset());
}

template <typename RefT>
void
DataStoreT<RefT>::holdElem(EntryRef ref, size_t numElems, size_t extraBytes)
{
    RefType intRef(ref);
    BufferState &state = getBufferState(intRef.bufferId());
    if (!state.hold_elems(numElems, extraBytes)) {
        _entry_ref_hold_list.insert({ref, numElems});
    }
}

template <typename RefT>
void
DataStoreT<RefT>::reclaim_entry_refs(generation_t oldest_used_gen)
{
    _entry_ref_hold_list.reclaim(oldest_used_gen, [this](const auto& elem) {
        free_elem_internal(elem.ref, elem.num_elems);
    });
}

template <typename RefT>
void
DataStoreT<RefT>::reclaim_all_entry_refs()
{
    _entry_ref_hold_list.reclaim_all([this](const auto& elem) {
        free_elem_internal(elem.ref, elem.num_elems);
    });
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

extern template class DataStoreT<EntryRefT<22> >;

}

