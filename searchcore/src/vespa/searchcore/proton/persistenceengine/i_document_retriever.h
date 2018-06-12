// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <persistence/spi/types.h>
#include <vespa/persistence/spi/bucket.h>
#include <vespa/persistence/spi/read_consistency.h>
#include <vespa/searchlib/common/idocumentmetastore.h>
#include <vespa/searchlib/docstore/idocumentstore.h>
#include <vespa/searchcore/proton/common/cachedselect.h>
#include <vespa/searchlib/query/base.h>
#include <vespa/searchcore/proton/documentmetastore/i_document_meta_store_context.h>

namespace document { class Document; }

namespace proton {

/**
 * This is an interface that allows retrieval of documents by local id and document metadata
 * by either bucket or document id.
 * It also provides a callback interface known in VDS as visitation.
 **/ 
class IDocumentRetriever
{
public:
    using ReadConsistency = storage::spi::ReadConsistency;
    using ReadGuard = IDocumentMetaStoreContext::IReadGuard::UP;
    using UP = std::unique_ptr<IDocumentRetriever>;
    using SP = std::shared_ptr<IDocumentRetriever>;

    using LidVector = search::IDocumentStore::LidVector;
    using DocumentUP = std::unique_ptr<document::Document>;

    virtual ~IDocumentRetriever() {}

    virtual const document::DocumentTypeRepo &getDocumentTypeRepo() const = 0;
    virtual void getBucketMetaData(const storage::spi::Bucket &bucket, search::DocumentMetaData::Vector &result) const = 0;
    virtual search::DocumentMetaData getDocumentMetaData(const document::DocumentId &id) const = 0;
    virtual DocumentUP getDocument(search::DocumentIdT lid) const = 0;
    virtual ReadGuard getReadGuard() const = 0;
    virtual uint32_t getDocIdLimit() const = 0;
    /**
     * Will visit all documents in the the given list. Visit order is undefined and will
     * be conducted in most efficient retrieval order.
     * @param lids to visit
     * @param Visitor to receive callback for each document found.
     */
    virtual void visitDocuments(const LidVector &lids, search::IDocumentVisitor &visitor, ReadConsistency readConsistency) const = 0;

    virtual CachedSelect::SP parseSelect(const vespalib::string &selection) const = 0;
};

class DocumentRetrieverBaseForTest : public IDocumentRetriever {
public:
    void visitDocuments(const LidVector &lids, search::IDocumentVisitor &visitor, ReadConsistency readConsistency) const override;
    ReadGuard getReadGuard() const override { return ReadGuard(); }
    uint32_t getDocIdLimit() const override { return std::numeric_limits<uint32_t>::max(); }
};

} // namespace proton

