// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "documentmetastore.h"
#include "i_document_meta_store_context.h"
#include <vespa/searchcore/proton/bucketdb/bucket_db_owner.h>

namespace proton {

/**
 * Class providing write and read interface to the document meta store.
 */
class DocumentMetaStoreContext : public IDocumentMetaStoreContext
{
public:
    class ReadGuard : public IDocumentMetaStoreContext::IReadGuard
    {
    private:
        search::AttributeGuard   _guard;
        const DocumentMetaStore &_store;
    public:
        ReadGuard(const search::AttributeVector::SP &metaStoreAttr);
        const search::IDocumentMetaStore &get() const override { return _store; }
    };
private:
    search::AttributeVector::SP _metaStoreAttr;
    IDocumentMetaStore::SP      _metaStore;
public:

    /**
     * Create a new context instantiating a document meta store
     * with the given name, grow strategy, and comparator.
     */
    DocumentMetaStoreContext(BucketDBOwner::SP bucketDB,
                             const vespalib::string &name = DocumentMetaStore::getFixedName(),
                             const search::GrowStrategy &grow = search::GrowStrategy(),
                             const DocumentMetaStore::IGidCompare::SP &gidCompare =
                             DocumentMetaStore::IGidCompare::SP(new DocumentMetaStore::DefaultGidCompare));
    ~DocumentMetaStoreContext();
    /**
     * Create a new context with the given document meta store encapsulated
     * as an attribute vector.
     */
    DocumentMetaStoreContext(const search::AttributeVector::SP &metaStoreAttr);

    proton::IDocumentMetaStore::SP   getSP() const override { return _metaStore; }
    proton::IDocumentMetaStore &       get()       override { return *_metaStore; }
    IReadGuard::UP getReadGuard() const override {
        return std::make_unique<ReadGuard>(_metaStoreAttr);
    }

    void constructFreeList() override;
};

} // namespace proton

