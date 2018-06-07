// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/persistenceengine/i_document_retriever.h>
#include <vespa/searchcore/proton/server/icommitable.h>
#include <vespa/document/fieldvalue/document.h>

namespace proton {

/*
 * Wrapper class for document retriever that performs commit and waits for
 * it to complete before retrieving document. This is used to ensure that
 * attribute vectors are committed before we read from them.
 */
class CommitAndWaitDocumentRetriever : public IDocumentRetriever
{
    IDocumentRetriever::SP _retriever;
    ICommitable           &_commit;
    using Bucket = storage::spi::Bucket;
public:
    CommitAndWaitDocumentRetriever(const IDocumentRetriever::SP &retriever, ICommitable &commit)
        : _retriever(retriever),
          _commit(commit)
    { }

    ~CommitAndWaitDocumentRetriever() {}

    const document::DocumentTypeRepo &getDocumentTypeRepo() const override {
        return _retriever->getDocumentTypeRepo();
    }

    void getBucketMetaData(const Bucket &bucket, search::DocumentMetaData::Vector &result) const override {
        return _retriever->getBucketMetaData(bucket, result);
    }

    search::DocumentMetaData getDocumentMetaData(const document::DocumentId &id) const override {
        return _retriever->getDocumentMetaData(id);
    }
    document::Document::UP getDocument(search::DocumentIdT lid) const override {
        // Ensure that attribute vectors are committed
        _commit.commitAndWait();
        return _retriever->getDocument(lid);
    }
    void visitDocuments(const LidVector &lids, search::IDocumentVisitor &visitor,
                        ReadConsistency readConsistency) const override
    {
        _commit.commitAndWait();
        _retriever->visitDocuments(lids, visitor, readConsistency);
    }

    CachedSelect::SP parseSelect(const vespalib::string &selection) const override {
        return _retriever->parseSelect(selection);
    }
    ReadGuard getReadGuard() const override {
        return _retriever->getReadGuard();
    }
    uint32_t getDocIdLimit() const override {
        return _retriever->getDocIdLimit();
    }
};

} // namespace proton
