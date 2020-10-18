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

namespace {

spi::ReadConsistency api_read_consistency_to_spi(api::InternalReadConsistency consistency) noexcept {
    switch (consistency) {
    case api::InternalReadConsistency::Strong: return spi::ReadConsistency::STRONG;
    case api::InternalReadConsistency::Weak:   return spi::ReadConsistency::WEAK;
    default: abort();
    }
}

document::FieldSet::SP
getFieldSet(const document::FieldSetRepo & repo, vespalib::stringref name, MessageTracker & tracker) {
    try {
        return repo.getFieldSet(name);
    } catch (document::FieldNotFoundException & e) {
        tracker.fail(storage::api::ReturnCode::ILLEGAL_PARAMETERS,
                     fmt("Field %s in fieldset %s not found in document", e.getFieldName().c_str(), to_str(name).c_str()));
    } catch (const vespalib::Exception & e) {
        tracker.fail(storage::api::ReturnCode::ILLEGAL_PARAMETERS,
                     fmt("Failed parsing fieldset %s with : %s", to_str(name).c_str(), e.getMessage().c_str()));
    }
    return document::FieldSet::SP();
}

}

MessageTracker::UP
PersistenceThread::handleGet(api::GetCommand& cmd, MessageTracker::UP tracker)
{
    auto& metrics = _env._metrics.get[cmd.getLoadType()];
    tracker->setMetric(metrics);
    metrics.request_size.addValue(cmd.getApproxByteSize());

    auto fieldSet = getFieldSet(*_env._component.getTypeRepo()->fieldSetRepo, cmd.getFieldSet(), *tracker);
    if ( ! fieldSet) { return tracker; }

    tracker->context().setReadConsistency(api_read_consistency_to_spi(cmd.internal_read_consistency()));
    spi::GetResult result = _spi.get(_env.getBucket(cmd.getDocumentId(), cmd.getBucket()),
                                     *fieldSet, cmd.getDocumentId(), tracker->context());

    if (tracker->checkForError(result)) {
        if (!result.hasDocument() && (document::FieldSet::Type::NONE != fieldSet->getType())) {
            metrics.notFound.inc();
        }
        tracker->setReply(std::make_shared<api::GetReply>(cmd, result.getDocumentPtr(), result.getTimestamp(),
                                                          false, result.is_tombstone()));
    }

    return tracker;
}

MessageTracker::UP
PersistenceThread::handleRevert(api::RevertCommand& cmd, MessageTracker::UP tracker)
{
    tracker->setMetric(_env._metrics.revert[cmd.getLoadType()]);
    spi::Bucket b = spi::Bucket(cmd.getBucket());
    const std::vector<api::Timestamp> & tokens = cmd.getRevertTokens();
    for (const api::Timestamp & token : tokens) {
        spi::Result result = _spi.removeEntry(b, spi::Timestamp(token), tracker->context());
    }
    return tracker;
}

MessageTracker::UP
PersistenceThread::handleCreateBucket(api::CreateBucketCommand& cmd, MessageTracker::UP tracker)
{
    tracker->setMetric(_env._metrics.createBuckets);
    LOG(debug, "CreateBucket(%s)", cmd.getBucketId().toString().c_str());
    if (_env._fileStorHandler.isMerging(cmd.getBucket())) {
        LOG(warning, "Bucket %s was merging at create time. Unexpected.", cmd.getBucketId().toString().c_str());
        DUMP_LOGGED_BUCKET_OPERATIONS(cmd.getBucketId());
    }
    spi::Bucket spiBucket(cmd.getBucket());
    _spi.createBucket(spiBucket, tracker->context());
    if (cmd.getActive()) {
        _spi.setActiveState(spiBucket, spi::BucketInfo::ACTIVE);
    }
    return tracker;
}

