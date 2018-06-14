// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "ifeedview.h"
#include "replaypacketdispatcher.h"
#include "ibucketstatecalculator.h"
#include <vespa/searchcore/proton/common/feedtoken.h>
#include <vespa/searchlib/common/serialnum.h>

namespace proton {

class DocumentOperation;

class CombiningFeedView : public IFeedView
{
private:
    const std::shared_ptr<const document::DocumentTypeRepo>          _repo;
    std::vector<IFeedView::SP>                    _views;
    std::vector<const ISimpleDocumentMetaStore *> _metaStores;
    IBucketStateCalculator::SP                    _calc;
    bool                                          _clusterUp;
    bool                                          _forceReady;
    document::BucketSpace                         _bucketSpace;

    const ISimpleDocumentMetaStore * getDocumentMetaStorePtr() const override;

    void findPrevDbdId(const document::GlobalId &gid, DocumentOperation &op);
    uint32_t getReadyFeedViewId() const { return 0u; }
    uint32_t getRemFeedViewId() const { return 1u; }
    uint32_t getNotReadyFeedViewId() const { return 2u; }

    IFeedView * getReadyFeedView() {
        return _views[getReadyFeedViewId()].get();
    }

    IFeedView * getRemFeedView() {
        return _views[getRemFeedViewId()].get();
    }

    IFeedView * getNotReadyFeedView() {
        return _views[getNotReadyFeedViewId()].get();
    }

    bool hasNotReadyFeedView() const {
        return _views.size() > getNotReadyFeedViewId();
    }

    bool shouldBeReady(const document::BucketId &bucket) const;
    void forceCommit(search::SerialNum serialNum) override;
public:
    typedef std::shared_ptr<CombiningFeedView> SP;

    CombiningFeedView(const std::vector<IFeedView::SP> &views,
                      document::BucketSpace bucketSpace,
                      const IBucketStateCalculator::SP &calc);

    ~CombiningFeedView() override;

    const std::shared_ptr<const document::DocumentTypeRepo> & getDocumentTypeRepo() const override;

    /**
     * Similar to IPersistenceHandler functions.
     */

    void preparePut(PutOperation &putOp) override;
    void handlePut(FeedToken token, const PutOperation &putOp) override;
    void prepareUpdate(UpdateOperation &updOp) override;
    void handleUpdate(FeedToken token, const UpdateOperation &updOp) override;
    void prepareRemove(RemoveOperation &rmOp) override;
    void handleRemove(FeedToken token, const RemoveOperation &rmOp) override;
    void prepareDeleteBucket(DeleteBucketOperation &delOp) override;
    void handleDeleteBucket(const DeleteBucketOperation &delOp) override;
    void prepareMove(MoveOperation &putOp) override;
    void handleMove(const MoveOperation &moveOp, std::shared_ptr<search::IDestructorCallback> moveDoneCtx) override;
    void heartBeat(search::SerialNum serialNum) override;
    void sync() override;
    void handlePruneRemovedDocuments(const PruneRemovedDocumentsOperation &pruneOp) override;
    void handleCompactLidSpace(const CompactLidSpaceOperation &op) override;

    // Called by document db executor
    void setCalculator(const IBucketStateCalculator::SP &newCalc);
};

} // namespace proton

