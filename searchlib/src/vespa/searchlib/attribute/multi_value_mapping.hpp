// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multi_value_mapping.h"
#include <vespa/vespalib/datastore/array_store.hpp>
#include <vespa/vespalib/util/rcuvector.hpp>

namespace search::attribute {

template <typename EntryT, typename RefT>
MultiValueMapping<EntryT,RefT>::MultiValueMapping(const datastore::ArrayStoreConfig &storeCfg,
                                                  const vespalib::GrowStrategy &gs)
#ifdef __clang__
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wuninitialized"
#endif
    : MultiValueMappingBase(gs, _store.getGenerationHolder()),
#ifdef __clang__
#pragma clang diagnostic pop
#endif
      _store(storeCfg)
{
}

template <typename EntryT, typename RefT>
MultiValueMapping<EntryT,RefT>::~MultiValueMapping() = default;

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
    auto oldValues = _store.get_writable(_indices[docId]);
    assert(oldValues.size() == values.size());
    EntryT *dst = &oldValues[0];
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
        compactionContext->compact(vespalib::ArrayRef<EntryRef>(&_indices[0], _indices.size()));
    }
}

template <typename EntryT, typename RefT>
vespalib::MemoryUsage
MultiValueMapping<EntryT,RefT>::getArrayStoreMemoryUsage() const
{
    return _store.getMemoryUsage();
}

template <typename EntryT, typename RefT>
vespalib::AddressSpace
MultiValueMapping<EntryT, RefT>::getAddressSpaceUsage() const {
    return _store.addressSpaceUsage();
}

template <typename EntryT, typename RefT>
datastore::ArrayStoreConfig
MultiValueMapping<EntryT, RefT>::optimizedConfigForHugePage(size_t maxSmallArraySize,
                                                             size_t hugePageSize,
                                                             size_t smallPageSize,
                                                             size_t minNumArraysForNewBuffer,
                                                             float allocGrowFactor,
                                                             bool enable_free_lists)
{
    auto result = ArrayStore::optimizedConfigForHugePage(maxSmallArraySize, hugePageSize, smallPageSize, minNumArraysForNewBuffer, allocGrowFactor);
    result.enable_free_lists(enable_free_lists);
    return result;
}

}