namespace {

bool bucketStatesAreSemanticallyEqual(const api::BucketInfo& a, const api::BucketInfo& b) {
    // Don't check document sizes, as background moving of documents in Proton
    // may trigger a change in size without any mutations taking place. This will
    // only take place when a document being moved was fed _prior_ to the change
    // where Proton starts reporting actual document sizes, and will eventually
    // converge to a stable value. But for now, ignore it to prevent false positive
    // error logs and non-deleted buckets.
    return ((a.getChecksum() == b.getChecksum()) && (a.getDocumentCount() == b.getDocumentCount()));
}

}

bool
PersistenceThread::checkProviderBucketInfoMatches(const spi::Bucket& bucket, const api::BucketInfo& info) const
{
    spi::BucketInfoResult result(_spi.getBucketInfo(bucket));
    if (result.hasError()) {
        LOG(error, "getBucketInfo(%s) failed before deleting bucket; got error '%s'",
            bucket.toString().c_str(), result.getErrorMessage().c_str());
        return false;
    }
    api::BucketInfo providerInfo(PersistenceUtil::convertBucketInfo(result.getBucketInfo()));
    // Don't check meta fields or active/ready fields since these are not
    // that important and ready may change under the hood in a race with
    // getModifiedBuckets(). If bucket is empty it means it has already
    // been deleted by a racing split/join.
    if (!bucketStatesAreSemanticallyEqual(info, providerInfo) && !providerInfo.empty()) {
        LOG(error,
            "Service layer bucket database and provider out of sync before "
            "deleting bucket %s! Service layer db had %s while provider says "
            "bucket has %s. Deletion has been rejected to ensure data is not "
            "lost, but bucket may remain out of sync until service has been "
            "restarted.",
            bucket.toString().c_str(), info.toString().c_str(), providerInfo.toString().c_str());
        return false;
    }
    return true;
}

MessageTracker::UP
PersistenceThread::handleDeleteBucket(api::DeleteBucketCommand& cmd, MessageTracker::UP tracker)
{
    tracker->setMetric(_env._metrics.deleteBuckets);
    LOG(debug, "DeletingBucket(%s)", cmd.getBucketId().toString().c_str());
    LOG_BUCKET_OPERATION(cmd.getBucketId(), "deleteBucket()");
    if (_env._fileStorHandler.isMerging(cmd.getBucket())) {
        _env._fileStorHandler.clearMergeStatus(cmd.getBucket(),
                api::ReturnCode(api::ReturnCode::ABORTED, "Bucket was deleted during the merge"));
    }
    spi::Bucket bucket(cmd.getBucket());
    if (!checkProviderBucketInfoMatches(bucket, cmd.getBucketInfo())) {
           return tracker;
    }
    _spi.deleteBucket(bucket, tracker->context());
    StorBucketDatabase& db(_env.getBucketDatabase(cmd.getBucket().getBucketSpace()));
    {
        StorBucketDatabase::WrappedEntry entry(db.get(cmd.getBucketId(), "FileStorThread::onDeleteBucket"));
        if (entry.exist() && entry->getMetaCount() > 0) {
            LOG(debug, "onDeleteBucket(%s): Bucket DB entry existed. Likely "
                       "active operation when delete bucket was queued. "
                       "Updating bucket database to keep it in sync with file. "
                       "Cannot delete bucket from bucket database at this "
                       "point, as it can have been intentionally recreated "
                       "after delete bucket had been sent",
                cmd.getBucketId().toString().c_str());
            api::BucketInfo info(0, 0, 0);
            // Only set document counts/size; retain ready/active state.
            info.setReady(entry->getBucketInfo().isReady());
            info.setActive(entry->getBucketInfo().isActive());

            entry->setBucketInfo(info);
            entry.write();
        }
    }
    return tracker;
}

