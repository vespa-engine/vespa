// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multi_value_mapping_base.h"
#include "multi_value_mapping_read_view.h"
#include <vespa/vespalib/datastore/array_store.h>
#include <vespa/vespalib/util/address_space.h>

namespace search::attribute {

/**
 * Class for mapping from from document id to an array of values.
 */
template <typename EntryT, typename RefT = vespalib::datastore::EntryRefT<19> >
class MultiValueMapping : public MultiValueMappingBase
{
public:
    using MultiValueType = EntryT;
    using RefType = RefT;
    using ReadView = MultiValueMappingReadView<EntryT, RefT>;
private:
    using ArrayRef = vespalib::ArrayRef<EntryT>;
    using ArrayStore = vespalib::datastore::ArrayStore<EntryT, RefT>;
    using generation_t = vespalib::GenerationHandler::generation_t;
    using ConstArrayRef = vespalib::ConstArrayRef<EntryT>;

    ArrayStore _store;
public:
    MultiValueMapping(const MultiValueMapping &) = delete;
    MultiValueMapping & operator = (const MultiValueMapping &) = delete;
    MultiValueMapping(const vespalib::datastore::ArrayStoreConfig &storeCfg,
                      const vespalib::GrowStrategy &gs,
                      std::shared_ptr<vespalib::alloc::MemoryAllocator> memory_allocator);
    ~MultiValueMapping() override;
    ConstArrayRef get(uint32_t docId) const { return _store.get(acquire_entry_ref(docId)); }
    ConstArrayRef getDataForIdx(EntryRef idx) const { return _store.get(idx); }
    void set(uint32_t docId, ConstArrayRef values);

    // get_writable is generally unsafe and should only be used when
    // compacting enum store (replacing old enum index with updated enum index)
    ArrayRef get_writable(uint32_t docId) { return _store.get_writable(_indices[docId].load_relaxed()); }

    /*
     * Readers holding a generation guard can call get_read_view() to
     * get a read view to the multi value mapping. Array bound (read_size) must
     * be specified by reader, cf. committed docid limit in attribute vectors.
     */
    ReadView get_read_view(size_t read_size) const { return ReadView(_indices.get_read_view(read_size), &_store); }
    // Pass on hold list management to underlying store
    void transferHoldLists(generation_t generation) { _store.transferHoldLists(generation); }
    void trimHoldLists(generation_t firstUsed) { _store.trimHoldLists(firstUsed); }
    void prepareLoadFromMultiValue() { _store.setInitializing(true); }

    void doneLoadFromMultiValue() { _store.setInitializing(false); }

    void compactWorst(CompactionSpec compactionSpec, const CompactionStrategy& compaction_strategy) override;

    vespalib::AddressSpace getAddressSpaceUsage() const override;
    vespalib::MemoryUsage getArrayStoreMemoryUsage() const override;
    bool has_free_lists_enabled() const { return _store.has_free_lists_enabled(); }

    static vespalib::datastore::ArrayStoreConfig optimizedConfigForHugePage(size_t maxSmallArraySize,
                                                                  size_t hugePageSize,
                                                                  size_t smallPageSize,
                                                                  size_t minNumArraysForNewBuffer,
                                                                  float allocGrowFactor,
                                                                  bool enable_free_lists);
};

}
