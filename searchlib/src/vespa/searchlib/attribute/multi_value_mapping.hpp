// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multi_value_mapping.h"
#include <vespa/vespalib/datastore/array_store.hpp>

namespace search::attribute {

template <typename EntryT, typename RefT>
MultiValueMapping<EntryT,RefT>::MultiValueMapping(const vespalib::datastore::ArrayStoreConfig &storeCfg,
                                                  const vespalib::GrowStrategy &gs,
                                                  std::shared_ptr<vespalib::alloc::MemoryAllocator> memory_allocator)
  : MultiValueMappingBase(gs, ArrayStore::getGenerationHolderLocation(_store), memory_allocator),
    _store(storeCfg, std::move(memory_allocator))
{
}

template <typename EntryT, typename RefT>
MultiValueMapping<EntryT,RefT>::~MultiValueMapping() = default;

template <typename EntryT, typename RefT>
void
MultiValueMapping<EntryT,RefT>::set(uint32_t docId, ConstArrayRef values)
{
    _indices.ensure_size(docId + 1);
    EntryRef oldRef(_indices[docId].load_relaxed());
    ConstArrayRef oldValues = _store.get(oldRef);
    _indices[docId].store_release(_store.add(values));
    updateValueCount(oldValues.size(), values.size());
    _store.remove(oldRef);
}

template <typename EntryT, typename RefT>
void
MultiValueMapping<EntryT,RefT>::compactWorst(CompactionSpec compaction_spec, const CompactionStrategy& compaction_strategy)
{
    vespalib::datastore::ICompactionContext::UP compactionContext(_store.compactWorst(compaction_spec, compaction_strategy));
    if (compactionContext) {
        compactionContext->compact(vespalib::ArrayRef<AtomicEntryRef>(&_indices[0], _indices.size()));
    }
}

template <typename EntryT, typename RefT>
bool
MultiValueMapping<EntryT,RefT>::has_held_buffers() const noexcept
{
    return _store.has_held_buffers();
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
vespalib::datastore::ArrayStoreConfig
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
