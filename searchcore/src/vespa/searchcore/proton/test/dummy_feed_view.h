// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/server/ifeedview.h>
#include <vespa/document/repo/documenttyperepo.h>

namespace proton {

namespace test {

struct DummyFeedView : public IFeedView
{
    document::DocumentTypeRepo::SP _docTypeRepo;

    DummyFeedView()
        : _docTypeRepo()
    {}
    DummyFeedView(const document::DocumentTypeRepo::SP &docTypeRepo)
        : _docTypeRepo(docTypeRepo)
    {}
    virtual const document::DocumentTypeRepo::SP &getDocumentTypeRepo() const {
        return _docTypeRepo;
    }
    virtual const ISimpleDocumentMetaStore *getDocumentMetaStorePtr() const {
        return std::nullptr_t();
    }
    virtual void preparePut(PutOperation &) {}
    virtual void handlePut(FeedToken *,
                           const PutOperation &) {}
    virtual void prepareUpdate(UpdateOperation &) {}
    virtual void handleUpdate(FeedToken *,
                              const UpdateOperation &) {}
    virtual void prepareRemove(RemoveOperation &) {}
    virtual void handleRemove(FeedToken *,
                              const RemoveOperation &) {}
    virtual void prepareDeleteBucket(DeleteBucketOperation &) {}
    virtual void handleDeleteBucket(const DeleteBucketOperation &) {}
    virtual void prepareMove(MoveOperation &) {}
    virtual void handleMove(const MoveOperation &) {}
    virtual void heartBeat(search::SerialNum) {}
    virtual void sync() {}
    virtual void handlePruneRemovedDocuments(const PruneRemovedDocumentsOperation &) {}
    virtual void handleCompactLidSpace(const CompactLidSpaceOperation &) {}
    void forceCommit(search::SerialNum) override { }
};

} // namespace test

} // namespace proton

