// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "not_implemented_attribute.h"
#include <vespa/document/base/globalid.h>
#include <vespa/searchlib/datastore/unique_store.h>
#include <vespa/searchlib/common/rcuvector.h>

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
        mutable uint32_t _lid;
    public:
        Reference()
            : _gid(),
              _lid(0u)
        {
        }
        Reference(const GlobalId &gid_)
            : _gid(gid_),
              _lid(0u)
        {
        }
        bool operator<(const Reference &rhs) const {
            return _gid < rhs._gid;
        }
        const GlobalId &gid() const { return _gid; }
        uint32_t lid() const { return _lid; }
        void setLid(uint32_t referencedLid) const { _lid = referencedLid; }
    };
    using Store = datastore::UniqueStore<Reference>;
    using IndicesCopyVector = vespalib::Array<EntryRef>;
private:
    Store _store;
    RcuVectorBase<EntryRef> _indices;
    MemoryUsage _cachedUniqueStoreMemoryUsage;
    std::shared_ptr<IGidToLidMapperFactory> _gidToLidMapperFactory;

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
};

}
}
