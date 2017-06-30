// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    virtual const document::DocumentTypeRepo::SP &getDocumentTypeRepo() const override {
        return _docTypeRepo;
    }
    virtual const ISimpleDocumentMetaStore *getDocumentMetaStorePtr() const override {
        return std::nullptr_t();
    }
    virtual void preparePut(PutOperation &) override {}
    virtual void handlePut(FeedToken *,
                           const PutOperation &) override {}
    virtual void prepareUpdate(UpdateOperation &) override {}
    virtual void handleUpdate(FeedToken *,
                              const UpdateOperation &) override {}
    virtual void prepareRemove(RemoveOperation &) override {}
    virtual void handleRemove(FeedToken *,
                              const RemoveOperation &) override {}
    virtual void prepareDeleteBucket(DeleteBucketOperation &) override {}
    virtual void handleDeleteBucket(const DeleteBucketOperation &) override {}
    virtual void prepareMove(MoveOperation &) override {}
    virtual void handleMove(const MoveOperation &, std::shared_ptr<search::IDestructorCallback>) override {}
    virtual void heartBeat(search::SerialNum) override {}
    virtual void sync() override {}
    virtual void handlePruneRemovedDocuments(const PruneRemovedDocumentsOperation &) override {}
    virtual void handleCompactLidSpace(const CompactLidSpaceOperation &) override {}
    void forceCommit(search::SerialNum) override { }
};

} // namespace test

} // namespace proton

