// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/persistence/spi/bucket.h>
#include <vespa/persistence/spi/read_consistency.h>
#include <vespa/persistence/spi/types.h>
#include <vespa/searchcore/proton/common/cachedselect.h>
#include <vespa/searchcore/proton/documentmetastore/i_document_meta_store_context.h>
#include <vespa/searchlib/common/idocumentmetastore.h>
#include <vespa/searchlib/docstore/idocumentstore.h>

namespace document {
class FieldSet;
}

namespace proton {

class DocTypeName;

/**
 * This is an interface that allows retrieval of documents by local id and document metadata
 * by either bucket or document id.
 * It also provides a callback interface known in VDS as visitation.
 **/
class IDocumentRetriever {
public:
    using ReadConsistency = storage::spi::ReadConsistency;
    using ReadGuard = IDocumentMetaStoreContext::IReadGuard::SP;
    using UP = std::unique_ptr<IDocumentRetriever>;
    using SP = std::shared_ptr<IDocumentRetriever>;

    using LidVector = search::IDocumentStore::LidVector;
    using DocumentUP = std::unique_ptr<document::Document>;

    virtual ~IDocumentRetriever() = default;

    virtual const document::DocumentTypeRepo& getDocumentTypeRepo() const = 0;
    virtual const DocTypeName& get_doc_type_name() const noexcept = 0;
    virtual bool can_populate_document_metadata_docid() const noexcept = 0;
    virtual void getBucketMetadata(const storage::spi::Bucket& bucket, search::DocumentMetadata::Vector& result,
                                   bool populate_docid) const = 0;
    virtual search::DocumentMetadata getDocumentMetadata(const document::DocumentId& id) const = 0;
    /**
     * Extracts the full document based on the LID
     */
    virtual DocumentUP getFullDocument(search::DocumentIdT lid) const = 0;
    /**
     * Fetches the necessary set of fields, allowing for more optimal fetch when combining only from attributes.
     */
    virtual DocumentUP getPartialDocument(search::DocumentIdT lid, const document::DocumentId& docId,
                                          const document::FieldSet& fieldSet) const;
    // Check if getPartialDocument() must fetch the full document from doc store.
    virtual bool need_fetch_from_doc_store(const document::FieldSet& field_set) const = 0;
    virtual ReadGuard getReadGuard() const = 0;
    virtual uint32_t getDocIdLimit() const = 0;
    /**
     * Will visit all documents in the the given list. Visit order is undefined and will
     * be conducted in most efficient retrieval order.
     * @param lids to visit
     * @param Visitor to receive callback for each document found.
     */
    virtual void visitDocuments(const LidVector& lids, search::IDocumentVisitor& visitor,
                                ReadConsistency readConsistency) const = 0;

    virtual CachedSelect::SP parseSelect(const std::string& selection) const = 0;

    // Convenience to get all fields
    DocumentUP getDocument(search::DocumentIdT lid, const document::DocumentId& docId) const;
};

class DocumentRetrieverBaseForTest : public IDocumentRetriever {
public:
    void visitDocuments(const LidVector& lids, search::IDocumentVisitor& visitor,
                        ReadConsistency readConsistency) const override;
    ReadGuard getReadGuard() const override { return ReadGuard(); }
    uint32_t getDocIdLimit() const override { return std::numeric_limits<uint32_t>::max(); }
};

} // namespace proton
