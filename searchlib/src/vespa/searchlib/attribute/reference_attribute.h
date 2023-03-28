// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "not_implemented_attribute.h"
#include "reference.h"
#include "reference_attribute_compaction_spec.h"
#include "reference_mappings.h"
#include <vespa/vespalib/datastore/unique_store.h>
#include <vespa/vespalib/util/rcuvector.h>
#include <vespa/vespalib/stllike/allocator.h>

namespace search { class IGidToLidMapperFactory; }

namespace search::attribute {

/*
 * Attribute vector which maintains a lid-2-lid mapping from local document ids to global ids (referencing external documents)
 * and their local document ids counterpart.
 *
 * The lid-2-lid mapping is updated as follows:
 * 1) In populateTargetLids() all target lids are set by using the gid-2-lid mapper.
 * 1) In update() a new lid-gid pair is set and the target lid is set by using gid-2-lid mapper.
 * 2) In notifyGidToLidChange() a gid-reference-lid pair is set explicitly.
 */
class ReferenceAttribute : public NotImplementedAttribute
{
public:
    using AtomicEntryRef = vespalib::datastore::AtomicEntryRef;
    using CompactionStrategy = vespalib::datastore::CompactionStrategy;
    using EntryRef = vespalib::datastore::EntryRef;
    using GlobalId = document::GlobalId;
    using ReferenceStore = vespalib::datastore::UniqueStore<Reference>;
    using ReferenceStoreIndices = vespalib::RcuVectorBase<AtomicEntryRef>;
    // Class used to map from target lid to source lids
    using ReverseMapping = vespalib::btree::BTreeStore<uint32_t, vespalib::btree::BTreeNoLeafData,
                                             vespalib::btree::NoAggregated,
                                             std::less<uint32_t>,
                                             vespalib::btree::BTreeDefaultTraits,
                                             vespalib::btree::NoAggrCalc>;
    using TargetLids = ReferenceMappings::TargetLids;
    // Class used to map from target lid to source lids
    using ReverseMappingRefs = ReferenceMappings::ReverseMappingRefs;
private:
    ReferenceStore _store;
    ReferenceStoreIndices _indices;
    ReferenceAttributeCompactionSpec _compaction_spec;
    std::shared_ptr<IGidToLidMapperFactory> _gidToLidMapperFactory;
    ReferenceMappings _referenceMappings;

    void onAddDocs(DocId docIdLimit) override;
    void reclaim_memory(generation_t oldest_used_gen) override;
    void before_inc_generation(generation_t current_gen) override;
    void onCommit() override;
    void onUpdateStat() override;
    std::unique_ptr<AttributeSaver> onInitSave(vespalib::stringref fileName) override;
    bool onLoad(vespalib::Executor *executor) override;
    uint64_t getUniqueValueCount() const override;

    bool consider_compact_values(const CompactionStrategy &compactionStrategy);
    void compact_worst_values(const CompactionStrategy& compaction_strategy);
    bool consider_compact_dictionary(const CompactionStrategy& compaction_strategy);
    void removeReverseMapping(EntryRef oldRef, uint32_t lid);
    void addReverseMapping(EntryRef newRef, uint32_t lid);
    void buildReverseMapping(EntryRef newRef, const std::vector<ReverseMapping::KeyDataType> &adds);
    void buildReverseMapping();

public:
    using SP = std::shared_ptr<ReferenceAttribute>;
    ReferenceAttribute(const vespalib::stringref baseFileName);
    ReferenceAttribute(const vespalib::stringref baseFileName, const Config & cfg);
    ~ReferenceAttribute() override;
    bool addDoc(DocId &doc) override;
    uint32_t clearDoc(DocId doc) override;
    void update(DocId doc, const GlobalId &gid);
    const Reference *getReference(DocId doc) const;
    void setGidToLidMapperFactory(std::shared_ptr<IGidToLidMapperFactory> gidToLidMapperFactory);
    std::shared_ptr<IGidToLidMapperFactory> getGidToLidMapperFactory() const { return _gidToLidMapperFactory; }
    TargetLids getTargetLids() const { return _referenceMappings.getTargetLids(); }
    DocId getTargetLid(DocId doc) const { return _referenceMappings.getTargetLid(doc); }
    ReverseMappingRefs getReverseMappingRefs() const { return _referenceMappings.getReverseMappingRefs(); }
    const ReverseMapping &getReverseMapping() const { return _referenceMappings.getReverseMapping(); }

    void notifyReferencedPutNoCommit(const GlobalId &gid, DocId targetLid);
    void notifyReferencedPut(const GlobalId &gid, DocId targetLid);
    bool notifyReferencedRemoveNoCommit(const GlobalId &gid);
    void notifyReferencedRemove(const GlobalId &gid);
    void populateTargetLids(const std::vector<GlobalId>& removes);
    void clearDocs(DocId lidLow, DocId lidLimit, bool in_shrink_lid_space) override;
    void onShrinkLidSpace() override;

    template <typename FunctionType>
    void
    foreach_lid(uint32_t targetLid, FunctionType &&func) const {
        _referenceMappings.foreach_lid(targetLid, std::forward<FunctionType>(func));
    }

    std::unique_ptr<SearchContext> getSearch(QueryTermSimpleUP term, const attribute::SearchContextParams& params) const override;
};

}
