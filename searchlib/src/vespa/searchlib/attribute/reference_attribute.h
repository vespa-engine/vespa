// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "not_implemented_attribute.h"
#include <vespa/document/base/globalid.h>
#include <vespa/searchlib/datastore/unique_store.h>
#include <vespa/searchlib/common/rcuvector.h>

namespace search {
namespace attribute {

/*
 * Attribute vector mapping from local document ids to global ids
 * referencing external documents.
 */
class ReferenceAttribute : public NotImplementedAttribute
{
public:
    using EntryRef = search::datastore::EntryRef;
    using GlobalId = document::GlobalId;
    using Store = datastore::UniqueStore<GlobalId>;
    using IndicesCopyVector = vespalib::Array<EntryRef>;

private:
    Store _store;
    RcuVectorBase<EntryRef> _indices;
    MemoryUsage _cachedUniqueStoreMemoryUsage;

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
    DECLARE_IDENTIFIABLE_ABSTRACT(ReferenceAttribute);
    ReferenceAttribute(const vespalib::stringref baseFileName,
                       const Config & cfg);
    virtual ~ReferenceAttribute();
    virtual bool addDoc(DocId &doc) override;
    virtual uint32_t clearDoc(DocId doc) override;
    void update(DocId doc, const GlobalId &gid);
    const GlobalId *getReference(DocId doc);
};

}
}
