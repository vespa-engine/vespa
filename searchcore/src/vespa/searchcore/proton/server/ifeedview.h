// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/common/feedtoken.h>
#include <vespa/searchlib/common/commit_param.h>

namespace document { class DocumentTypeRepo; }

namespace vespalib { class IDestructorCallback; }

namespace proton {

class CompactLidSpaceOperation;
class DeleteBucketOperation;
struct ISimpleDocumentMetaStore;
class MoveOperation;
class PruneRemovedDocumentsOperation;
class PutOperation;
class RemoveOperation;
class UpdateOperation;

/**
 * Interface for a feed view as seen from a feed handler.
 */
class IFeedView
{
protected:
    IFeedView() = default;
public:
    using SP = std::shared_ptr<IFeedView>;
    using DoneCallback = std::shared_ptr<vespalib::IDestructorCallback>;
    using CommitParam = search::CommitParam;

    IFeedView(const IFeedView &) = delete;
    IFeedView & operator = (const IFeedView &) = delete;
    virtual ~IFeedView() = default;

    virtual const std::shared_ptr<const document::DocumentTypeRepo> &getDocumentTypeRepo() const = 0;

    /**
     * Access to const version of document meta store.
     * Should only be used by the writer thread.
     */
    virtual const ISimpleDocumentMetaStore * getDocumentMetaStorePtr() const = 0;

    /**
     * Similar to IPersistenceHandler functions.
     */

    virtual void preparePut(PutOperation &putOp) = 0;
    virtual void handlePut(FeedToken token, const PutOperation &putOp) = 0;
    virtual void prepareUpdate(UpdateOperation &updOp) = 0;
    virtual void handleUpdate(FeedToken token, const UpdateOperation &updOp) = 0;
    virtual void prepareRemove(RemoveOperation &rmOp) = 0;
    virtual void handleRemove(FeedToken token, const RemoveOperation &rmOp) = 0;
    virtual void prepareDeleteBucket(DeleteBucketOperation &delOp) = 0;
    virtual void handleDeleteBucket(const DeleteBucketOperation &delOp) = 0;
    virtual void prepareMove(MoveOperation &putOp) = 0;
    virtual void handleMove(const MoveOperation &putOp, DoneCallback onDone) = 0;
    virtual void heartBeat(search::SerialNum serialNum) = 0;
    virtual void sync() = 0;
    virtual void forceCommit(const CommitParam & param, DoneCallback onDone) = 0;
    void forceCommit(CommitParam param) { forceCommit(param, DoneCallback()); }
    void forceCommit(search::SerialNum serialNum) { forceCommit(CommitParam(serialNum)); }
    virtual void handlePruneRemovedDocuments(const PruneRemovedDocumentsOperation & pruneOp) = 0;
    virtual void handleCompactLidSpace(const CompactLidSpaceOperation &op) = 0;
};

} // namespace proton

