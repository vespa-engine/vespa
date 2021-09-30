// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "commit_and_wait_document_retriever.h"
#include <vespa/document/fieldvalue/document.h>

namespace proton {

CommitAndWaitDocumentRetriever::CommitAndWaitDocumentRetriever(IDocumentRetriever::SP retriever,
                                                               ILidCommitState & unCommittedLidTracker)
    : _retriever(std::move(retriever)),
      _uncommittedLidsTracker(unCommittedLidTracker)
{ }

CommitAndWaitDocumentRetriever::~CommitAndWaitDocumentRetriever() = default;

const document::DocumentTypeRepo &
CommitAndWaitDocumentRetriever::getDocumentTypeRepo() const {
    return _retriever->getDocumentTypeRepo();
}

void
CommitAndWaitDocumentRetriever::getBucketMetaData(const Bucket &bucket, search::DocumentMetaData::Vector &result) const {
    return _retriever->getBucketMetaData(bucket, result);
}

search::DocumentMetaData
CommitAndWaitDocumentRetriever::getDocumentMetaData(const document::DocumentId &id) const {
    return _retriever->getDocumentMetaData(id);
}

document::Document::UP
CommitAndWaitDocumentRetriever::getFullDocument(search::DocumentIdT lid) const {
    // Ensure that attribute vectors are committed
    _uncommittedLidsTracker.waitComplete(lid);
    return _retriever->getFullDocument(lid);
}

document::Document::UP
CommitAndWaitDocumentRetriever::getPartialDocument(search::DocumentIdT lid, const document::DocumentId & docId,
                                                   const document::FieldSet & fieldSet) const
{
    _uncommittedLidsTracker.waitComplete(lid);
    return _retriever->getPartialDocument(lid, docId, fieldSet);
}

void
CommitAndWaitDocumentRetriever::visitDocuments(const LidVector &lids, search::IDocumentVisitor &visitor,
                                               ReadConsistency readConsistency) const
{
    _uncommittedLidsTracker.waitComplete(lids);
    _retriever->visitDocuments(lids, visitor, readConsistency);
}

CachedSelect::SP
CommitAndWaitDocumentRetriever::parseSelect(const vespalib::string &selection) const {
    return _retriever->parseSelect(selection);
}

IDocumentRetriever::ReadGuard
CommitAndWaitDocumentRetriever::getReadGuard() const {
    return _retriever->getReadGuard();
}

uint32_t
CommitAndWaitDocumentRetriever::getDocIdLimit() const {
    return _retriever->getDocIdLimit();
}

} // namespace proton
