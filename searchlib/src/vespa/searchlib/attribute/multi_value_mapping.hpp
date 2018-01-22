// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multi_value_mapping.h"
#include <vespa/searchlib/datastore/array_store.hpp>
#include <vespa/searchlib/common/rcuvector.hpp>

namespace search {
namespace attribute {

template <typename EntryT, typename RefT>
MultiValueMapping<EntryT,RefT>::MultiValueMapping(const datastore::ArrayStoreConfig &storeCfg, const GrowStrategy &gs)
    : MultiValueMappingBase(gs, _store.getGenerationHolder()),
      _store(storeCfg)
{
}

template <typename EntryT, typename RefT>
MultiValueMapping<EntryT,RefT>::~MultiValueMapping()
{
}

template <typename EntryT, typename RefT>
void
MultiValueMapping<EntryT,RefT>::set(uint32_t docId, ConstArrayRef values)
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
MultiValueMapping<EntryT,RefT>::replace(uint32_t docId, ConstArrayRef values)
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
MultiValueMapping<EntryT,RefT>::compactWorst(bool compactMemory, bool compactAddressSpace)
{
    datastore::ICompactionContext::UP compactionContext(_store.compactWorst(compactMemory, compactAddressSpace));
    if (compactionContext) {
        compactionContext->compact(vespalib::ArrayRef<EntryRef>(&_indices[0],
                                                                _indices.size()));
    }
}

template <typename EntryT, typename RefT>
MemoryUsage
MultiValueMapping<EntryT,RefT>::getArrayStoreMemoryUsage() const
{
    return _store.getMemoryUsage();
}

template <typename EntryT, typename RefT>
AddressSpace
MultiValueMapping<EntryT, RefT>::getAddressSpaceUsage() const {
    return _store.addressSpaceUsage();
}

template <typename EntryT, typename RefT>
datastore::ArrayStoreConfig
MultiValueMapping<EntryT, RefT>::optimizedConfigForHugePage(size_t maxSmallArraySize,
                                                             size_t hugePageSize,
                                                             size_t smallPageSize,
                                                             size_t minNumArraysForNewBuffer,
                                                             float allocGrowFactor)
{
    return ArrayStore::optimizedConfigForHugePage(maxSmallArraySize, hugePageSize, smallPageSize, minNumArraysForNewBuffer, allocGrowFactor);
}

} // namespace search::attribute
} // namespace search
