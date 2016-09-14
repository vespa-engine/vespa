// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/searchcore/proton/documentmetastore/i_simple_document_meta_store.h>
#include <vespa/searchcore/proton/common/feedtoken.h>
#include <vespa/searchcore/proton/feedoperation/compact_lid_space_operation.h>
#include <vespa/searchcore/proton/feedoperation/deletebucketoperation.h>
#include <vespa/searchcore/proton/feedoperation/joinbucketsoperation.h>
#include <vespa/searchcore/proton/feedoperation/pruneremoveddocumentsoperation.h>
#include <vespa/searchcore/proton/feedoperation/putoperation.h>
#include <vespa/searchcore/proton/feedoperation/removeoperation.h>
#include <vespa/searchcore/proton/feedoperation/splitbucketoperation.h>
#include <vespa/searchcore/proton/feedoperation/updateoperation.h>
#include <vespa/searchlib/transactionlog/common.h>

namespace proton
{

class MoveOperation;

/**
 * Interface for a feed view as seen from a feed handler.
 */
class IFeedView
{
protected:
    typedef search::transactionlog::Packet Packet;
    IFeedView() = default;
public:
    typedef std::shared_ptr<IFeedView> SP;

    IFeedView(const IFeedView &) = delete;
    IFeedView & operator = (const IFeedView &) = delete;
    virtual ~IFeedView() { }

    virtual const document::DocumentTypeRepo::SP &getDocumentTypeRepo() const = 0;

    /**
     * Access to const version of document meta store.
     * Should only be used by the writer thread.
     */
    virtual const ISimpleDocumentMetaStore * getDocumentMetaStorePtr() const = 0;

    /**
     * Similar to IPersistenceHandler functions.
     */

    virtual void preparePut(PutOperation &putOp) = 0;
    virtual void handlePut(FeedToken *token, const PutOperation &putOp) = 0;
    virtual void prepareUpdate(UpdateOperation &updOp) = 0;
    virtual void handleUpdate(FeedToken *token, const UpdateOperation &updOp) = 0;
    virtual void prepareRemove(RemoveOperation &rmOp) = 0;
    virtual void handleRemove(FeedToken *token, const RemoveOperation &rmOp) = 0;
    virtual void prepareDeleteBucket(DeleteBucketOperation &delOp) = 0;
    virtual void handleDeleteBucket(const DeleteBucketOperation &delOp) = 0;
    virtual void prepareMove(MoveOperation &putOp) = 0;
    virtual void handleMove(const MoveOperation &putOp) = 0;
    virtual void heartBeat(search::SerialNum serialNum) = 0;
    virtual void sync() = 0;
    virtual void forceCommit(search::SerialNum serialNum) = 0;
    virtual void handlePruneRemovedDocuments(const PruneRemovedDocumentsOperation & pruneOp) = 0;
    virtual void handleCompactLidSpace(const CompactLidSpaceOperation &op) = 0;
};

} // namespace proton

