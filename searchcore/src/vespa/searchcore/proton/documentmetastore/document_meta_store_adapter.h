// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_document_meta_store.h"

namespace proton {

/**
 * Class that maps functions in IDocumentMetaStore that also are found
 * in search::AttributeVector to functions that DocumentMetaStore can implement.
 */
class DocumentMetaStoreAdapter : public IDocumentMetaStore
{
protected:
    virtual void doCommit(const CommitParam & param) = 0;
    virtual DocId doGetCommittedDocIdLimit() const = 0;
    virtual void doRemoveAllOldGenerations() = 0;
    virtual uint64_t doGetCurrentGeneration() const = 0;
public:
    void commit(const CommitParam & param) override {
        doCommit(param);
    }
    DocId getCommittedDocIdLimit() const override {
        return doGetCommittedDocIdLimit();
    }
    void removeAllOldGenerations() override {
        doRemoveAllOldGenerations();
    }
    uint64_t getCurrentGeneration() const override {
        return doGetCurrentGeneration();
    }
};

} // namespace proton

