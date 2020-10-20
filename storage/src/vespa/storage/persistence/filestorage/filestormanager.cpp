// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "filestorhandlerimpl.h"
#include "filestormanager.h"
#include <vespa/storage/bucketdb/lockablemap.hpp>
#include <vespa/storage/bucketdb/minimumusedbitstracker.h>
#include <vespa/storage/common/bucketmessages.h>
#include <vespa/storage/common/content_bucket_space_repo.h>
#include <vespa/storage/common/doneinitializehandler.h>
#include <vespa/vdslib/state/cluster_state_bundle.h>
#include <vespa/storage/common/messagebucket.h>
#include <vespa/storage/config/config-stor-server.h>
#include <vespa/storage/persistence/bucketownershipnotifier.h>
#include <vespa/storage/persistence/persistencethread.h>
#include <vespa/storage/persistence/persistencehandler.h>
#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/storageapi/message/stat.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/sequencedtaskexecutor.h>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".persistence.filestor.manager");

using std::shared_ptr;
using document::BucketSpace;
using vespalib::make_string_short::fmt;

namespace storage {

FileStorManager::
FileStorManager(const config::ConfigUri & configUri, spi::PersistenceProvider& provider,
                ServiceLayerComponentRegister& compReg, DoneInitializeHandler& init_handler)
    : StorageLinkQueued("File store manager", compReg),
      framework::HtmlStatusReporter("filestorman", "File store manager"),
      _compReg(compReg),
      _component(compReg, "filestormanager"),
      _providerCore(provider),
      _providerErrorWrapper(_providerCore),
      _provider(&_providerErrorWrapper),
      _init_handler(init_handler),
      _bucketIdFactory(_component.getBucketIdFactory()),
      _persistenceHandlers(),
      _threads(),
      _bucketOwnershipNotifier(std::make_unique<BucketOwnershipNotifier>(_component, *this)),
      _configFetcher(configUri.getContext()),
      _threadLockCheckInterval(60),
      _failDiskOnError(false),
      _metrics(std::make_unique<FileStorMetrics>(_component.getLoadTypes()->getMetricLoadTypes())),
      _closed(false),
      _lock()
{
    _configFetcher.subscribe(configUri.getConfigId(), this);
    _configFetcher.start();
    _component.registerMetric(*_metrics);
    _component.registerStatusPage(*this);
    _component.getStateUpdater().addStateListener(*this);
    propagateClusterStates();
}

FileStorManager::~FileStorManager()
{
    closeNextLink();
    LOG(debug, "Deleting link %s. Giving filestor threads stop signal.",
        toString().c_str());

    for (const auto & thread : _threads) {
        if (thread) {
            thread->getThread().interrupt();
        }
    }
    for (const auto & thread : _threads) {
        if (thread) {
            thread->getThread().join();
        }
    }
    LOG(debug, "Closing all filestor queues, answering queued messages. New messages will be refused.");
    _filestorHandler->close();
    LOG(debug, "Deleting filestor threads. Waiting for their current operation "
               "to finish. Stop their threads and delete objects.");
    _threads.clear();
}

void
FileStorManager::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << "FileStorManager";
}

namespace {

uint32_t computeNumResponseThreads(int configured) {
    return (configured <= 0) ? std::max(1u, std::thread::hardware_concurrency()/4) : configured;
}

vespalib::Executor::OptimizeFor
selectSequencer(vespa::config::content::StorFilestorConfig::ResponseSequencerType sequencerType) {
    switch (sequencerType) {
        case vespa::config::content::StorFilestorConfig::ResponseSequencerType::THROUGHPUT:
            return vespalib::Executor::OptimizeFor::THROUGHPUT;
        case vespa::config::content::StorFilestorConfig::ResponseSequencerType::LATENCY:
            return vespalib::Executor::OptimizeFor::LATENCY;
        case vespa::config::content::StorFilestorConfig::ResponseSequencerType::ADAPTIVE:
        default:
            return vespalib::Executor::OptimizeFor::ADAPTIVE;
    }
}

vespalib::string
createThreadName(size_t stripeId) {
    return fmt("PersistenceThread-%zu", stripeId);
}

thread_local PersistenceHandler * _G_threadLocalHandler = nullptr;

}

PersistenceHandler &
FileStorManager::createRegisteredHandler(const ServiceLayerComponent & component)
{
    std::lock_guard guard(_lock);
    size_t index = _persistenceHandlers.size();
    assert(index < _metrics->disks[0]->threads.size());
    _persistenceHandlers.push_back(
            std::make_unique<PersistenceHandler>(*_sequencedExecutor, component,
                                                 *_config, *_provider, *_filestorHandler,
                                                 *_bucketOwnershipNotifier, *_metrics->disks[0]->threads[index]));
    return *_persistenceHandlers.back();
}

