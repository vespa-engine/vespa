// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/datastore/array_store.hpp>

namespace search {
namespace attribute {

template <typename EntryT, typename RefT>
MultiValueMapping2<EntryT,RefT>::MultiValueMapping2(uint32_t maxSmallArraySize, const GrowStrategy &gs)
    : _store(maxSmallArraySize),
      _indices(gs.getDocsInitialCapacity(),
               gs.getDocsGrowPercent(),
               gs.getDocsGrowDelta(),
               _store.getGenerationHolder())
{
}

template <typename EntryT, typename RefT>
MultiValueMapping2<EntryT,RefT>::~MultiValueMapping2()
{
}


template <typename EntryT, typename RefT>
void
MultiValueMapping2<EntryT,RefT>::set(uint32_t docId, ConstArrayRef values)
{
    _indices.ensure_size(docId + 1);
    _store.remove(_indices[docId]);
    _indices[docId] = _store.add(values);
}

template <typename EntryT, typename RefT>
void
MultiValueMapping2<EntryT,RefT>::replace(uint32_t docId, ConstArrayRef values)
{
    ConstArrayRef oldValues = _store.get(docId);
    assert(oldValues.size() == values.size());
    EntryT *dst = const_cast<EntryT *>(&oldValues[0]);
    for (auto &src : values) {
        *dst = src;
        ++dst;
    }
}

template <typename EntryT, typename RefT>
void
MultiValueMapping2<EntryT,RefT>::compactWorst()
{
    datastore::ICompactionContext::UP compactionContext(_store.compactWorst());
    if (compactionContext) {
        compactionContext->compact(vespalib::ArrayRef<EntryRef>(&_indices[0],
                                                                _indices.size()));
    }
}

} // namespace search::attribute
} // namespace search
