// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::api::MessageHandler
 * @ingroup messageapi
 *
 * @brief Class to prevent manual casting and switches of message types.
 *
 * MessageHandler defines an interface for processing StorageMessage objects
 * of various subclasses.
 *
 * @version $Id$
 */

#pragma once

#include <memory>

namespace storage {
namespace api {

// Commands

class GetCommand; // Retrieve document
class PutCommand; // Add document
class UpdateCommand; // Update document
class RemoveCommand; // Remove document
class RevertCommand; // Revert put/remove operation
class BatchPutRemoveCommand;
class BatchDocumentUpdateCommand;

class CreateVisitorCommand; // Create a new visitor
class DestroyVisitorCommand; // Destroy a running visitor
class VisitorInfoCommand; // Sends visitor info to visitor controller
class MapVisitorCommand;
class SearchResultCommand;
class DocumentSummaryCommand;
class QueryResultCommand;

class InternalCommand;

class CreateBucketCommand;
class DeleteBucketCommand;
class MergeBucketCommand;
class GetBucketDiffCommand;
class ApplyBucketDiffCommand;
class SplitBucketCommand;
class JoinBucketsCommand;
class SetBucketStateCommand;

class RequestBucketInfoCommand;
class NotifyBucketChangeCommand;
class SetNodeStateCommand;
class GetNodeStateCommand;
class SetSystemStateCommand;
class GetSystemStateCommand;
class GetBucketNodesCommand;
class BucketsAddedCommand;
class BucketsRemovedCommand;

// Replies

class GetReply;
class PutReply;
class UpdateReply;
class RemoveReply;
class RevertReply;
class BatchPutRemoveReply;
class BatchDocumentUpdateReply;

class CreateVisitorReply;
class DestroyVisitorReply;
class VisitorInfoReply;
class MapVisitorReply;
class SearchResultReply;
class DocumentSummaryReply;
class QueryResultReply;

class InternalReply;

class CreateBucketReply;
class DeleteBucketReply;
class MergeBucketReply;
class GetBucketDiffReply;
class ApplyBucketDiffReply;
class SplitBucketReply;
class JoinBucketsReply;
class SetBucketStateReply;

class RequestBucketInfoReply;
class NotifyBucketChangeReply;
class SetNodeStateReply;
class GetNodeStateReply;
class SetSystemStateReply;
class GetSystemStateReply;
class GetBucketNodesReply;
class BucketsAddedReply;
class BucketsRemovedReply;

class StatBucketCommand;
class StatBucketReply;
class GetBucketListCommand;
class GetBucketListReply;

class EmptyBucketsCommand;
class EmptyBucketsReply;

class RemoveLocationCommand;
class RemoveLocationReply;

#define _INTERNAL_DEF_ON_MC(m, c) bool m(const std::shared_ptr<storage::api::c> & ) override
#define _INTERNAL_DEF_IMPL_ON_MC(m, c) bool m(const std::shared_ptr<storage::api::c> & ) override { return false; }
#define _INTERNAL_IMPL_ON_MC(cl, m, c, p) bool cl::m(const std::shared_ptr<storage::api::c> & p)
#define DEF_IMPL_MSG_COMMAND_H(m) _INTERNAL_DEF_IMPL_ON_MC(on##m, m##Command)
#define DEF_IMPL_MSG_REPLY_H(m) _INTERNAL_DEF_IMPL_ON_MC(on##m##Reply, m##Reply)
#define DEF_MSG_COMMAND_H(m) _INTERNAL_DEF_ON_MC(on##m, m##Command)
#define DEF_MSG_REPLY_H(m) _INTERNAL_DEF_ON_MC(on##m##Reply, m##Reply)
#define IMPL_MSG_COMMAND_ARG_H(cl, m, p) _INTERNAL_IMPL_ON_MC(cl, on##m, m##Command, p)
#define IMPL_MSG_REPLY_ARG_H(cl, m, p) _INTERNAL_IMPL_ON_MC(cl, on##m##Reply, m##Reply, p)
#define IMPL_MSG_COMMAND_H(cl, m) IMPL_MSG_COMMAND_ARG_H(cl, m, cmd)
#define IMPL_MSG_REPLY_H(cl, m) IMPL_MSG_REPLY_ARG_H(cl, m, reply)
#define ON_M(m) DEF_IMPL_MSG_COMMAND_H(m); DEF_IMPL_MSG_REPLY_H(m)

class MessageHandler {
public:
        // Basic operations
    virtual bool onGet(const std::shared_ptr<api::GetCommand>&)
        { return false; }
    virtual bool onGetReply(const std::shared_ptr<api::GetReply>&)
        { return false; }
    virtual bool onPut(const std::shared_ptr<api::PutCommand>&)
        { return false; }
    virtual bool onPutReply(const std::shared_ptr<api::PutReply>&)
        { return false; }
    virtual bool onUpdate(const std::shared_ptr<api::UpdateCommand>&)
        { return false; }
    virtual bool onUpdateReply(const std::shared_ptr<api::UpdateReply>&)
        { return false; }
    virtual bool onRemove(const std::shared_ptr<api::RemoveCommand>&)
        { return false; }
    virtual bool onRemoveReply(const std::shared_ptr<api::RemoveReply>&)
        { return false; }
    virtual bool onRevert(const std::shared_ptr<api::RevertCommand>&)
        { return false; }
    virtual bool onRevertReply(const std::shared_ptr<api::RevertReply>&)
        { return false; }
    virtual bool onBatchPutRemove(
            const std::shared_ptr<api::BatchPutRemoveCommand>&)
        { return false; }
    virtual bool onBatchPutRemoveReply(
            const std::shared_ptr<api::BatchPutRemoveReply>&)
        { return false; }
    virtual bool onBatchDocumentUpdate(
            const std::shared_ptr<api::BatchDocumentUpdateCommand>&)
        { return false; }
    virtual bool onBatchDocumentUpdateReply(
            const std::shared_ptr<api::BatchDocumentUpdateReply>&)
        { return false; }