PersistenceHandler &
FileStorManager::getThreadLocalHandler() {
    if (_G_threadLocalHandler == nullptr) {
        _G_threadLocalHandler = & createRegisteredHandler(_component);
    }
    return *_G_threadLocalHandler;
}
/**
 * If live configuration, assuming storageserver makes sure no messages are
 * incoming during reconfiguration
 */
void
FileStorManager::configure(std::unique_ptr<vespa::config::content::StorFilestorConfig> config)
{
    // If true, this is not the first configure.
    bool liveUpdate = ! _threads.empty();

    _threadLockCheckInterval = config->diskOperationTimeout;
    _failDiskOnError = (config->failDiskAfterErrorCount > 0);

    if (!liveUpdate) {
        _config = std::move(config);
        size_t numThreads = _config->numThreads;
        size_t numStripes = std::max(size_t(1u), numThreads / 2);
        _metrics->initDiskMetrics(1, _component.getLoadTypes()->getMetricLoadTypes(), numStripes,
                                  numThreads + _config->numResponseThreads + _config->numNetworkThreads);

        _filestorHandler = std::make_unique<FileStorHandlerImpl>(numThreads, numStripes, *this, *_metrics, _compReg);
        uint32_t numResponseThreads = computeNumResponseThreads(_config->numResponseThreads);
        _sequencedExecutor = vespalib::SequencedTaskExecutor::create(numResponseThreads, 10000, selectSequencer(_config->responseSequencerType));
        assert(_sequencedExecutor);
        LOG(spam, "Setting up the disk");
        for (uint32_t i = 0; i < numThreads; i++) {
            _persistenceComponents.push_back(std::make_unique<ServiceLayerComponent>(_compReg, createThreadName(i)));
            _threads.push_back(std::make_unique<PersistenceThread>(createRegisteredHandler(*_persistenceComponents.back()),
                                                                   *_filestorHandler, i % numStripes, _component));
        }
    }
}

void
FileStorManager::replyDroppedOperation(api::StorageMessage& msg, const document::Bucket& bucket,
                                       api::ReturnCode::Result returnCode, vespalib::stringref reason)
{
    std::ostringstream error;
    error << "Dropping " << msg.getType() << " to bucket "
          << bucket.toString() << ". Reason: " << reason;
    LOGBT(debug, bucket.toString(), "%s", error.str().c_str());
    if (!msg.getType().isReply()) {
        std::shared_ptr<api::StorageReply> reply = static_cast<api::StorageCommand&>(msg).makeReply();
        reply->setResult(api::ReturnCode(returnCode, error.str()));
        sendUp(reply);
    }
}

void
FileStorManager::replyWithBucketNotFound(api::StorageMessage& msg, const document::Bucket& bucket)
{
    replyDroppedOperation(msg, bucket, api::ReturnCode::BUCKET_NOT_FOUND, "bucket does not exist");
}

StorBucketDatabase::WrappedEntry
FileStorManager::mapOperationToDisk(api::StorageMessage& msg, const document::Bucket& bucket)
{
    StorBucketDatabase::WrappedEntry entry(_component.getBucketDatabase(bucket.getBucketSpace()).get(
            bucket.getBucketId(), "FileStorManager::mapOperationToDisk"));
    if (!entry.exist()) {
        replyWithBucketNotFound(msg, bucket);
    }
    return entry;
}

StorBucketDatabase::WrappedEntry
FileStorManager::mapOperationToBucketAndDisk(api::BucketCommand& cmd, const document::DocumentId* docId)
{
    StorBucketDatabase &database = _component.getBucketDatabase(cmd.getBucket().getBucketSpace());
    StorBucketDatabase::WrappedEntry entry(database.get(
            cmd.getBucketId(), "FileStorManager::mapOperationToBucketAndDisk"));
    if (!entry.exist()) {
        document::BucketId specific(cmd.getBucketId());
        if (docId) {
            specific = _bucketIdFactory.getBucketId(*docId);
        }
        typedef std::map<document::BucketId, StorBucketDatabase::WrappedEntry> BucketMap;
        std::shared_ptr<api::StorageReply> reply;
        {
            BucketMap results( database.getContained( specific, "FileStorManager::mapOperationToBucketAndDisk-2"));
            if (results.size() == 1) {
                LOG(debug, "Remapping %s operation to specific %s versus non-existing %s to %s.",
                    cmd.toString().c_str(), specific.toString().c_str(),
                    cmd.getBucketId().toString().c_str(),
                    results.begin()->first.toString().c_str());
                cmd.remapBucketId(results.begin()->first);
                return std::move(results.begin()->second);
            }
            std::ostringstream error;
            error << "Dropping " << cmd.getType() << " to bucket "
                  << cmd.getBucketId().toString() << " since bucket doesnt exist. ";
            if (results.size() > 1) {
                error << "Bucket was inconsistent with " << results.size()
                      << " entries so no automatic remapping done:";
                BucketMap::const_iterator it = results.begin();
                for (uint32_t i=0; i <= 4 && it != results.end(); ++it, ++i) {
                    error << " " << it->first;
                }
                if (it != results.end()) {
                    error << " ...";
                }
            } else {
                error << "No other bucket exists that can contain this data either.";
            }
            LOGBT(debug, cmd.getBucketId().toString(), "%s", error.str().c_str());

            reply = static_cast<api::StorageCommand&>(cmd).makeReply();
            reply->setResult( api::ReturnCode( api::ReturnCode::BUCKET_NOT_FOUND, error.str()));
        }
        sendUp(reply);
    }
    return entry;
}

