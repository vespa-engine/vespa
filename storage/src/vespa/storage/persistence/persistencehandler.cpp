// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "persistencehandler.h"

#include <vespa/log/log.h>
LOG_SETUP(".persistence.persistencehandler");

using vespalib::CpuUsage;

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
      _mergeHandler(_env, provider, component.cluster_context(), _clock, sequencedExecutor,
                    cfg.bucketMergeChunkSize,
                    cfg.commonMergeChainOptimalizationMinimumSize),
      _asyncHandler(_env, provider, bucketOwnershipNotifier, sequencedExecutor, component.getBucketIdFactory()),
      _splitJoinHandler(_env, provider, bucketOwnershipNotifier, cfg.enableMultibitSplitOptimalization),
      _simpleHandler(_env, provider, component.getBucketIdFactory())
{
}

PersistenceHandler::~PersistenceHandler() = default;

// Guard that allows an operation that may be executed in an async fashion to
// be explicitly notified when the sync phase of the operation is done, i.e.
// when the persistence thread is no longer working on it. An operation that
// does not care about such notifications can safely return a nullptr notifier,
// in which case the guard is a no-op.
class OperationSyncPhaseTrackingGuard {
    std::shared_ptr<FileStorHandler::OperationSyncPhaseDoneNotifier> _maybe_notifier;
public:
    explicit OperationSyncPhaseTrackingGuard(const MessageTracker& tracker)
        : _maybe_notifier(tracker.sync_phase_done_notifier_or_nullptr())
    {}

    ~OperationSyncPhaseTrackingGuard() {
        if (_maybe_notifier) {
            _maybe_notifier->signal_operation_sync_phase_done();
        }
    }
};

MessageTracker::UP
PersistenceHandler::handleCommandSplitByType(api::StorageCommand& msg, MessageTracker::UP tracker) const
{
    OperationSyncPhaseTrackingGuard sync_guard(*tracker);
    switch (msg.getType().getId()) {
    case api::MessageType::GET_ID:
    {
        auto usage = vespalib::CpuUsage::use(CpuUsage::Category::READ);
        return _simpleHandler.handleGet(static_cast<api::GetCommand&>(msg), std::move(tracker));
    }
    case api::MessageType::PUT_ID:
        return _asyncHandler.handlePut(static_cast<api::PutCommand&>(msg), std::move(tracker));
    case api::MessageType::REMOVE_ID:
        return _asyncHandler.handleRemove(static_cast<api::RemoveCommand&>(msg), std::move(tracker));
    case api::MessageType::UPDATE_ID:
        return _asyncHandler.handleUpdate(static_cast<api::UpdateCommand&>(msg), std::move(tracker));
    case api::MessageType::REVERT_ID:
        return _simpleHandler.handleRevert(static_cast<api::RevertCommand&>(msg), std::move(tracker));
    case api::MessageType::CREATEBUCKET_ID:
        return _asyncHandler.handleCreateBucket(static_cast<api::CreateBucketCommand&>(msg), std::move(tracker));
    case api::MessageType::DELETEBUCKET_ID:
        return _asyncHandler.handleDeleteBucket(static_cast<api::DeleteBucketCommand&>(msg), std::move(tracker));
    case api::MessageType::JOINBUCKETS_ID:
        return _splitJoinHandler.handleJoinBuckets(static_cast<api::JoinBucketsCommand&>(msg), std::move(tracker));
    case api::MessageType::SPLITBUCKET_ID:
        return _splitJoinHandler.handleSplitBucket(static_cast<api::SplitBucketCommand&>(msg), std::move(tracker));
       // Depends on iterators
    case api::MessageType::STATBUCKET_ID:
        return _processAllHandler.handleStatBucket(static_cast<api::StatBucketCommand&>(msg), std::move(tracker));
    case api::MessageType::REMOVELOCATION_ID:
        return _asyncHandler.handleRemoveLocation(static_cast<api::RemoveLocationCommand&>(msg), std::move(tracker));
    case api::MessageType::MERGEBUCKET_ID:
        return _mergeHandler.handleMergeBucket(static_cast<api::MergeBucketCommand&>(msg), std::move(tracker));
    case api::MessageType::GETBUCKETDIFF_ID:
        return _mergeHandler.handleGetBucketDiff(static_cast<api::GetBucketDiffCommand&>(msg), std::move(tracker));
    case api::MessageType::APPLYBUCKETDIFF_ID:
        return _mergeHandler.handleApplyBucketDiff(static_cast<api::ApplyBucketDiffCommand&>(msg), std::move(tracker));
    case api::MessageType::SETBUCKETSTATE_ID:
        return _asyncHandler.handleSetBucketState(static_cast<api::SetBucketStateCommand&>(msg), std::move(tracker));
    case api::MessageType::INTERNAL_ID:
        switch(static_cast<api::InternalCommand&>(msg).getType()) {
        case GetIterCommand::ID:
        {
            auto usage = vespalib::CpuUsage::use(CpuUsage::Category::READ);
            return _simpleHandler.handleGetIter(static_cast<GetIterCommand&>(msg), std::move(tracker));
        }
        case CreateIteratorCommand::ID:
        {
            auto usage = vespalib::CpuUsage::use(CpuUsage::Category::READ);
            return _simpleHandler.handleCreateIterator(static_cast<CreateIteratorCommand&>(msg), std::move(tracker));
        }
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

MessageTracker::UP
PersistenceHandler::handleReply(api::StorageReply& reply, MessageTracker::UP tracker) const
{
    switch (reply.getType().getId()) {
    case api::MessageType::GETBUCKETDIFF_REPLY_ID:
        _mergeHandler.handleGetBucketDiffReply(static_cast<api::GetBucketDiffReply&>(reply), _env._fileStorHandler);
        break;
    case api::MessageType::APPLYBUCKETDIFF_REPLY_ID:
        _mergeHandler.handleApplyBucketDiffReply(static_cast<api::ApplyBucketDiffReply&>(reply), _env._fileStorHandler, std::move(tracker));
        break;
    default:
        break;
    }
    return tracker;
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
            return handleReply(static_cast<api::StorageReply&>(msg), std::move(tracker));
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
    LOG(debug, "NodeIndex %d, ptr=%p", _env._nodeIndex, lock.msg.get());
    api::StorageMessage & msg(*lock.msg);

    // Important: we _copy_ the message shared_ptr instead of moving to ensure that `msg` remains
    // valid even if the tracker is destroyed by an exception in processMessage().
    auto tracker = std::make_unique<MessageTracker>(framework::MilliSecTimer(_clock), _env, _env._fileStorHandler,
                                                    std::move(lock.lock), lock.msg, std::move(lock.throttle_token));
    tracker = processMessage(msg, std::move(tracker));
    if (tracker) {
        tracker->sendReply();
    }
}

void
PersistenceHandler::set_throttle_merge_feed_ops(bool throttle) noexcept
{
    _mergeHandler.set_throttle_merge_feed_ops(throttle);
}

}
