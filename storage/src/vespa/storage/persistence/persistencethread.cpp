// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "persistencethread.h"
#include "splitbitdetector.h"
#include "bucketownershipnotifier.h"
#include "testandsethelper.h"
#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/storage/common/bucketoperationlogger.h>
#include <vespa/document/fieldset/fieldsetrepo.h>
#include <vespa/document/base/exceptions.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/isequencedtaskexecutor.h>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP(".persistence.thread");

using vespalib::make_string_short::fmt;
using to_str = vespalib::string;

namespace storage {

namespace {

vespalib::string
createThreadName(size_t stripeId) {
    return fmt("PersistenceThread-%zu", stripeId);
}

}

PersistenceThread::PersistenceThread(vespalib::ISequencedTaskExecutor & sequencedExecutor,
                                     ServiceLayerComponentRegister& compReg,
                                     const vespa::config::content::StorFilestorConfig & cfg,
                                     spi::PersistenceProvider& provider,
                                     FileStorHandler& filestorHandler,
                                     BucketOwnershipNotifier & bucketOwnershipNotifier,
                                     FileStorThreadMetrics& metrics)
    : _stripeId(filestorHandler.getNextStripeId()),
      _component(std::make_unique<ServiceLayerComponent>(compReg, createThreadName(_stripeId))),
      _env(*_component, filestorHandler, metrics, provider),
      _spi(provider),
      _processAllHandler(_env, provider),
      _mergeHandler(_env, _spi, cfg.bucketMergeChunkSize,
                    cfg.enableMergeLocalNodeChooseDocsOptimalization,
                    cfg.commonMergeChainOptimalizationMinimumSize),
      _asyncHandler(_env, _spi, sequencedExecutor),
      _splitJoinHandler(_env, provider, bucketOwnershipNotifier, cfg.enableMultibitSplitOptimalization),
      _simpleHandler(_env, provider),
      _thread()
{
    _thread = _component->startThread(*this, 60s, 1s);
}

PersistenceThread::~PersistenceThread()
{
    LOG(debug, "Shutting down persistence thread. Waiting for current operation to finish.");
    _thread->interrupt();
    LOG(debug, "Waiting for thread to terminate.");
    _thread->join();
    LOG(debug, "Persistence thread done with destruction");
}

MessageTracker::UP
PersistenceThread::handleCommandSplitByType(api::StorageCommand& msg, MessageTracker::UP tracker)
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
        case InternalBucketJoinCommand::ID:
            return _splitJoinHandler.handleInternalBucketJoin(static_cast<InternalBucketJoinCommand&>(msg), std::move(tracker));
        case RecheckBucketInfoCommand::ID:
            return _splitJoinHandler.handleRecheckBucketInfo(static_cast<RecheckBucketInfoCommand&>(msg), std::move(tracker));
        default:
            LOG(warning, "Persistence thread received unhandled internal command %s", msg.toString().c_str());
            break;
        }
    default:
        break;
    }
    return MessageTracker::UP();
}

void
PersistenceThread::handleReply(api::StorageReply& reply)
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
PersistenceThread::processMessage(api::StorageMessage& msg, MessageTracker::UP tracker)
{
    MBUS_TRACE(msg.getTrace(), 5, "PersistenceThread: Processing message in persistence layer");

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
PersistenceThread::processLockedMessage(FileStorHandler::LockedMessage lock) {
    LOG(debug, "NodeIndex %d, ptr=%p", _env._nodeIndex, lock.second.get());
    api::StorageMessage & msg(*lock.second);

    // Important: we _copy_ the message shared_ptr instead of moving to ensure that `msg` remains
    // valid even if the tracker is destroyed by an exception in processMessage().
    auto tracker = std::make_unique<MessageTracker>(_env, _env._fileStorHandler, std::move(lock.first), lock.second);
    tracker = processMessage(msg, std::move(tracker));
    if (tracker) {
        tracker->sendReply();
    }
}

void
PersistenceThread::run(framework::ThreadHandle& thread)
{
    LOG(debug, "Started persistence thread");

    while (!thread.interrupted() && !_env._fileStorHandler.closed()) {
        thread.registerTick();

        FileStorHandler::LockedMessage lock(_env._fileStorHandler.getNextMessage(_stripeId));

        if (lock.first) {
            processLockedMessage(std::move(lock));
        }
    }
    LOG(debug, "Closing down persistence thread");
}

void
PersistenceThread::flush()
{
    //TODO Only need to check for this stripe.
    while (_env._fileStorHandler.getQueueSize() != 0) {
        std::this_thread::sleep_for(1ms);
    }
}

} // storage
