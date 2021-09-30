// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "persistencehandler.h"

#include <vespa/log/log.h>
LOG_SETUP(".persistence.persistencehandler");

namespace storage {

PersistenceHandler::PersistenceHandler(vespalib::ISequencedTaskExecutor & sequencedExecutor,
                                      const ServiceLayerComponent & component,
                                      const vespa::config::content::StorFilestorConfig & cfg,
                                      spi::PersistenceProvider& provider,
                                      FileStorHandler& filestorHandler,
                                      BucketOwnershipNotifier & bucketOwnershipNotifier,
                                      FileStorThreadMetrics& metrics)
    : _clock(component.getClock()),
      _env(component, filestorHandler, metrics, provider),
      _processAllHandler(_env, provider),
      _mergeHandler(_env, provider, component.cluster_context(), _clock,
                    cfg.bucketMergeChunkSize,
                    cfg.commonMergeChainOptimalizationMinimumSize),
      _asyncHandler(_env, provider, sequencedExecutor, component.getBucketIdFactory()),
      _splitJoinHandler(_env, provider, bucketOwnershipNotifier, cfg.enableMultibitSplitOptimalization),
      _simpleHandler(_env, provider)
{
}

PersistenceHandler::~PersistenceHandler() = default;

MessageTracker::UP
PersistenceHandler::handleCommandSplitByType(api::StorageCommand& msg, MessageTracker::UP tracker) const
{
    switch (msg.getType().getId()) {
    case api::MessageType::GET_ID:
        return _simpleHandler.handleGet(static_cast<api::GetCommand&>(msg), std::move(tracker));
    case api::MessageType::PUT_ID:
        return _asyncHandler.handlePut(static_cast<api::PutCommand&>(msg), std::move(tracker));
    case api::MessageType::REMOVE_ID:
        return _asyncHandler.handleRemove(static_cast<api::RemoveCommand&>(msg), std::move(tracker));
    case api::MessageType::UPDATE_ID:
        return _asyncHandler.handleUpdate(static_cast<api::UpdateCommand&>(msg), std::move(tracker));
    case api::MessageType::REVERT_ID:
        return _simpleHandler.handleRevert(static_cast<api::RevertCommand&>(msg), std::move(tracker));
    case api::MessageType::CREATEBUCKET_ID:
        return _simpleHandler.handleCreateBucket(static_cast<api::CreateBucketCommand&>(msg), std::move(tracker));
    case api::MessageType::DELETEBUCKET_ID:
        return _simpleHandler.handleDeleteBucket(static_cast<api::DeleteBucketCommand&>(msg), std::move(tracker));
    case api::MessageType::JOINBUCKETS_ID:
        return _splitJoinHandler.handleJoinBuckets(static_cast<api::JoinBucketsCommand&>(msg), std::move(tracker));
    case api::MessageType::SPLITBUCKET_ID:
        return _splitJoinHandler.handleSplitBucket(static_cast<api::SplitBucketCommand&>(msg), std::move(tracker));
       // Depends on iterators
    case api::MessageType::STATBUCKET_ID:
        return _processAllHandler.handleStatBucket(static_cast<api::StatBucketCommand&>(msg), std::move(tracker));
    case api::MessageType::REMOVELOCATION_ID:
        return _processAllHandler.handleRemoveLocation(static_cast<api::RemoveLocationCommand&>(msg), std::move(tracker));
    case api::MessageType::MERGEBUCKET_ID:
        return _mergeHandler.handleMergeBucket(static_cast<api::MergeBucketCommand&>(msg), std::move(tracker));
    case api::MessageType::GETBUCKETDIFF_ID:
        return _mergeHandler.handleGetBucketDiff(static_cast<api::GetBucketDiffCommand&>(msg), std::move(tracker));
    case api::MessageType::APPLYBUCKETDIFF_ID:
        return _mergeHandler.handleApplyBucketDiff(static_cast<api::ApplyBucketDiffCommand&>(msg), std::move(tracker));
    case api::MessageType::SETBUCKETSTATE_ID:
        return _splitJoinHandler.handleSetBucketState(static_cast<api::SetBucketStateCommand&>(msg), std::move(tracker));
    case api::MessageType::INTERNAL_ID:
        switch(static_cast<api::InternalCommand&>(msg).getType()) {
        case GetIterCommand::ID:
            return _simpleHandler.handleGetIter(static_cast<GetIterCommand&>(msg), std::move(tracker));
        case CreateIteratorCommand::ID:
            return _simpleHandler.handleCreateIterator(static_cast<CreateIteratorCommand&>(msg), std::move(tracker));
        case ReadBucketList::ID:
            return _simpleHandler.handleReadBucketList(static_cast<ReadBucketList&>(msg), std::move(tracker));
        case ReadBucketInfo::ID:
            return _simpleHandler.handleReadBucketInfo(static_cast<ReadBucketInfo&>(msg), std::move(tracker));
        case RecheckBucketInfoCommand::ID:
            return _splitJoinHandler.handleRecheckBucketInfo(static_cast<RecheckBucketInfoCommand&>(msg), std::move(tracker));
        case RunTaskCommand::ID:
            return _asyncHandler.handleRunTask(static_cast<RunTaskCommand &>(msg), std::move(tracker));
        default:
            LOG(warning, "Persistence handler received unhandled internal command %s", msg.toString().c_str());
            break;
        }
    default:
        break;
    }
    return MessageTracker::UP();
}

void
PersistenceHandler::handleReply(api::StorageReply& reply) const
{
    switch (reply.getType().getId()) {
    case api::MessageType::GETBUCKETDIFF_REPLY_ID:
        _mergeHandler.handleGetBucketDiffReply(static_cast<api::GetBucketDiffReply&>(reply), _env._fileStorHandler);
        break;
    case api::MessageType::APPLYBUCKETDIFF_REPLY_ID:
        _mergeHandler.handleApplyBucketDiffReply(static_cast<api::ApplyBucketDiffReply&>(reply), _env._fileStorHandler);
        break;
    default:
        break;
    }
}

MessageTracker::UP
PersistenceHandler::processMessage(api::StorageMessage& msg, MessageTracker::UP tracker) const
{
    MBUS_TRACE(msg.getTrace(), 5, "PersistenceHandler: Processing message in persistence layer");

    _env._metrics.operations.inc();
    if (msg.getType().isReply()) {
        try{
            LOG(debug, "Handling reply: %s", msg.toString().c_str());
            LOG(spam, "Message content: %s", msg.toString(true).c_str());
            handleReply(static_cast<api::StorageReply&>(msg));
        } catch (std::exception& e) {
            // It's a reply, so nothing we can do.
            LOG(debug, "Caught exception for %s: %s", msg.toString().c_str(), e.what());
        }
    } else {
        auto & initiatingCommand = static_cast<api::StorageCommand&>(msg);
        try {
            LOG(debug, "Handling command: %s", msg.toString().c_str());
            LOG(spam, "Message content: %s", msg.toString(true).c_str());
            return handleCommandSplitByType(initiatingCommand, std::move(tracker));
        } catch (std::exception& e) {
            LOG(debug, "Caught exception for %s: %s", msg.toString().c_str(), e.what());
            api::StorageReply::SP reply(initiatingCommand.makeReply());
            reply->setResult(api::ReturnCode(api::ReturnCode::INTERNAL_FAILURE, e.what()));
            _env._fileStorHandler.sendReply(reply);
        }
    }

    return tracker;
}

void
PersistenceHandler::processLockedMessage(FileStorHandler::LockedMessage lock) const {
    LOG(debug, "NodeIndex %d, ptr=%p", _env._nodeIndex, lock.second.get());
    api::StorageMessage & msg(*lock.second);

    // Important: we _copy_ the message shared_ptr instead of moving to ensure that `msg` remains
    // valid even if the tracker is destroyed by an exception in processMessage().
    auto tracker = std::make_unique<MessageTracker>(framework::MilliSecTimer(_clock), _env, _env._fileStorHandler, std::move(lock.first), lock.second);
    tracker = processMessage(msg, std::move(tracker));
    if (tracker) {
        tracker->sendReply();
    }
}

}
