// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multi_value_mapping.h"
#include <vespa/vespalib/datastore/array_store.hpp>

namespace search::attribute {

template <typename ElemT, typename RefT>
MultiValueMapping<ElemT,RefT>::MultiValueMapping(const vespalib::datastore::ArrayStoreConfig &storeCfg,
                                                  const vespalib::GrowStrategy &gs,
                                                  std::shared_ptr<vespalib::alloc::MemoryAllocator> memory_allocator)
  : MultiValueMappingBase(gs, ArrayStore::getGenerationHolderLocation(_store), memory_allocator),
    _store(storeCfg, std::move(memory_allocator), ArrayStoreTypeMapper(storeCfg.max_type_id(), array_store_grow_factor))
{
}

template <typename ElemT, typename RefT>
MultiValueMapping<ElemT,RefT>::~MultiValueMapping() = default;

template <typename ElemT, typename RefT>
void
MultiValueMapping<ElemT,RefT>::set(uint32_t docId, ConstArrayRef values)
{
    _indices.ensure_size(docId + 1);
    EntryRef oldRef(_indices[docId].load_relaxed());
    ConstArrayRef oldValues = _store.get(oldRef);
    _indices[docId].store_release(_store.add(values));
    updateValueCount(oldValues.size(), values.size());
    _store.remove(oldRef);
}

template <typename ElemT, typename RefT>
vespalib::MemoryUsage
MultiValueMapping<ElemT,RefT>::update_stat(const CompactionStrategy& compaction_strategy)
{
    auto retval = _store.update_stat(compaction_strategy);
    retval.merge(_indices.getMemoryUsage());
    return retval;
}

template <typename ElemT, typename RefT>
void
MultiValueMapping<ElemT,RefT>::compact_worst(const CompactionStrategy& compaction_strategy)
{
    vespalib::datastore::ICompactionContext::UP compactionContext(_store.compact_worst(compaction_strategy));
    if (compactionContext) {
        compactionContext->compact(vespalib::ArrayRef<AtomicEntryRef>(&_indices[0], _indices.size()));
    }
}

template <typename ElemT, typename RefT>
vespalib::MemoryUsage
MultiValueMapping<ElemT,RefT>::getArrayStoreMemoryUsage() const
{
    return _store.getMemoryUsage();
}

template <typename ElemT, typename RefT>
vespalib::AddressSpace
MultiValueMapping<ElemT, RefT>::getAddressSpaceUsage() const {
    return _store.addressSpaceUsage();
}

template <typename ElemT, typename RefT>
vespalib::datastore::ArrayStoreConfig
MultiValueMapping<ElemT, RefT>::optimizedConfigForHugePage(size_t max_type_id,
                                                           size_t hugePageSize,
                                                           size_t smallPageSize,
                                                           size_t max_buffer_size,
                                                           size_t min_num_entries_for_new_buffer,
                                                           float allocGrowFactor,
                                                           bool enable_free_lists)
{
    ArrayStoreTypeMapper mapper(max_type_id, array_store_grow_factor);
    auto result = ArrayStore::optimizedConfigForHugePage(max_type_id, mapper, hugePageSize, smallPageSize, max_buffer_size, min_num_entries_for_new_buffer, allocGrowFactor);
    result.enable_free_lists(enable_free_lists);
    return result;
}

}