        // Visiting
    virtual bool onCreateVisitor(
            const std::shared_ptr<api::CreateVisitorCommand>&)
        { return false; }
    virtual bool onCreateVisitorReply(
            const std::shared_ptr<api::CreateVisitorReply>&)
        { return false; }
    virtual bool onDestroyVisitor(
            const std::shared_ptr<api::DestroyVisitorCommand>&)
        { return false; }
    virtual bool onDestroyVisitorReply(
            const std::shared_ptr<api::DestroyVisitorReply>&)
        { return false; }
    virtual bool onVisitorInfo(
            const std::shared_ptr<api::VisitorInfoCommand>&)
        { return false; }
    virtual bool onVisitorInfoReply(
            const std::shared_ptr<api::VisitorInfoReply>&)
        { return false; }
    virtual bool onMapVisitor(
            const std::shared_ptr<api::MapVisitorCommand>&)
        { return false; }
    virtual bool onMapVisitorReply(
            const std::shared_ptr<api::MapVisitorReply>&)
        { return false; }
    virtual bool onSearchResult(
            const std::shared_ptr<api::SearchResultCommand>&)
        { return false; }
    virtual bool onSearchResultReply(
            const std::shared_ptr<api::SearchResultReply>&)
        { return false; }
    virtual bool onQueryResult(
            const std::shared_ptr<api::QueryResultCommand>&)
        { return false; }
    virtual bool onQueryResultReply(
            const std::shared_ptr<api::QueryResultReply>&)
        { return false; }
    virtual bool onDocumentSummary(
            const std::shared_ptr<api::DocumentSummaryCommand>&)
        { return false; }
    virtual bool onDocumentSummaryReply(
            const std::shared_ptr<api::DocumentSummaryReply>&)
        { return false; }
    virtual bool onEmptyBuckets(
            const std::shared_ptr<api::EmptyBucketsCommand>&)
        { return false; }
    virtual bool onEmptyBucketsReply(
            const std::shared_ptr<api::EmptyBucketsReply>&)
        { return false; }


    virtual bool onInternal(const std::shared_ptr<api::InternalCommand>&)
        { return false; }
    virtual bool onInternalReply(
            const std::shared_ptr<api::InternalReply>&)
        { return false; }

