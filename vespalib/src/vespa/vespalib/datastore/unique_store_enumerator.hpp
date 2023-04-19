// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "unique_store_enumerator.h"
#include "bufferstate.h"
#include "datastorebase.h"

namespace vespalib::datastore {

template <typename RefT>
UniqueStoreEnumerator<RefT>::UniqueStoreEnumerator(const IUniqueStoreDictionary &dict, DataStoreBase &store, bool sort_unique_values)
    : _dict_snapshot(dict.get_read_snapshot()),
      _store(store),
      _enumValues(),
      _next_enum_val(1)
{
    _dict_snapshot->fill();
    if (sort_unique_values) {
        _dict_snapshot->sort();
    }
    allocate_enum_values(store);
}

template <typename RefT>
UniqueStoreEnumerator<RefT>::~UniqueStoreEnumerator() = default;

template <typename RefT>
void
UniqueStoreEnumerator<RefT>::enumerateValue(EntryRef ref)
{
    RefType iRef(ref);
    assert(iRef.valid());
    assert(iRef.offset() < _enumValues[iRef.bufferId()].size());
    uint32_t &enumVal = _enumValues[iRef.bufferId()][iRef.offset()];
    assert(enumVal == 0u);
    enumVal = _next_enum_val;
    ++_next_enum_val;
}

template <typename RefT>
void
UniqueStoreEnumerator<RefT>::allocate_enum_values(DataStoreBase & store)
{
    _enumValues.resize(store.get_bufferid_limit_relaxed());
    store.for_each_active_buffer([this](uint32_t buffer_id, const BufferState & state) {
        _enumValues[buffer_id].resize(state.size());
    });
}

template <typename RefT>
void
UniqueStoreEnumerator<RefT>::enumerateValues()
{
    _next_enum_val = 1;
    _dict_snapshot->foreach_key([this](const AtomicEntryRef& ref) noexcept { enumerateValue(ref.load_acquire()); });
}

template <typename RefT>
void
UniqueStoreEnumerator<RefT>::clear()
{
    EnumValues().swap(_enumValues);
}

}
