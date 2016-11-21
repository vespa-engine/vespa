// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/datastore/array_store.hpp>

namespace search {
namespace attribute {

template <typename EntryT, typename RefT>
MultiValueMapping2<EntryT,RefT>::MultiValueMapping2(uint32_t maxSmallArraySize, const GrowStrategy &gs)
    : MultiValueMapping2Base(gs, _store.getGenerationHolder()),
      _store(maxSmallArraySize)
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
    EntryRef oldRef(_indices[docId]);
    ConstArrayRef oldValues = _store.get(oldRef);
    _indices[docId] = _store.add(values);
    updateValueCount(oldValues.size(), values.size());
    _store.remove(oldRef);
}

template <typename EntryT, typename RefT>
void
MultiValueMapping2<EntryT,RefT>::replace(uint32_t docId, ConstArrayRef values)
{
    ConstArrayRef oldValues = _store.get(_indices[docId]);
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

template <typename EntryT, typename RefT>
MemoryUsage
MultiValueMapping2<EntryT,RefT>::getArrayStoreMemoryUsage() const
{
    return _store.getMemoryUsage();
}

template <typename EntryT, typename RefT>
AddressSpace
MultiValueMapping2<EntryT, RefT>::getAddressSpaceUsage() const {
    return _store.addressSpaceUsage();
}

} // namespace search::attribute
} // namespace search