bool
FileStorManager::handlePersistenceMessage( const shared_ptr<api::StorageMessage>& msg)
{
    api::ReturnCode errorCode(api::ReturnCode::OK);
    do {
        LOG(spam, "Received %s. Attempting to queue it.", msg->getType().getName().c_str());

        if (_filestorHandler->schedule(msg)) {
            LOG(spam, "Received persistence message %s. Queued it to disk",
                msg->getType().getName().c_str());
            return true;
        }
        switch (_filestorHandler->getDiskState()) {
            case FileStorHandler::DISABLED:
                errorCode = api::ReturnCode(api::ReturnCode::DISK_FAILURE, "Disk disabled");
                break;
            case FileStorHandler::CLOSED:
                errorCode = api::ReturnCode(api::ReturnCode::ABORTED, "Shutting down storage node.");
                break;
            case FileStorHandler::AVAILABLE:
                assert(false);
        }
    } while (false);
        // If we get here, we failed to schedule message. errorCode says why
        // We need to reply to message (while not having bucket lock)
    if (!msg->getType().isReply()) {
        std::shared_ptr<api::StorageReply> reply = static_cast<api::StorageCommand&>(*msg).makeReply();
        reply->setResult(errorCode);
        LOG(spam, "Received persistence message %s. Returning reply: %s",
            msg->getType().getName().c_str(), errorCode.toString().c_str());
        dispatchUp(reply);
    }
    return true;
}

bool
FileStorManager::onPut(const shared_ptr<api::PutCommand>& cmd)
{
    if (cmd->getTimestamp() == 0) {
        shared_ptr<api::StorageReply> reply = cmd->makeReply();
        std::string msg("Put command received without timestamp set. "
                        "Distributor need to set timestamp to ensure equal "
                        "timestamps between storage nodes. Rejecting.");
        reply->setResult(api::ReturnCode(api::ReturnCode::REJECTED, msg));
        sendUp(reply);
        return true;
    }
    StorBucketDatabase::WrappedEntry entry(mapOperationToBucketAndDisk(*cmd, &cmd->getDocumentId()));
    if (entry.exist()) {
        handlePersistenceMessage(cmd);
    }
    return true;
}

bool
FileStorManager::onUpdate(const shared_ptr<api::UpdateCommand>& cmd)
{
    if (cmd->getTimestamp() == 0) {
        shared_ptr<api::StorageReply> reply = cmd->makeReply();
        std::string msg("Update command received without timestamp set. "
                        "Distributor need to set timestamp to ensure equal "
                        "timestamps between storage nodes. Rejecting.");
        reply->setResult(api::ReturnCode(api::ReturnCode::REJECTED, msg));
        sendUp(reply);
        return true;
    }
    StorBucketDatabase::WrappedEntry entry(mapOperationToBucketAndDisk(*cmd, &cmd->getDocumentId()));
    if (entry.exist()) {
        handlePersistenceMessage(cmd);
    }
    return true;
}

bool
FileStorManager::onGet(const shared_ptr<api::GetCommand>& cmd)
{
    StorBucketDatabase::WrappedEntry entry(mapOperationToBucketAndDisk(*cmd, &cmd->getDocumentId()));
    if (entry.exist()) {
        handlePersistenceMessage(cmd);
    }
    return true;
}

