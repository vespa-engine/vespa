// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "not_implemented_attribute.h"
#include "reference_mappings.h"
#include "reference.h"
#include <vespa/searchlib/datastore/unique_store.h>
#include <vespa/searchlib/common/rcuvector.h>

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
    using EntryRef = search::datastore::EntryRef;
    using GlobalId = document::GlobalId;
    using ReferenceStore = datastore::UniqueStore<Reference>;
    using ReferenceStoreIndices = RcuVectorBase<EntryRef>;
    using IndicesCopyVector = vespalib::Array<EntryRef>;
    using ReverseMappingIndices = RcuVectorBase<EntryRef>;
    using ReverseMapping = btree::BTreeStore<uint32_t, btree::BTreeNoLeafData,
                                             btree::NoAggregated,
                                             std::less<uint32_t>,
                                             btree::BTreeDefaultTraits,
                                             btree::NoAggrCalc>;
    using TargetLids = ReferenceMappings::TargetLids;
    using ReverseMappingRefs = ReferenceMappings::ReverseMappingRefs;
private:
    ReferenceStore _store;
    ReferenceStoreIndices _indices;
    MemoryUsage _cachedUniqueStoreMemoryUsage;
    std::shared_ptr<IGidToLidMapperFactory> _gidToLidMapperFactory;
    ReferenceMappings _referenceMappings;

    virtual void onAddDocs(DocId docIdLimit) override;
    virtual void removeOldGenerations(generation_t firstUsed) override;
    virtual void onGenerationChange(generation_t generation) override;
    virtual void onCommit() override;
    virtual void onUpdateStat() override;
    virtual std::unique_ptr<AttributeSaver> onInitSave() override;
    virtual bool onLoad() override;
    virtual uint64_t getUniqueValueCount() const override;

    bool considerCompact(const CompactionStrategy &compactionStrategy);
    void compactWorst();
    IndicesCopyVector getIndicesCopy(uint32_t size) const;
    void removeReverseMapping(EntryRef oldRef, uint32_t lid);
    void addReverseMapping(EntryRef newRef, uint32_t lid);
    void buildReverseMapping(EntryRef newRef, const std::vector<ReverseMapping::KeyDataType> &adds);
    void buildReverseMapping();

public:
    using SP = std::shared_ptr<ReferenceAttribute>;
    DECLARE_IDENTIFIABLE_ABSTRACT(ReferenceAttribute);
    ReferenceAttribute(const vespalib::stringref baseFileName,
                       const Config & cfg);
    virtual ~ReferenceAttribute();
    virtual bool addDoc(DocId &doc) override;
    virtual uint32_t clearDoc(DocId doc) override;
    void update(DocId doc, const GlobalId &gid);
    const Reference *getReference(DocId doc);
    void setGidToLidMapperFactory(std::shared_ptr<IGidToLidMapperFactory> gidToLidMapperFactory);
    std::shared_ptr<IGidToLidMapperFactory> getGidToLidMapperFactory() const { return _gidToLidMapperFactory; }
    TargetLids getTargetLids() const { return _referenceMappings.getTargetLids(); }
    DocId getTargetLid(DocId doc) const { return _referenceMappings.getTargetLid(doc); }
    ReverseMappingRefs getReverseMappingRefs() const { return _referenceMappings.getReverseMappingRefs(); }
    const ReverseMapping &getReverseMapping() const { return _referenceMappings.getReverseMapping(); }

    void notifyReferencedPutNoCommit(const GlobalId &gid, DocId targetLid);
    void notifyReferencedPut(const GlobalId &gid, DocId targetLid);
    void notifyReferencedRemove(const GlobalId &gid);
    void populateTargetLids();
    virtual void clearDocs(DocId lidLow, DocId lidLimit) override;
    virtual void onShrinkLidSpace() override;

    template <typename FunctionType>
    void
    foreach_lid(uint32_t targetLid, FunctionType &&func) const {
        _referenceMappings.foreach_lid(targetLid, std::forward<FunctionType>(func));
    }
};

}
