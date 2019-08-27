// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "unique_store_enumerator.h"

namespace search::datastore {

template <typename RefT>
UniqueStoreEnumerator<RefT>::UniqueStoreEnumerator(const UniqueStoreDictionaryBase &dict, const DataStoreBase &store)
    : _dict_snapshot(dict.get_read_snapshot()),
      _store(store),
      _enumValues(),
      _next_enum_val(1)
{
}

template <typename RefT>
UniqueStoreEnumerator<RefT>::~UniqueStoreEnumerator()
{
}

template <typename RefT>
void
UniqueStoreEnumerator<RefT>::enumerateValue(EntryRef ref)
{
    RefType iRef(ref);
    assert(iRef.valid());
    assert(iRef.unscaled_offset() < _enumValues[iRef.bufferId()].size());
    uint32_t &enumVal = _enumValues[iRef.bufferId()][iRef.unscaled_offset()];
    assert(enumVal == 0u);
    enumVal = _next_enum_val;
    ++_next_enum_val;
}

template <typename RefT>
void
UniqueStoreEnumerator<RefT>::enumerateValues()
{
    _enumValues.resize(RefType::numBuffers());
    for (uint32_t bufferId = 0; bufferId < RefType::numBuffers(); ++bufferId) {
        const BufferState &state = _store.getBufferState(bufferId);
        if (state.isActive()) {
            _enumValues[bufferId].resize(state.size() / state.getArraySize());
        }
    }
    _next_enum_val = 1;
    _dict_snapshot->foreach_key([this](EntryRef ref) { enumerateValue(ref); });
}

template <typename RefT>
void
UniqueStoreEnumerator<RefT>::clear()
{
    EnumValues().swap(_enumValues);
}

}