bool
FileStorManager::onRemove(const shared_ptr<api::RemoveCommand>& cmd)
{
    if (cmd->getTimestamp() == 0) {
        shared_ptr<api::StorageReply> reply = cmd->makeReply();
        std::string msg("Remove command received without timestamp set. "
                        "Distributor need to set timestamp to ensure equal "
                        "timestamps between storage nodes. Rejecting.");
        reply->setResult(api::ReturnCode(api::ReturnCode::REJECTED, msg));
        sendUp(reply);
        return true;
    }
    StorBucketDatabase::WrappedEntry entry(mapOperationToBucketAndDisk(*cmd, &cmd->getDocumentId()));
    if (entry.exist()) {
        handlePersistenceMessage(cmd);
    }
    return true;
}

bool
FileStorManager::onRevert(const shared_ptr<api::RevertCommand>& cmd)
{
    StorBucketDatabase::WrappedEntry entry(mapOperationToBucketAndDisk(*cmd, 0));
    if (entry.exist()) {
        handlePersistenceMessage(cmd);
    }
    return true;
}

bool
FileStorManager::onRemoveLocation(const std::shared_ptr<api::RemoveLocationCommand>& cmd)
{
    StorBucketDatabase::WrappedEntry entry(mapOperationToDisk(*cmd, cmd->getBucket()));
    if (entry.exist()) {
        handlePersistenceMessage(cmd);
    }
    return true;
}

bool
FileStorManager::onStatBucket(const std::shared_ptr<api::StatBucketCommand>& cmd)
{
    StorBucketDatabase::WrappedEntry entry(mapOperationToDisk(*cmd, cmd->getBucket()));
    if (entry.exist()) {
        handlePersistenceMessage(cmd);
    }
    return true;
}

bool
FileStorManager::onCreateBucket(
        const std::shared_ptr<api::CreateBucketCommand>& cmd)
{
    api::ReturnCode code(api::ReturnCode::OK);
    {
        document::Bucket bucket(cmd->getBucket());
        StorBucketDatabase::WrappedEntry entry(
                _component.getBucketDatabase(bucket.getBucketSpace()).get(
                    bucket.getBucketId(), "FileStorManager::onCreateBucket",
                    StorBucketDatabase::CREATE_IF_NONEXISTING));
        if (entry.preExisted()) {
            LOG(debug, "Got create bucket request for %s which already exists: %s",
                cmd->getBucketId().toString().c_str(),
                entry->getBucketInfo().toString().c_str());
            code = api::ReturnCode(api::ReturnCode::EXISTS, "Bucket already exist");
        } else {
            // Newly created buckets are ready but not active, unless
            // explicitly marked as such by the distributor.
            entry->setBucketInfo(api::BucketInfo(0, 0, 0, 0, 0, true, cmd->getActive()));
            cmd->setPriority(0);
            handlePersistenceMessage(cmd);
            entry.write();
            LOG(debug, "Created bucket %s (node index is %d)",
                cmd->getBucketId().toString().c_str(), _component.getIndex());
            return true;
        }
    }
    std::shared_ptr<api::CreateBucketReply> reply((api::CreateBucketReply*)cmd->makeReply().release());
    reply->setBucketInfo(api::BucketInfo(0, 0, 0, 0, 0, true, cmd->getActive()));
    reply->setResult(code);
    sendUp(reply);
    return true;
}

bool
FileStorManager::onDeleteBucket(const shared_ptr<api::DeleteBucketCommand>& cmd)
{
    {
        document::Bucket bucket(cmd->getBucket());
        StorBucketDatabase::WrappedEntry entry(_component.getBucketDatabase(bucket.getBucketSpace()).get(bucket.getBucketId(),
                                                                                  "FileStorManager::onDeleteBucket"));
        if (!entry.exist()) {
            LOG(debug, "%s was already deleted", cmd->getBucketId().toString().c_str());
            std::shared_ptr<api::StorageReply> reply = cmd->makeReply();
            sendUp(reply);
            return true;
        }

        // If bucket info in command is invalid, it means it was sent by a
        // distributor with an older protocol implementation of
        // DeleteBucketCommand, so we should always allow it to go through
        if (cmd->getBucketInfo().valid()
            && (cmd->getBucketInfo().getChecksum()
                != entry->getBucketInfo().getChecksum()))
        {
            vespalib::asciistream ost;
            ost << "DeleteBucketCommand("
                << cmd->getBucketId().toString()
                << ") did not have up to date bucketinfo. "
                << "Distributor thought we had "
                << cmd->getBucketInfo().toString()
                << ", but storage bucket database contains "
                << entry->getBucketInfo().toString();

            LOG(debug, "Rejecting bucket delete: %s", ost.str().data());
            std::shared_ptr<api::StorageReply> reply = cmd->makeReply();
            static_cast<api::DeleteBucketReply&>(*reply).setBucketInfo(entry->getBucketInfo());
            reply->setResult(api::ReturnCode(api::ReturnCode::REJECTED, ost.str()));
            entry.unlock();
            sendUp(reply);
            return true;
        }

        // Forcing max pri on delete bucket for now, so we can't get into
        // a race condition with a create bucket/put coming in after with
        // higher priority.
        cmd->setPriority(0);
        LOG(debug, "Deleting %s", cmd->getBucketId().toString().c_str());
        handlePersistenceMessage(cmd);
        entry.remove();
    }
    _filestorHandler->failOperations(cmd->getBucket(),
                                     api::ReturnCode(api::ReturnCode::BUCKET_DELETED,
                                                     fmt("Bucket %s about to be deleted anyway",
                                                         cmd->getBucketId().toString().c_str())));
    return true;
}



