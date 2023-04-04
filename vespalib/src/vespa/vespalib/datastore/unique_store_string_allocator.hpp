// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "unique_store_string_allocator.h"
#include "datastore.hpp"

namespace vespalib::datastore {

template <typename RefT>
UniqueStoreStringAllocator<RefT>::UniqueStoreStringAllocator(std::shared_ptr<alloc::MemoryAllocator> memory_allocator)
    : ICompactable(),
      _store(),
      _type_handlers()
{
    _type_handlers.emplace_back(std::make_unique<UniqueStoreExternalStringBufferType>(1, RefT::offsetSize(), memory_allocator));
    for (auto size : string_allocator::array_sizes) {
        _type_handlers.emplace_back(std::make_unique<UniqueStoreSmallStringBufferType>(size, RefT::offsetSize(), memory_allocator));
    }
    uint32_t exp_type_id = 0;
    for (auto &type_handler : _type_handlers) {
        auto type_id = _store.addType(type_handler.get());
        assert(type_id == exp_type_id);
        ++exp_type_id;
    }
    _store.init_primary_buffers();
    _store.enableFreeLists();
}

template <typename RefT>
UniqueStoreStringAllocator<RefT>::~UniqueStoreStringAllocator()
{
    _store.reclaim_all_memory();
    _store.dropBuffers();
}

template <typename RefT>
EntryRef
UniqueStoreStringAllocator<RefT>::allocate(const char *value)
{
    size_t value_len = strlen(value);
    uint32_t type_id = string_allocator::get_type_id(value_len);
    if (type_id != 0) {
        size_t array_size = string_allocator::array_sizes[type_id - 1];
        auto handle = _store.template freeListRawAllocator<char>(type_id).alloc(1);
        new (static_cast<void *>(handle.data)) UniqueStoreSmallStringEntry(value, value_len, array_size);
        return handle.ref;
    } else {
        auto handle = _store.template freeListAllocator<WrappedExternalEntryType, UniqueStoreEntryReclaimer<WrappedExternalEntryType>>(0).alloc(std::string(value));
        RefT iRef(handle.ref);
        auto &state = _store.getBufferState(iRef.bufferId());
        state.stats().inc_extra_used_bytes(value_len + 1);
        return handle.ref;
    }
}

template <typename RefT>
void
UniqueStoreStringAllocator<RefT>::hold(EntryRef ref)
{
    RefT iRef(ref);
    uint32_t type_id = _store.getTypeId(iRef.bufferId());
    if (type_id != 0) {
        _store.hold_entry(ref);
    } else {
        auto &value = _store.template getEntry<WrappedExternalEntryType>(iRef)->value();
        _store.hold_entry(ref, value.size() + 1);
    }
}

template <typename RefT>
EntryRef
UniqueStoreStringAllocator<RefT>::move_on_compact(EntryRef ref)
{
    RefT iRef(ref);
    uint32_t type_id = _store.getTypeId(iRef.bufferId());
    if (type_id != 0) {
        static_assert(std::is_trivially_copyable<UniqueStoreSmallStringEntry>::value,
                      "UniqueStoreSmallStringEntry must be trivially copyable");
        size_t array_size = string_allocator::array_sizes[type_id - 1];
        auto handle = _store.template rawAllocator<char>(type_id).alloc(1);
        auto orig = _store.template getEntryArray<char>(iRef, array_size);
        memcpy(handle.data, orig, array_size);
        return handle.ref;
    } else {
        auto handle = _store.template allocator<WrappedExternalEntryType>(0).alloc(*_store.template getEntry<WrappedExternalEntryType>(iRef));
        auto &state = _store.getBufferState(RefT(handle.ref).bufferId());
        auto &value = static_cast<const WrappedExternalEntryType *>(handle.data)->value();
        state.stats().inc_extra_used_bytes(value.size() + 1);
        return handle.ref;
    }
}

}
