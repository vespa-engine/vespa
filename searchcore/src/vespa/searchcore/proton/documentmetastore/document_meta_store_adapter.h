// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    virtual void doCommit(search::SerialNum firstSerialNum,
                          search::SerialNum lastSerialNum) = 0;
    virtual DocId doGetCommittedDocIdLimit() const = 0;
    virtual void doRemoveAllOldGenerations() = 0;

public:
    virtual void commit(search::SerialNum firstSerialNum,
                        search::SerialNum lastSerialNum) override {
        doCommit(firstSerialNum, lastSerialNum);
    }
    virtual DocId getCommittedDocIdLimit() const override {
        return doGetCommittedDocIdLimit();
    }
    virtual void removeAllOldGenerations() override {
        doRemoveAllOldGenerations();
    }
};

} // namespace proton

