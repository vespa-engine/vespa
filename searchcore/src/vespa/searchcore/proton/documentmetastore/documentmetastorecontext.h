// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_document_meta_store_context.h"
#include <vespa/searchcommon/common/growstrategy.h>

namespace proton {

class DocumentMetaStore;

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
        explicit ReadGuard(const std::shared_ptr<search::AttributeVector> &metaStoreAttr);
        const search::IDocumentMetaStore &get() const override;
    };
private:
    std::shared_ptr<search::AttributeVector>  _metaStoreAttr;
    std::shared_ptr<IDocumentMetaStore>       _metaStore;
public:

    explicit DocumentMetaStoreContext(std::shared_ptr<bucketdb::BucketDBOwner> bucketDB);
    /**
     * Create a new context instantiating a document meta store
     * with the given name, grow strategy, and comparator.
     */
    DocumentMetaStoreContext(std::shared_ptr<bucketdb::BucketDBOwner> bucketDB,
                             const vespalib::string &name,
                             const search::GrowStrategy &grow);
    ~DocumentMetaStoreContext() override;
    /**
     * Create a new context with the given document meta store encapsulated
     * as an attribute vector.
     */
    explicit DocumentMetaStoreContext(std::shared_ptr<search::AttributeVector> metaStoreAttr);

    std::shared_ptr<proton::IDocumentMetaStore>   getSP() const override { return _metaStore; }
    proton::IDocumentMetaStore &       get()       override { return *_metaStore; }
    IReadGuard::SP getReadGuard() const override {
        return std::make_shared<ReadGuard>(_metaStoreAttr);
    }

    void constructFreeList() override;
};

} // namespace proton