StorBucketDatabase::WrappedEntry
FileStorManager::ensureConsistentBucket(
        const document::Bucket& bucket,
        api::StorageMessage& msg,
        const char* callerId)
{
    StorBucketDatabase::WrappedEntry entry(_component.getBucketDatabase(bucket.getBucketSpace()).get(
                    bucket.getBucketId(), callerId, StorBucketDatabase::CREATE_IF_NONEXISTING));
    assert(entry.exist());
    if (!_component.getBucketDatabase(bucket.getBucketSpace()).isConsistent(entry)) {
        if (!entry.preExisted()) {
            // Don't create empty bucket if merge isn't allowed to continue.
            entry.remove();
        }
        replyDroppedOperation(msg, bucket, api::ReturnCode::ABORTED, "bucket is inconsistently split");
        return StorBucketDatabase::WrappedEntry();
    }

    return entry;
}

bool
FileStorManager::onMergeBucket(const shared_ptr<api::MergeBucketCommand>& cmd)
{
    StorBucketDatabase::WrappedEntry entry(ensureConsistentBucket(cmd->getBucket(), *cmd, "FileStorManager::onMergeBucket"));
    if (!entry.exist()) {
        return true;
    }

    if (!entry.preExisted()) {
        entry->info = api::BucketInfo(0, 0, 0, 0, 0, true, false);
        LOG(debug, "Created bucket %s (node index is %d) due to merge being received.",
            cmd->getBucketId().toString().c_str(), _component.getIndex());
        // Call before writing bucket entry as we need to have bucket
        // lock while calling
        handlePersistenceMessage(cmd);
        entry.write();
    } else {
        handlePersistenceMessage(cmd);
    }
    return true;
}

bool
FileStorManager::onGetBucketDiff(const shared_ptr<api::GetBucketDiffCommand>& cmd)
{
    StorBucketDatabase::WrappedEntry entry(ensureConsistentBucket(cmd->getBucket(), *cmd, "FileStorManager::onGetBucketDiff"));
    if (!entry.exist()) {
        return true;
    }
    if (!entry.preExisted()) {
        LOG(debug, "Created bucket %s (node index is %d) due to get bucket diff being received.",
            cmd->getBucketId().toString().c_str(), _component.getIndex());
        entry->info.setTotalDocumentSize(0);
        entry->info.setUsedFileSize(0);
        entry->info.setReady(true);
        // Call before writing bucket entry as we need to have bucket
        // lock while calling
        handlePersistenceMessage(cmd);
        entry.write();
    } else {
        handlePersistenceMessage(cmd);
    }
    return true;
}

bool
FileStorManager::validateApplyDiffCommandBucket(api::StorageMessage& msg, const StorBucketDatabase::WrappedEntry& entry)
{
    if (!entry.exist()) {
        return false;
    }
    BucketSpace bucketSpace(msg.getBucket().getBucketSpace());
    if (!_component.getBucketDatabase(bucketSpace).isConsistent(entry)) {
        document::Bucket bucket(bucketSpace, entry.getBucketId());
        replyDroppedOperation(msg, bucket, api::ReturnCode::ABORTED, "bucket became inconsistent during merging");
        return false;
    }
    return true;
}

bool
FileStorManager::validateDiffReplyBucket(const StorBucketDatabase::WrappedEntry& entry,
                                         const document::Bucket& bucket)
{
    if (!entry.exist()) {
        _filestorHandler->clearMergeStatus(bucket,
                api::ReturnCode(api::ReturnCode::BUCKET_NOT_FOUND, "Bucket removed during merge"));
        return false;
    }
    if (!_component.getBucketDatabase(bucket.getBucketSpace()).isConsistent(entry)) {
        _filestorHandler->clearMergeStatus(bucket,
                api::ReturnCode(api::ReturnCode::ABORTED, "Bucket became inconsistent during merging"));
        return false;
    }
    return true;
}