    virtual bool onCreateBucket(
            const std::shared_ptr<api::CreateBucketCommand>&)
        { return false; }
    virtual bool onCreateBucketReply(
            const std::shared_ptr<api::CreateBucketReply>&)
        { return false; }
    virtual bool onDeleteBucket(
            const std::shared_ptr<api::DeleteBucketCommand>&)
        { return false; }
    virtual bool onDeleteBucketReply(
            const std::shared_ptr<api::DeleteBucketReply>&)
        { return false; }
    virtual bool onMergeBucket(
            const std::shared_ptr<api::MergeBucketCommand>&)
        { return false; }
    virtual bool onMergeBucketReply(
            const std::shared_ptr<api::MergeBucketReply>&)
        { return false; }
    virtual bool onGetBucketDiff(
            const std::shared_ptr<api::GetBucketDiffCommand>&)
        { return false; }
    virtual bool onGetBucketDiffReply(
            const std::shared_ptr<api::GetBucketDiffReply>&)
        { return false; }
    virtual bool onApplyBucketDiff(
            const std::shared_ptr<api::ApplyBucketDiffCommand>&)
        { return false; }
    virtual bool onApplyBucketDiffReply(
            const std::shared_ptr<api::ApplyBucketDiffReply>&)
        { return false; }
    virtual bool onSplitBucket(
            const std::shared_ptr<api::SplitBucketCommand>&)
        { return false; }
    virtual bool onSplitBucketReply(
            const std::shared_ptr<api::SplitBucketReply>&)
        { return false; }
    virtual bool onJoinBuckets(
            const std::shared_ptr<api::JoinBucketsCommand>&)
        { return false; }
    virtual bool onJoinBucketsReply(
            const std::shared_ptr<api::JoinBucketsReply>&)
        { return false; }
    virtual bool onSetBucketState(
            const std::shared_ptr<api::SetBucketStateCommand>&)
        { return false; }
    virtual bool onSetBucketStateReply(
            const std::shared_ptr<api::SetBucketStateReply>&)
        { return false; }

    virtual bool onRequestBucketInfo(
            const std::shared_ptr<api::RequestBucketInfoCommand>&)
        { return false; }
    virtual bool onRequestBucketInfoReply(
            const std::shared_ptr<api::RequestBucketInfoReply>&)
        { return false; }
    virtual bool onNotifyBucketChange(
            const std::shared_ptr<api::NotifyBucketChangeCommand>&)
        { return false; }
    virtual bool onNotifyBucketChangeReply(
            const std::shared_ptr<api::NotifyBucketChangeReply>&)
        { return false; }
    virtual bool onSetNodeState(
            const std::shared_ptr<api::SetNodeStateCommand>&)
        { return false; }
    virtual bool onSetNodeStateReply(
            const std::shared_ptr<api::SetNodeStateReply>&)
        { return false; }
    virtual bool onGetNodeState(
            const std::shared_ptr<api::GetNodeStateCommand>&)
        { return false; }
    virtual bool onGetNodeStateReply(
            const std::shared_ptr<api::GetNodeStateReply>&)
        { return false; }
    virtual bool onSetSystemState(
            const std::shared_ptr<api::SetSystemStateCommand>&)
        { return false; }
    virtual bool onSetSystemStateReply(
            const std::shared_ptr<api::SetSystemStateReply>&)
        { return false; }
    virtual bool onGetSystemState(
            const std::shared_ptr<api::GetSystemStateCommand>&)
        { return false; }
    virtual bool onGetSystemStateReply(
            const std::shared_ptr<api::GetSystemStateReply>&)
        { return false; }
    virtual bool onBucketsAdded(
            const std::shared_ptr<api::BucketsAddedCommand>&)
        { return false; }
    virtual bool onBucketsAddedReply(
            const std::shared_ptr<api::BucketsAddedReply>&)
        { return false; }
    virtual bool onBucketsRemoved(
            const std::shared_ptr<api::BucketsRemovedCommand>&)
        { return false; }
    virtual bool onBucketsRemovedReply(
            const std::shared_ptr<api::BucketsRemovedReply>&)
        { return false; }

    virtual bool onStatBucket(
            const std::shared_ptr<api::StatBucketCommand>&)
        { return false; }
    virtual bool onStatBucketReply(
            const std::shared_ptr<api::StatBucketReply>&)
        { return false; }

    virtual bool onGetBucketList(
            const std::shared_ptr<api::GetBucketListCommand>&)
        { return false; }
    virtual bool onGetBucketListReply(
            const std::shared_ptr<api::GetBucketListReply>&)
        { return false; }

    virtual bool onRemoveLocation(
            const std::shared_ptr<api::RemoveLocationCommand>&)
        { return false; }

    virtual bool onRemoveLocationReply(
            const std::shared_ptr<api::RemoveLocationReply>&)
        { return false; }

    virtual ~MessageHandler() {}
};

#undef ON_M

} // api
} // storage
