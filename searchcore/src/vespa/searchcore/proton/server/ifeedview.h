// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/common/feedtoken.h>
#include <vespa/searchlib/common/serialnum.h>

namespace document { class DocumentTypeRepo; }

namespace search { class IDestructorCallback; }

namespace proton {

class CompactLidSpaceOperation;
class DeleteBucketOperation;
class ISimpleDocumentMetaStore;
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
    typedef std::shared_ptr<IFeedView> SP;

    IFeedView(const IFeedView &) = delete;
    IFeedView & operator = (const IFeedView &) = delete;
    virtual ~IFeedView() { }

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
    virtual void handleMove(const MoveOperation &putOp, std::shared_ptr<search::IDestructorCallback> doneCtx) = 0;
    virtual void heartBeat(search::SerialNum serialNum) = 0;
    virtual void sync() = 0;
    virtual void forceCommit(search::SerialNum serialNum) = 0;
    virtual void handlePruneRemovedDocuments(const PruneRemovedDocumentsOperation & pruneOp) = 0;
    virtual void handleCompactLidSpace(const CompactLidSpaceOperation &op) = 0;
};

} // namespace proton