bool
FileStorManager::onGetBucketDiffReply(const shared_ptr<api::GetBucketDiffReply>& reply)
{
    StorBucketDatabase::WrappedEntry entry(mapOperationToDisk(*reply, reply->getBucket()));
    if (validateDiffReplyBucket(entry, reply->getBucket())) {
        handlePersistenceMessage(reply);
    }
    return true;
}

bool
FileStorManager::onApplyBucketDiff(const shared_ptr<api::ApplyBucketDiffCommand>& cmd)
{
    StorBucketDatabase::WrappedEntry entry(mapOperationToDisk(*cmd, cmd->getBucket()));
    if (validateApplyDiffCommandBucket(*cmd, entry)) {
        handlePersistenceMessage(cmd);
    }
    return true;
}

bool
FileStorManager::onApplyBucketDiffReply(const shared_ptr<api::ApplyBucketDiffReply>& reply)
{
    StorBucketDatabase::WrappedEntry entry(mapOperationToDisk(*reply, reply->getBucket()));
    if (validateDiffReplyBucket(entry, reply->getBucket())) {
        handlePersistenceMessage(reply);
    }
    return true;
}

bool
FileStorManager::onJoinBuckets(const std::shared_ptr<api::JoinBucketsCommand>& cmd)
{
    document::Bucket bucket(cmd->getBucket());
    StorBucketDatabase::WrappedEntry entry(_component.getBucketDatabase(bucket.getBucketSpace()).get(
                bucket.getBucketId(), "FileStorManager::onJoinBuckets"));
    return handlePersistenceMessage(cmd);
}

bool
FileStorManager::onSplitBucket(const std::shared_ptr<api::SplitBucketCommand>& cmd)
{
    StorBucketDatabase::WrappedEntry entry(mapOperationToDisk(*cmd, cmd->getBucket()));
    if (entry.exist()) {
        handlePersistenceMessage(cmd);
    }
    return true;
}

bool
FileStorManager::onSetBucketState(const std::shared_ptr<api::SetBucketStateCommand>& cmd)
{
    StorBucketDatabase::WrappedEntry entry(mapOperationToDisk(*cmd, cmd->getBucket()));
    if (entry.exist()) {
        handlePersistenceMessage(cmd);
    }
    return true;
}

bool
FileStorManager::onInternal(const shared_ptr<api::InternalCommand>& msg)
{
    switch (msg->getType()) {
    case GetIterCommand::ID:
    {
        shared_ptr<GetIterCommand> cmd(std::static_pointer_cast<GetIterCommand>(msg));
        StorBucketDatabase::WrappedEntry entry(mapOperationToDisk(*cmd, cmd->getBucket()));
        if (entry.exist()) {
            handlePersistenceMessage(cmd);
        }
        return true;
    }
    case CreateIteratorCommand::ID:
    {
        shared_ptr<CreateIteratorCommand> cmd(std::static_pointer_cast<CreateIteratorCommand>(msg));
        StorBucketDatabase::WrappedEntry entry(mapOperationToDisk(*cmd, cmd->getBucket()));
        if (entry.exist()) {
            handlePersistenceMessage(cmd);
        }
        return true;
    }
    case DestroyIteratorCommand::ID:
    {
        spi::Context context(msg->getLoadType(), msg->getPriority(), msg->getTrace().getLevel());
        shared_ptr<DestroyIteratorCommand> cmd(std::static_pointer_cast<DestroyIteratorCommand>(msg));
        _provider->destroyIterator(cmd->getIteratorId(), context);
        msg->getTrace().getRoot().addChild(context.getTrace().getRoot());
        return true;
    }
    case ReadBucketList::ID:
    {
        shared_ptr<ReadBucketList> cmd(std::static_pointer_cast<ReadBucketList>(msg));
        handlePersistenceMessage(cmd);
        return true;
    }
    case ReadBucketInfo::ID:
    {
        shared_ptr<ReadBucketInfo> cmd(std::static_pointer_cast<ReadBucketInfo>(msg));
        StorBucketDatabase::WrappedEntry entry(mapOperationToDisk(*cmd, cmd->getBucket()));
        if (entry.exist()) {
            handlePersistenceMessage(cmd);
        }
        return true;
    }
    case InternalBucketJoinCommand::ID:
    {
        shared_ptr<InternalBucketJoinCommand> cmd(std::static_pointer_cast<InternalBucketJoinCommand>(msg));
        StorBucketDatabase::WrappedEntry entry(mapOperationToDisk(*cmd, cmd->getBucket()));
        if (entry.exist()) {
            handlePersistenceMessage(cmd);
        }
        return true;
    }
    case RecheckBucketInfoCommand::ID:
    {
        shared_ptr<RecheckBucketInfoCommand> cmd(std::static_pointer_cast<RecheckBucketInfoCommand>(msg));
        StorBucketDatabase::WrappedEntry entry(mapOperationToDisk(*cmd, cmd->getBucket()));
        if (entry.exist()) {
            handlePersistenceMessage(cmd);
        }
        return true;
    }
    case AbortBucketOperationsCommand::ID:
    {
        shared_ptr<AbortBucketOperationsCommand> cmd(std::static_pointer_cast<AbortBucketOperationsCommand>(msg));
        handleAbortBucketOperations(cmd);
        return true;
    }
    default:
        return false;
    }
}

