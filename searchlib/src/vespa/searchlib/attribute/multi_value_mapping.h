// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multi_value_mapping_base.h"
#include <vespa/vespalib/datastore/array_store.h>
#include <vespa/vespalib/util/address_space.h>

namespace search::attribute {

/**
 * Class for mapping from from document id to an array of values.
 */
template <typename EntryT, typename RefT = datastore::EntryRefT<19> >
class MultiValueMapping : public MultiValueMappingBase
{
public:
    using MultiValueType = EntryT;
    using RefType = RefT;
private:
    using ArrayStore = datastore::ArrayStore<EntryT, RefT>;
    using generation_t = vespalib::GenerationHandler::generation_t;
    using ConstArrayRef = vespalib::ConstArrayRef<EntryT>;

    ArrayStore _store;
public:
    MultiValueMapping(const MultiValueMapping &) = delete;
    MultiValueMapping & operator = (const MultiValueMapping &) = delete;
    MultiValueMapping(const datastore::ArrayStoreConfig &storeCfg,
                      const vespalib::GrowStrategy &gs = vespalib::GrowStrategy());
    ~MultiValueMapping() override;
    ConstArrayRef get(uint32_t docId) const { return _store.get(_indices[docId]); }
    ConstArrayRef getDataForIdx(EntryRef idx) const { return _store.get(idx); }
    void set(uint32_t docId, ConstArrayRef values);

    // replace is generally unsafe and should only be used when
    // compacting enum store (replacing old enum index with updated enum index)
    void replace(uint32_t docId, ConstArrayRef values);

    // Pass on hold list management to underlying store
    void transferHoldLists(generation_t generation) { _store.transferHoldLists(generation); }
    void trimHoldLists(generation_t firstUsed) { _store.trimHoldLists(firstUsed); }
    void prepareLoadFromMultiValue() { _store.setInitializing(true); }

    void doneLoadFromMultiValue() { _store.setInitializing(false); }

    void compactWorst(bool compactMemory, bool compactAddressSpace) override;

    vespalib::AddressSpace getAddressSpaceUsage() const override;
    vespalib::MemoryUsage getArrayStoreMemoryUsage() const override;
    bool has_free_lists_enabled() const { return _store.has_free_lists_enabled(); }

    static datastore::ArrayStoreConfig optimizedConfigForHugePage(size_t maxSmallArraySize,
                                                                  size_t hugePageSize,
                                                                  size_t smallPageSize,
                                                                  size_t minNumArraysForNewBuffer,
                                                                  float allocGrowFactor,
                                                                  bool enable_free_lists);
};

}
