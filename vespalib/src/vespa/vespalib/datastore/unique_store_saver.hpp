// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "unique_store_saver.h"

namespace search::datastore {

template <typename EntryT, typename RefT>
UniqueStoreSaver<EntryT, RefT>::UniqueStoreSaver(const UniqueStoreDictionaryBase &dict, const DataStoreBase &store)
    : _dict(dict),
      _root(_dict.get_frozen_root()),
      _store(store),
      _next_enum_val(1)
{
}

template <typename EntryT, typename RefT>
UniqueStoreSaver<EntryT, RefT>::~UniqueStoreSaver()
{
}

template <typename EntryT, typename RefT>
void
UniqueStoreSaver<EntryT, RefT>::enumerateValue(EntryRef ref)
{
    RefType iRef(ref);
    assert(iRef.valid());
    assert(iRef.unscaled_offset() < _enumValues[iRef.bufferId()].size());
    uint32_t &enumVal = _enumValues[iRef.bufferId()][iRef.unscaled_offset()];
    assert(enumVal == 0u);
    enumVal = _next_enum_val;
    ++_next_enum_val;
}

template <typename EntryT, typename RefT>
void
UniqueStoreSaver<EntryT, RefT>::enumerateValues()
{
    _enumValues.resize(RefType::numBuffers());
    for (uint32_t bufferId = 0; bufferId < RefType::numBuffers(); ++bufferId) {
        const BufferState &state = _store.getBufferState(bufferId);
        if (state.isActive()) {
            _enumValues[bufferId].resize(state.size() / state.getArraySize());
        }
    }
    _next_enum_val = 1;
    _dict.foreach_key(_root, [this](EntryRef ref) { enumerateValue(ref); });
}

}