void
FileStorManager::handleAbortBucketOperations(const shared_ptr<AbortBucketOperationsCommand>& cmd)
{
    _filestorHandler->abortQueuedOperations(*cmd);
    sendReply(api::StorageReply::SP(cmd->makeReply()));
}

bool
FileStorManager::onInternalReply(const shared_ptr<api::InternalReply>& r)
{
    switch(r->getType()) {
    case GetIterReply::ID:
    {
        sendUp(r);
        return true;
    }
    default:
        return false;
    }
}

void
FileStorManager::sendCommand(const std::shared_ptr<api::StorageCommand>& cmd)
{
    sendUp(cmd);
}

void
FileStorManager::sendReply(const std::shared_ptr<api::StorageReply>& reply)
{
    LOG(spam, "Sending reply %s", reply->toString().c_str());

    if (reply->getType() == api::MessageType::INTERNAL_REPLY) {
        std::shared_ptr<api::InternalReply> rep(std::dynamic_pointer_cast<api::InternalReply>(reply));
        assert(rep.get());
        if (onInternalReply(rep)) return;
    }

    // Currently we need to dispatch due to replies sent by remapQueue
    // function in handlerimpl, as filestorthread keeps bucket db lock
    // while running this function
    dispatchUp(reply);
}

void
FileStorManager::sendReplyDirectly(const std::shared_ptr<api::StorageReply>& reply)
{
    LOG(spam, "Sending reply %s", reply->toString().c_str());

    if (reply->getType() == api::MessageType::INTERNAL_REPLY) {
        std::shared_ptr<api::InternalReply> rep(std::dynamic_pointer_cast<api::InternalReply>(reply));
        assert(rep);
        if (onInternalReply(rep)) return;
    }
    sendUp(reply);
}

void
FileStorManager::sendUp(const std::shared_ptr<api::StorageMessage>& msg)
{
    StorageLinkQueued::sendUp(msg);
}

void FileStorManager::onClose()
{
    LOG(debug, "Start closing");
    // Avoid getting config during shutdown
    _configFetcher.close();
    LOG(debug, "Closed _configFetcher.");
    _filestorHandler->close();
    LOG(debug, "Closed _filestorHandler.");
    _closed = true;
    StorageLinkQueued::onClose();
    LOG(debug, "Done closing");
}

void FileStorManager::onFlush(bool downwards)
{
    // Don't delete merges first time around, since threads might be
    // processing them
    LOG(debug, "Start Flushing");
    _filestorHandler->flush(!downwards);
    LOG(debug, "Flushed _filestorHandler->flush(!downwards);");
    for (const auto & thread : _threads) {
        if (thread) {
            thread->flush();
            LOG(debug, "flushed thread[%s]", thread->getThread().getId().c_str());
        }
    }
    uint32_t queueSize = _filestorHandler->getQueueSize();
    std::ostringstream ost;
    if (queueSize > 0) {
        ost << "Queue size " << queueSize;
    }
    std::string result = ost.str();
    if (result.size() > 0) {
        LOG(error, "Operations in persistence layer after flush. This is ok "
                   "during load, but should not happen when flush is called "
                   "during shutdown as load then is supposed to have been "
                   "stopped: %s",
            result.c_str());
    }
    StorageLinkQueued::onFlush(downwards);
    LOG(debug, "Done Flushing");
}

void
FileStorManager::reportHtmlStatus(std::ostream& out, const framework::HttpUrlPath& path) const
{
    bool showStatus = !path.hasAttribute("thread");
    bool verbose = path.hasAttribute("verbose");

        // Print menu
    out << "<font size=\"-1\">[ <a href=\"/\">Back to top</a>"
        << " | <a href=\"?" << (verbose ? "verbose" : "")
        << "\">Main filestor manager status page</a>"
        << " | <a href=\"?" << (verbose ? "notverbose" : "verbose");
    if (!showStatus) {
        out << "&thread=" << path.get("thread", std::string(""));
    }
    out << "\">" << (verbose ? "Less verbose" : "More verbose") << "</a>\n"
        << " ]</font><br><br>\n";

    out << "<p>Using " << _threads.size() << " threads</p>\n";

    _filestorHandler->getStatus(out, path);
}

