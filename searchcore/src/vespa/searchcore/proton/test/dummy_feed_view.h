// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcore/proton/server/ifeedview.h>

namespace proton::test {

struct DummyFeedView : public IFeedView
{
    std::shared_ptr<const document::DocumentTypeRepo> _docTypeRepo;

    DummyFeedView();
    explicit DummyFeedView(std::shared_ptr<const document::DocumentTypeRepo> docTypeRepo);
    ~DummyFeedView() override;
    const std::shared_ptr<const document::DocumentTypeRepo> &getDocumentTypeRepo() const override {
        return _docTypeRepo;
    }
    const ISimpleDocumentMetaStore *getDocumentMetaStorePtr() const override {
        return nullptr;
    }
    void preparePut(PutOperation &) override {}
    void handlePut(FeedToken, const PutOperation &) override {}
    void prepareUpdate(UpdateOperation &) override {}
    void handleUpdate(FeedToken, const UpdateOperation &) override {}
    void prepareRemove(RemoveOperation &) override {}
    void handleRemove(FeedToken, const RemoveOperation &) override {}
    void prepareDeleteBucket(DeleteBucketOperation &) override {}
    void handleDeleteBucket(const DeleteBucketOperation &) override {}
    void prepareMove(MoveOperation &) override {}
    void handleMove(const MoveOperation &, std::shared_ptr<search::IDestructorCallback>) override {}
    void heartBeat(search::SerialNum) override {}
    void sync() override {}
    void handlePruneRemovedDocuments(const PruneRemovedDocumentsOperation &) override {}
    void handleCompactLidSpace(const CompactLidSpaceOperation &) override {}
    void forceCommit(search::SerialNum, DoneCallback) override { }
    ILidCommitState & getUncommittedLidsTracker() override;
    bool isDrained() const override { return true; }
};

}
