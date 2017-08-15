// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "not_implemented_attribute.h"
#include <vespa/document/base/globalid.h>
#include <vespa/searchlib/datastore/unique_store.h>
#include <vespa/searchlib/common/rcuvector.h>
#include "postingstore.h"

namespace search {

class IGidToLidMapperFactory;

namespace attribute {

/*
 * Attribute vector which maintains a lid-2-lid mapping from local document ids to global ids (referencing external documents)
 * and their local document ids counterpart.
 *
 * The lid-2-lid mapping is updated as follows:
 * 1) In populateReferencedLids() all referenced lids are set by using the gid-2-lid mapper.
 * 1) In update() a new lid-gid pair is set and the referenced lid is set by using gid-2-lid mapper.
 * 2) In notifyGidToLidChange() a gid-reference-lid pair is set explicitly.
 */
class ReferenceAttribute : public NotImplementedAttribute
{
public:
    using EntryRef = search::datastore::EntryRef;
    using GlobalId = document::GlobalId;
    class Reference {
        GlobalId _gid;
        mutable uint32_t _lid;  // referenced lid
        mutable EntryRef _revMapIdx; // map from gid to lids referencing gid
    public:
        Reference()
            : _gid(),
              _lid(0u),
              _revMapIdx()
        {
        }
        Reference(const GlobalId &gid_)
            : _gid(gid_),
              _lid(0u),
              _revMapIdx()
        {
        }
        bool operator<(const Reference &rhs) const {
            return _gid < rhs._gid;
        }
        const GlobalId &gid() const { return _gid; }
        uint32_t lid() const { return _lid; }
        EntryRef revMapIdx() const { return _revMapIdx; }
        void setLid(uint32_t referencedLid) const { _lid = referencedLid; }
        void setRevMapIdx(EntryRef newRevMapIdx) const { _revMapIdx = newRevMapIdx; }
    };
    using ReferenceStore = datastore::UniqueStore<Reference>;
    using ReferenceStoreIndices = RcuVectorBase<EntryRef>;
    using IndicesCopyVector = vespalib::Array<EntryRef>;
    using ReverseMappingIndices = RcuVectorBase<EntryRef>;
    using ReverseMapping = btree::BTreeStore<uint32_t, btree::BTreeNoLeafData,
                                             btree::NoAggregated,
                                             std::less<uint32_t>,
                                             btree::BTreeDefaultTraits,
                                             btree::NoAggrCalc>;
private:
    ReferenceStore _store;
    ReferenceStoreIndices _indices;
    MemoryUsage _cachedUniqueStoreMemoryUsage;
    std::shared_ptr<IGidToLidMapperFactory> _gidToLidMapperFactory;
    // Vector containing references to trees of lids referencing given
    // referenced lid.
    ReverseMappingIndices _reverseMappingIndices;
    // Store of B-Trees, used to map from gid or referenced lid to
    // referencing lids.
    ReverseMapping _reverseMapping;

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
    void syncReverseMappingIndices(const Reference &entry);
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
    DocId getReferencedLid(DocId doc) const;
    void notifyGidToLidChange(const GlobalId &gid, DocId referencedLid);
    void populateReferencedLids();
    virtual void clearDocs(DocId lidLow, DocId lidLimit) override;
    virtual void onShrinkLidSpace() override;

    template <typename FunctionType>
    void
    foreach_lid(uint32_t referencedLid, FunctionType &&func) const;
};

template <typename FunctionType>
void
ReferenceAttribute::foreach_lid(uint32_t referencedLid, FunctionType &&func) const
{
    if (referencedLid < _reverseMappingIndices.size()) {
        EntryRef revMapIdx = _reverseMappingIndices[referencedLid];
        _reverseMapping.foreach_frozen_key(revMapIdx, func);
    }
}

}
}