namespace {
    struct Deactivator {
        StorBucketDatabase::Decision operator() (document::BucketId::Type, StorBucketDatabase::Entry& data) noexcept
        {
            data.info.setActive(false);
            return StorBucketDatabase::Decision::UPDATE;
        }
    };
}

void
FileStorManager::updateState()
{
    auto clusterStateBundle = _component.getStateUpdater().getClusterStateBundle();
    lib::ClusterState::CSP state(clusterStateBundle->getBaselineClusterState());
    lib::Node node(_component.getNodeType(), _component.getIndex());

    LOG(debug, "FileStorManager received cluster state '%s'", state->toString().c_str());
    for (const auto &elem : _component.getBucketSpaceRepo()) {
        BucketSpace bucketSpace(elem.first);
        ContentBucketSpace &contentBucketSpace = *elem.second;
        auto derivedClusterState = contentBucketSpace.getClusterState();
        bool nodeUp = derivedClusterState->getNodeState(node).getState().oneOf("uir");
        // If edge where we go down
        if (contentBucketSpace.getNodeUpInLastNodeStateSeenByProvider() && !nodeUp) {
            LOG(debug, "Received cluster state where this node is down; de-activating all buckets in database for bucket space %s", bucketSpace.toString().c_str());
            Deactivator deactivator;
            contentBucketSpace.bucketDatabase().for_each_mutable_unordered(
                    std::ref(deactivator), "FileStorManager::updateState");
        }
        contentBucketSpace.setNodeUpInLastNodeStateSeenByProvider(nodeUp);
        spi::ClusterState spiState(*derivedClusterState, _component.getIndex(), *contentBucketSpace.getDistribution());
        _provider->setClusterState(bucketSpace, spiState);
    }
}

void
FileStorManager::storageDistributionChanged()
{
    updateState();
}

void
FileStorManager::propagateClusterStates()
{
    auto clusterStateBundle = _component.getStateUpdater().getClusterStateBundle();
    for (const auto &elem : _component.getBucketSpaceRepo()) {
        elem.second->setClusterState(clusterStateBundle->getDerivedClusterState(elem.first));
    }
}

void
FileStorManager::handleNewState()
{
    propagateClusterStates();
    //TODO: Don't update if it isn't necessary (distributor-only change)
    updateState();
}

void FileStorManager::update_reported_state_after_db_init() {
    auto state_lock = _component.getStateUpdater().grabStateChangeLock();
    auto ns = *_component.getStateUpdater().getReportedNodeState();
    ns.setInitProgress(1.0);
    ns.setMinUsedBits(_component.getMinUsedBitsTracker().getMinUsedBits());
    _component.getStateUpdater().setReportedNodeState(ns);
}

void FileStorManager::initialize_bucket_databases_from_provider() {
    framework::MilliSecTimer start_time(_component.getClock());
    size_t bucket_count = 0;
    for (const auto& elem : _component.getBucketSpaceRepo()) {
        const auto bucket_space = elem.first;
        const auto bucket_result = _provider->listBuckets(bucket_space);
        assert(!bucket_result.hasError());
        const auto& buckets = bucket_result.getList();
        LOG(debug, "Fetching bucket info for %zu buckets in space '%s'",
            buckets.size(), elem.first.toString().c_str());
        auto& db = elem.second->bucketDatabase();

        for (const auto& bucket : buckets) {
            _component.getMinUsedBitsTracker().update(bucket);
            // TODO replace with far more efficient bulk insert API
            auto entry = db.get(bucket, "FileStorManager::initialize_bucket_databases_from_provider",
                                StorBucketDatabase::CREATE_IF_NONEXISTING);
            assert(!entry.preExisted());
            auto spi_bucket = spi::Bucket(document::Bucket(bucket_space, bucket));
            auto provider_result = _provider->getBucketInfo(spi_bucket);
            assert(!provider_result.hasError());
            entry->setBucketInfo(PersistenceUtil::convertBucketInfo(provider_result.getBucketInfo()));
            entry.write();
        }
        bucket_count += buckets.size();
    }
    const double elapsed = start_time.getElapsedTimeAsDouble();
    LOG(info, "Completed listing of %zu buckets in %.2g milliseconds", bucket_count, elapsed);
    _metrics->bucket_db_init_latency.addValue(elapsed);

    update_reported_state_after_db_init();
    _init_handler.notifyDoneInitializing();
}

} // storage
