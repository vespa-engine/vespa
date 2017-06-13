// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "unique_store_saver.h"

namespace search {
namespace datastore {

template <typename EntryT, typename RefT>
UniqueStoreSaver<EntryT, RefT>::UniqueStoreSaver(const Dictionary &dict, const DataStoreBase &store)
    : _itr(),
      _store(store)
{
    _itr = dict.getFrozenView().begin();
}

template <typename EntryT, typename RefT>
UniqueStoreSaver<EntryT, RefT>::~UniqueStoreSaver()
{
}

template <typename EntryT, typename RefT>
void
UniqueStoreSaver<EntryT, RefT>::enumerateValues()
{
    _enumValues.resize(RefType::numBuffers());
    for (uint32_t bufferId = 0; bufferId < RefType::numBuffers(); ++bufferId) {
        const BufferState &state = _store.getBufferState(bufferId);
        if (state.isActive()) {
            _enumValues[bufferId].resize(state.size());
        }
    }
    ConstIterator it = _itr;
    uint32_t nextEnumVal = 1;
    while (it.valid()) {
        RefType ref(it.getKey());
        assert(ref.valid());
        assert(ref.offset() < _enumValues[ref.bufferId()].size());
        uint32_t &enumVal = _enumValues[ref.bufferId()][ref.offset()];
        assert(enumVal == 0u);
        enumVal = nextEnumVal;
        ++it;
        ++nextEnumVal;
    }
}

}
}
