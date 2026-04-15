// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    virtual vespalib::Generation doGetCurrentGeneration() const = 0;
public:
    void commit(const CommitParam & param) override {
        doCommit(param);
    }
    DocId getCommittedDocIdLimit() const override {
        return doGetCommittedDocIdLimit();
    }
    void reclaim_unused_memory() override {
        doRemoveAllOldGenerations();
    }
    vespalib::Generation getCurrentGeneration() const override {
        return doGetCurrentGeneration();
    }
};

} // namespace proton

