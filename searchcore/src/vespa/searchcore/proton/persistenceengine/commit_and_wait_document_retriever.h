// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_document_retriever.h"
#include <vespa/searchcore/proton/common/ipendinglidtracker.h>

namespace proton {

/*
 * Wrapper class for document retriever that performs commit and waits for
 * it to complete before retrieving document. This is used to ensure that
 * attribute vectors are committed before we read from them.
 */
class CommitAndWaitDocumentRetriever : public IDocumentRetriever
{
    IDocumentRetriever::SP _retriever;
    ILidCommitState       &_uncommittedLidsTracker;
    using Bucket = storage::spi::Bucket;
public:
    CommitAndWaitDocumentRetriever(IDocumentRetriever::SP retriever, ILidCommitState & unCommittedLidTracker);
    ~CommitAndWaitDocumentRetriever() override;

    const document::DocumentTypeRepo &getDocumentTypeRepo() const override;
    void getBucketMetaData(const Bucket &bucket, search::DocumentMetaData::Vector &result) const override;
    search::DocumentMetaData getDocumentMetaData(const document::DocumentId &id) const override;
    DocumentUP getFullDocument(search::DocumentIdT lid) const override;
    DocumentUP getPartialDocument(search::DocumentIdT lid, const document::DocumentId & docId, const document::FieldSet & fieldSet) const override;
    void visitDocuments(const LidVector &lids, search::IDocumentVisitor &visitor, ReadConsistency readConsistency) const override;
    CachedSelect::SP parseSelect(const vespalib::string &selection) const override;
    ReadGuard getReadGuard() const override;
    uint32_t getDocIdLimit() const override;
};

} // namespace proton
