// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "free_list_allocator.h"
#include "bufferstate.h"

namespace vespalib::datastore {

template <typename EntryT, typename RefT, typename ReclaimerT>
FreeListAllocator<EntryT, RefT, ReclaimerT>::FreeListAllocator(DataStoreBase &store, uint32_t typeId)
    : ParentType(store, typeId)
{
}

namespace allocator {

template <typename EntryT, typename ... Args>
struct Assigner {
    static void assign(EntryT &entry, Args && ... args) {
        entry = EntryT(std::forward<Args>(args)...);
    }
};

template <typename EntryT>
struct Assigner<EntryT> {
    static void assign(EntryT &entry) {
        (void) entry;
    }
};

// Assignment operator
template <typename EntryT>
struct Assigner<EntryT, const EntryT &> {
    static void assign(EntryT &entry, const EntryT &rhs) {
        entry = rhs;
    }
};

// Move assignment
template <typename EntryT>
struct Assigner<EntryT, EntryT &&> {
    static void assign(EntryT &entry, EntryT &&rhs) {
        entry = std::move(rhs);
    }
};

}

template <typename EntryT, typename RefT, typename ReclaimerT>
template <typename ... Args>
typename Allocator<EntryT, RefT>::HandleType
FreeListAllocator<EntryT, RefT, ReclaimerT>::alloc(Args && ... args)
{
    auto& free_list = _store.getFreeList(_typeId);
    if (free_list.empty()) {
        return ParentType::alloc(std::forward<Args>(args)...);
    }
    RefT ref = free_list.pop_entry();
    EntryT *entry = _store.template getEntry<EntryT>(ref);
    ReclaimerT::reclaim(entry);
    allocator::Assigner<EntryT, Args...>::assign(*entry, std::forward<Args>(args)...);
    return HandleType(ref, entry);
}

template <typename EntryT, typename RefT, typename ReclaimerT>
typename Allocator<EntryT, RefT>::HandleType
FreeListAllocator<EntryT, RefT, ReclaimerT>::allocArray(ConstArrayRef array)
{
    auto& free_list = _store.getFreeList(_typeId);
    if (free_list.empty()) {
        return ParentType::allocArray(array);
    }
    RefT ref = free_list.pop_entry();
    auto& state = _store.getBufferState(ref.bufferId());
    assert(state.getArraySize() == array.size());
    EntryT *buf = _store.template getEntryArray<EntryT>(ref, array.size());
    for (size_t i = 0; i < array.size(); ++i) {
        *(buf + i) = array[i];
    }
    return HandleType(ref, buf);
}

template <typename EntryT, typename RefT, typename ReclaimerT>
typename Allocator<EntryT, RefT>::HandleType
FreeListAllocator<EntryT, RefT, ReclaimerT>::allocArray()
{
    auto& free_list = _store.getFreeList(_typeId);
    if (free_list.empty()) {
        return ParentType::allocArray();
    }
    RefT ref = free_list.pop_entry();
    auto& state = _store.getBufferState(ref.bufferId());
    auto size = state.getArraySize();
    EntryT *buf = _store.template getEntryArray<EntryT>(ref, size);
    return HandleType(ref, buf);
}

template <typename EntryT, typename RefT, typename ReclaimerT>
template <typename BufferType>
typename Allocator<EntryT, RefT>::HandleType
FreeListAllocator<EntryT, RefT, ReclaimerT>::alloc_dynamic_array(ConstArrayRef array)
{
    auto& free_list = _store.getFreeList(_typeId);
    if (free_list.empty()) {
        return ParentType::template alloc_dynamic_array<BufferType>(array);
    }
    RefT ref = free_list.pop_entry();
    assert(_store.getBufferState(ref.bufferId()).getArraySize() >= array.size());
    auto entry_size = _store.get_entry_size(_typeId);
    EntryT* buf = BufferType::get_entry(_store.getBuffer(ref.bufferId()), ref.offset(), entry_size);
    for (size_t i = 0; i < array.size(); ++i) {
        *(buf + i) = array[i];
    }
    BufferType::set_dynamic_array_size(buf, array.size());
    return HandleType(ref, buf);
}

}