MessageTracker::UP
PersistenceThread::handleGetIter(GetIterCommand& cmd, MessageTracker::UP tracker)
{
    tracker->setMetric(_env._metrics.visit[cmd.getLoadType()]);
    spi::IterateResult result(_spi.iterate(cmd.getIteratorId(), cmd.getMaxByteSize(), tracker->context()));
    if (tracker->checkForError(result)) {
        auto reply = std::make_shared<GetIterReply>(cmd);
        reply->getEntries() = result.steal_entries();
        _env._metrics.visit[cmd.getLoadType()].
            documentsPerIterate.addValue(reply->getEntries().size());
        if (result.isCompleted()) {
            reply->setCompleted();
        }
        tracker->setReply(reply);
    }
    return tracker;
}

MessageTracker::UP
PersistenceThread::handleReadBucketList(ReadBucketList& cmd, MessageTracker::UP tracker)
{
    tracker->setMetric(_env._metrics.readBucketList);

    assert(cmd.getPartition() == 0u);
    spi::BucketIdListResult result(_spi.listBuckets(cmd.getBucketSpace()));
    if (tracker->checkForError(result)) {
        auto reply = std::make_shared<ReadBucketListReply>(cmd);
        result.getList().swap(reply->getBuckets());
        tracker->setReply(reply);
    }

    return tracker;
}

MessageTracker::UP
PersistenceThread::handleReadBucketInfo(ReadBucketInfo& cmd, MessageTracker::UP tracker)
{
    tracker->setMetric(_env._metrics.readBucketInfo);
    _env.updateBucketDatabase(cmd.getBucket(), _env.getBucketInfo(cmd.getBucket()));
    return tracker;
}

MessageTracker::UP
PersistenceThread::handleCreateIterator(CreateIteratorCommand& cmd, MessageTracker::UP tracker)
{
    tracker->setMetric(_env._metrics.createIterator);
    auto fieldSet = getFieldSet(*_env._component.getTypeRepo()->fieldSetRepo, cmd.getFields(), *tracker);
    if ( ! fieldSet) { return tracker; }

    tracker->context().setReadConsistency(cmd.getReadConsistency());
    spi::CreateIteratorResult result(_spi.createIterator(
            spi::Bucket(cmd.getBucket()),
            std::move(fieldSet), cmd.getSelection(), cmd.getIncludedVersions(), tracker->context()));
    if (tracker->checkForError(result)) {
        tracker->setReply(std::make_shared<CreateIteratorReply>(cmd, spi::IteratorId(result.getIteratorId())));
    }
    return tracker;
}

MessageTracker::UP
PersistenceThread::handleCommandSplitByType(api::StorageCommand& msg, MessageTracker::UP tracker)
{
    switch (msg.getType().getId()) {
    case api::MessageType::GET_ID:
        return handleGet(static_cast<api::GetCommand&>(msg), std::move(tracker));
    case api::MessageType::PUT_ID:
        return _asyncHandler.handlePut(static_cast<api::PutCommand&>(msg), std::move(tracker));
    case api::MessageType::REMOVE_ID:
        return _asyncHandler.handleRemove(static_cast<api::RemoveCommand&>(msg), std::move(tracker));
    case api::MessageType::UPDATE_ID:
        return _asyncHandler.handleUpdate(static_cast<api::UpdateCommand&>(msg), std::move(tracker));
    case api::MessageType::REVERT_ID:
        return handleRevert(static_cast<api::RevertCommand&>(msg), std::move(tracker));
    case api::MessageType::CREATEBUCKET_ID:
        return handleCreateBucket(static_cast<api::CreateBucketCommand&>(msg), std::move(tracker));
    case api::MessageType::DELETEBUCKET_ID:
        return handleDeleteBucket(static_cast<api::DeleteBucketCommand&>(msg), std::move(tracker));
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
            return handleGetIter(static_cast<GetIterCommand&>(msg), std::move(tracker));
        case CreateIteratorCommand::ID:
            return handleCreateIterator(static_cast<CreateIteratorCommand&>(msg), std::move(tracker));
        case ReadBucketList::ID:
            return handleReadBucketList(static_cast<ReadBucketList&>(msg), std::move(tracker));
        case ReadBucketInfo::ID:
            return handleReadBucketInfo(static_cast<ReadBucketInfo&>(msg), std::move(tracker));
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
