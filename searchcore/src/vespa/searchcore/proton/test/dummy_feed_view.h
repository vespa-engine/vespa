// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    void handleDeleteBucket(const DeleteBucketOperation &, const DoneCallback&) override {}
    bool isMoveStillValid(const MoveOperation &) const override { return true; }
    void prepareMove(MoveOperation &) override {}
    void handleMove(const MoveOperation &, const DoneCallback&) override {}
    void heartBeat(search::SerialNum, const DoneCallback&) override {}
    void handlePruneRemovedDocuments(const PruneRemovedDocumentsOperation &, const DoneCallback&) override {}
    void handleCompactLidSpace(const CompactLidSpaceOperation &, const DoneCallback&) override {}
    void forceCommit(const CommitParam &, const DoneCallback&) override { }
};

}
