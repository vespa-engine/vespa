// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "filestormanager.h"

#include <vespa/storage/bucketdb/lockablemap.hpp>
#include <vespa/storage/common/bucketmessages.h>
#include <vespa/storage/common/bucketoperationlogger.h>
#include <vespa/storage/common/content_bucket_space_repo.h>
#include <vespa/vdslib/state/cluster_state_bundle.h>
#include <vespa/storage/common/messagebucket.h>
#include <vespa/storage/config/config-stor-server.h>
#include <vespa/storage/persistence/bucketownershipnotifier.h>
#include <vespa/storage/persistence/persistencethread.h>
#include <vespa/storage/storageutil/log.h>
#include <vespa/storageapi/message/batch.h>
#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/storageapi/message/multioperation.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".persistence.filestor.manager");

using std::shared_ptr;
using document::BucketSpace;

namespace storage {

FileStorManager::
FileStorManager(const config::ConfigUri & configUri,
                const spi::PartitionStateList& partitions,
                spi::PersistenceProvider& provider,
                ServiceLayerComponentRegister& compReg)
    : StorageLinkQueued("File store manager", compReg),
      framework::HtmlStatusReporter("filestorman", "File store manager"),
      _compReg(compReg),
      _component(compReg, "filestormanager"),
      _partitions(partitions),
      _providerCore(provider),
      _providerErrorWrapper(_providerCore),
      _nodeUpInLastNodeStateSeenByProvider(false),
      _providerMetric(new spi::MetricPersistenceProvider(_providerErrorWrapper)),
      _provider(_providerMetric.get()),
      _bucketIdFactory(_component.getBucketIdFactory()),
      _configUri(configUri),
      _disks(),
      _bucketOwnershipNotifier(new BucketOwnershipNotifier(_component, *this)),
      _configFetcher(_configUri.getContext()),
      _threadLockCheckInterval(60),
      _failDiskOnError(false),
      _metrics(new FileStorMetrics(_component.getLoadTypes()->getMetricLoadTypes())),
      _threadMonitor(),
      _closed(false)
{
    _metrics->registerMetric(*_providerMetric),
    _configFetcher.subscribe(_configUri.getConfigId(), this);
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

    for (uint32_t i = 0; i < _disks.size(); ++i) {
        for (uint32_t j = 0; j < _disks[i].size(); ++j) {
            if (_disks[i][j].get() != 0) {
                _disks[i][j]->getThread().interrupt();
            }
        }
    }
    for (uint32_t i = 0; i < _disks.size(); ++i) {
        for (uint32_t j = 0; j < _disks[i].size(); ++j) {
            if (_disks[i][j].get() != 0) {
                _disks[i][j]->getThread().join();
            }
        }
    }
    LOG(debug, "Closing all filestor queues, answering queued messages. "
               "New messages will be refused.");
    _filestorHandler->close();
    LOG(debug, "Deleting filestor threads. Waiting for their current operation "
               "to finish. Stop their threads and delete objects.");
    _disks.clear();
}

void
FileStorManager::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << "FileStorManager";
}

/**
 * If live configuration, assuming storageserver makes sure no messages are
 * incoming during reconfiguration
 */
void
FileStorManager::configure(std::unique_ptr<vespa::config::content::StorFilestorConfig> config)
{
    // If true, this is not the first configure.
    bool liveUpdate = (_disks.size() != 0);

    _threadLockCheckInterval = config->diskOperationTimeout;
    _failDiskOnError = (config->failDiskAfterErrorCount > 0);

    if (!liveUpdate) {
        _config = std::move(config);

        _disks.resize(_component.getDiskCount());

        size_t numThreads = (_config->numThreads) ? 6 : _config->numThreads;
        _metrics->initDiskMetrics(_disks.size(), _component.getLoadTypes()->getMetricLoadTypes(), numThreads);

        _filestorHandler.reset(new FileStorHandler(*this, *_metrics, _partitions, _compReg));
        for (uint32_t i=0; i<_component.getDiskCount(); ++i) {
            if (_partitions[i].isUp()) {
                LOG(spam, "Setting up disk %u", i);
                for (uint32_t j = 0; j < numThreads; j++) {
                    _disks[i].push_back(DiskThread::SP(
                            new PersistenceThread(_compReg, _configUri, *_provider, *_filestorHandler,
                                                  *_metrics->disks[i]->threads[j], i)));

                }
            } else {
                _filestorHandler->disable(i);
            }
        }
    }
}

void
FileStorManager::replyDroppedOperation(api::StorageMessage& msg,
                                       const document::Bucket& bucket,
                                       api::ReturnCode::Result returnCode,
                                       vespalib::stringref reason)
{
    std::ostringstream error;
    error << "Dropping " << msg.getType() << " to bucket "
          << bucket.toString() << ". Reason: " << reason;
    LOGBT(debug, bucket.toString(), "%s", error.str().c_str());
    if (!msg.getType().isReply()) {
        std::shared_ptr<api::StorageReply> reply(
                static_cast<api::StorageCommand&>(msg).makeReply().release());
        reply->setResult(api::ReturnCode(returnCode, error.str()));
        sendUp(reply);
    }
}

void
FileStorManager::replyWithBucketNotFound(api::StorageMessage& msg,
                                         const document::Bucket& bucket)
{
    replyDroppedOperation(msg,
                          bucket,
                          api::ReturnCode::BUCKET_NOT_FOUND,
                          "bucket does not exist");
}

StorBucketDatabase::WrappedEntry
FileStorManager::mapOperationToDisk(api::StorageMessage& msg,
                                    const document::Bucket& bucket)
{
    StorBucketDatabase::WrappedEntry entry(_component.getBucketDatabase(bucket.getBucketSpace()).get(
            bucket.getBucketId(), "FileStorManager::mapOperationToDisk"));
    if (!entry.exist()) {
        replyWithBucketNotFound(msg, bucket);
    }
    return entry;
}

StorBucketDatabase::WrappedEntry
FileStorManager::mapOperationToBucketAndDisk(api::BucketCommand& cmd,
                                             const document::DocumentId* docId)
{
    StorBucketDatabase &database = _component.getBucketDatabase(cmd.getBucket().getBucketSpace());
    StorBucketDatabase::WrappedEntry entry(database.get(
            cmd.getBucketId(), "FileStorManager::mapOperationToBucketAndDisk"));
    if (!entry.exist()) {
        document::BucketId specific(cmd.getBucketId());
        if (docId) {
            specific = _bucketIdFactory.getBucketId(*docId);
        }
        typedef std::map<document::BucketId,
                         StorBucketDatabase::WrappedEntry> BucketMap;
        std::shared_ptr<api::StorageReply> reply;
        {
            BucketMap results(
                    database.getContained(
                            specific, "FileStorManager::mapOperationToBucketAndDisk-2"));
            if (results.size() == 1) {
                LOG(debug,
                    "Remapping %s operation to specific %s versus "
                    "non-existing %s to %s.",
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

            reply.reset(static_cast<api::StorageCommand&>(cmd).makeReply().release());
            reply->setResult(
                    api::ReturnCode(
                            api::ReturnCode::BUCKET_NOT_FOUND, error.str()));
        }
        sendUp(reply);
    }
    return entry;
}

bool
FileStorManager::handlePersistenceMessage(
        const shared_ptr<api::StorageMessage>& msg, uint16_t disk)
{
    api::ReturnCode errorCode(api::ReturnCode::OK);
    do {
        LOG(spam, "Received %s. Attempting to queue it to disk %u.",
            msg->getType().getName().c_str(), disk);

        LOG_BUCKET_OPERATION_NO_LOCK(
                getStorageMessageBucket(*msg).getBucketId(),
                vespalib::make_string("Attempting to queue %s to disk %u",
                                            msg->toString().c_str(), disk));


        if (_filestorHandler->schedule(msg, disk)) {
            LOG(spam, "Received persistence message %s. Queued it to disk %u",
                msg->getType().getName().c_str(), disk);
            return true;
        }
        switch (_filestorHandler->getDiskState(disk)) {
            case FileStorHandler::DISABLED:
                errorCode = api::ReturnCode(api::ReturnCode::DISK_FAILURE,
                                            "Disk disabled");
                break;
            case FileStorHandler::CLOSED:
                errorCode = api::ReturnCode(api::ReturnCode::ABORTED,
                                            "Shutting down storage node.");
                break;
            case FileStorHandler::AVAILABLE:
                assert(false);
        }
    } while(0);
        // If we get here, we failed to schedule message. errorCode says why
        // We need to reply to message (while not having bucket lock)
    if (!msg->getType().isReply()) {
        std::shared_ptr<api::StorageReply> reply(
                static_cast<api::StorageCommand&>(*msg).makeReply().release());
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
        shared_ptr<api::StorageReply> reply(cmd->makeReply().release());
        std::string msg("Put command received without timestamp set. "
                        "Distributor need to set timestamp to ensure equal "
                        "timestamps between storage nodes. Rejecting.");
        reply->setResult(api::ReturnCode(api::ReturnCode::REJECTED, msg));
        sendUp(reply);
        return true;
    }
    StorBucketDatabase::WrappedEntry entry(mapOperationToBucketAndDisk(
                *cmd, &cmd->getDocumentId()));
    if (entry.exist()) {
        handlePersistenceMessage(cmd, entry->disk);
    }
    return true;
}

bool
FileStorManager::onUpdate(const shared_ptr<api::UpdateCommand>& cmd)
{
    if (cmd->getTimestamp() == 0) {
        shared_ptr<api::StorageReply> reply(cmd->makeReply().release());
        std::string msg("Update command received without timestamp set. "
                        "Distributor need to set timestamp to ensure equal "
                        "timestamps between storage nodes. Rejecting.");
        reply->setResult(api::ReturnCode(api::ReturnCode::REJECTED, msg));
        sendUp(reply);
        return true;
    }
    StorBucketDatabase::WrappedEntry entry(mapOperationToBucketAndDisk(
                *cmd, &cmd->getDocumentId()));
    if (entry.exist()) {
        handlePersistenceMessage(cmd, entry->disk);
    }
    return true;
}

bool
FileStorManager::onGet(const shared_ptr<api::GetCommand>& cmd)
{
    StorBucketDatabase::WrappedEntry entry(mapOperationToBucketAndDisk(
                *cmd, &cmd->getDocumentId()));
    if (entry.exist()) {
        handlePersistenceMessage(cmd, entry->disk);
    }
    return true;
}

bool
FileStorManager::onRemove(const shared_ptr<api::RemoveCommand>& cmd)
{
    if (cmd->getTimestamp() == 0) {
        shared_ptr<api::StorageReply> reply(cmd->makeReply().release());
        std::string msg("Remove command received without timestamp set. "
                        "Distributor need to set timestamp to ensure equal "
                        "timestamps between storage nodes. Rejecting.");
        reply->setResult(api::ReturnCode(api::ReturnCode::REJECTED, msg));
        sendUp(reply);
        return true;
    }
    StorBucketDatabase::WrappedEntry entry(mapOperationToBucketAndDisk(
                *cmd, &cmd->getDocumentId()));
    if (entry.exist()) {
        handlePersistenceMessage(cmd, entry->disk);
    }
    return true;
}

bool
FileStorManager::onRevert(const shared_ptr<api::RevertCommand>& cmd)
{
    StorBucketDatabase::WrappedEntry entry(mapOperationToBucketAndDisk(
                *cmd, 0));
    if (entry.exist()) {
        handlePersistenceMessage(cmd, entry->disk);
    }
    return true;
}

bool
FileStorManager::onMultiOperation(const std::shared_ptr<api::MultiOperationCommand>& cmd)
{
    StorBucketDatabase::WrappedEntry entry(mapOperationToBucketAndDisk(*cmd, 0));
    if (entry.exist()) {
        handlePersistenceMessage(cmd, entry->disk);
    }
    return true;
}

bool
FileStorManager::onBatchPutRemove(const std::shared_ptr<api::BatchPutRemoveCommand>& cmd)
{
    StorBucketDatabase::WrappedEntry entry(mapOperationToBucketAndDisk(*cmd, 0));
    if (entry.exist()) {
        handlePersistenceMessage(cmd, entry->disk);
    }
    return true;
}

bool
FileStorManager::onRemoveLocation(const std::shared_ptr<api::RemoveLocationCommand>& cmd)
{
    StorBucketDatabase::WrappedEntry entry(mapOperationToDisk(*cmd, cmd->getBucket()));
    if (entry.exist()) {
        handlePersistenceMessage(cmd, entry->disk);
    }
    return true;
}

bool
FileStorManager::onStatBucket(const std::shared_ptr<api::StatBucketCommand>& cmd)
{
    StorBucketDatabase::WrappedEntry entry(mapOperationToDisk(*cmd, cmd->getBucket()));
    if (entry.exist()) {
        handlePersistenceMessage(cmd, entry->disk);
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
            LOG(debug,
                "Got create bucket request for %s which already exists: %s",
                cmd->getBucketId().toString().c_str(),
                entry->getBucketInfo().toString().c_str());
            code = api::ReturnCode(api::ReturnCode::EXISTS,
                                   "Bucket already exist");
        } else {
            entry->disk = _component.getIdealPartition(cmd->getBucket());
            if (_partitions[entry->disk].isUp()) {
                // Newly created buckets are ready but not active, unless
                // explicitly marked as such by the distributor.
                entry->setBucketInfo(api::BucketInfo(
                        0, 0, 0, 0, 0, true, cmd->getActive()));
                cmd->setPriority(0);
                handlePersistenceMessage(cmd, entry->disk);
                entry.write();
                LOG(debug, "Created bucket %s on disk %d (node index is %d)",
                    cmd->getBucketId().toString().c_str(),
                    entry->disk, _component.getIndex());
                return true;
            } else {
                entry.remove();
                code = api::ReturnCode(
                        api::ReturnCode::IO_FAILURE,
                        vespalib::make_string(
                            "Trying to create bucket %s on disabled disk %d",
                            cmd->getBucketId().toString().c_str(),
                            entry->disk));
            }
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
    uint16_t disk;
    {
        document::Bucket bucket(cmd->getBucket());
        StorBucketDatabase::WrappedEntry entry(_component.getBucketDatabase(bucket.getBucketSpace()).get(bucket.getBucketId(),
                                                                                  "FileStorManager::onDeleteBucket"));
        if (!entry.exist()) {
            LOG(debug, "%s was already deleted", cmd->getBucketId().toString().c_str());
            std::shared_ptr<api::StorageReply> reply(cmd->makeReply().release());
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

            LOG(debug, "Rejecting bucket delete: %s", ost.str().c_str());
            std::shared_ptr<api::StorageReply> reply(cmd->makeReply().release());
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
        handlePersistenceMessage(cmd, entry->disk);
        disk = entry->disk;
        entry.remove();
    }
    _filestorHandler->failOperations(cmd->getBucket(), disk,
                                     api::ReturnCode(api::ReturnCode::BUCKET_DELETED,
                                                     vespalib::make_string("Bucket %s about to be deleted anyway",
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
    StorBucketDatabase::WrappedEntry entry(ensureConsistentBucket(cmd->getBucket(), *cmd,
                                                                  "FileStorManager::onMergeBucket"));
    if (!entry.exist()) {
        return true;
    }

    if (!entry.preExisted()) {
        entry->disk = _component.getIdealPartition(cmd->getBucket());
        if (_partitions[entry->disk].isUp()) {
            entry->info = api::BucketInfo(0, 0, 0, 0, 0, true, false);
            LOG(debug, "Created bucket %s on disk %d (node index is %d) due to merge being received.",
                cmd->getBucketId().toString().c_str(), entry->disk, _component.getIndex());
                // Call before writing bucket entry as we need to have bucket
                // lock while calling
            handlePersistenceMessage(cmd, entry->disk);
            entry.write();
        } else {
            entry.remove();
            api::ReturnCode code(api::ReturnCode::IO_FAILURE,
                    vespalib::make_string(
                            "Trying to perform merge %s whose bucket belongs on target disk %d, which is down. Cluster state version of command is %d, our system state version is %d",
                            cmd->toString().c_str(), entry->disk, cmd->getClusterStateVersion(),
                            _component.getStateUpdater().getClusterStateBundle()->getVersion()));
            LOGBT(debug, cmd->getBucketId().toString(), "%s", code.getMessage().c_str());
            api::MergeBucketReply::SP reply(new api::MergeBucketReply(*cmd));
            reply->setResult(code);
            sendUp(reply);
            return true;
        }
    } else {
        handlePersistenceMessage(cmd, entry->disk);
    }
    return true;
}

bool
FileStorManager::onGetBucketDiff(
        const shared_ptr<api::GetBucketDiffCommand>& cmd)
{
    StorBucketDatabase::WrappedEntry entry(
            ensureConsistentBucket(cmd->getBucket(),
                                   *cmd,
                                   "FileStorManager::onGetBucketDiff"));
    if (!entry.exist()) {
        return true;
    }
    if (!entry.preExisted()) {
        entry->disk = _component.getIdealPartition(cmd->getBucket());
        if (_partitions[entry->disk].isUp()) {
            LOG(debug, "Created bucket %s on disk %d (node index is %d) due "
                       "to get bucket diff being received.",
                cmd->getBucketId().toString().c_str(),
                entry->disk, _component.getIndex());
            entry->info.setTotalDocumentSize(0);
            entry->info.setUsedFileSize(0);
            entry->info.setReady(true);
                // Call before writing bucket entry as we need to have bucket
                // lock while calling
            handlePersistenceMessage(cmd, entry->disk);
            entry.write();
        } else {
            entry.remove();
            api::ReturnCode code(api::ReturnCode::IO_FAILURE,
                        vespalib::make_string(
                            "Trying to merge non-existing bucket %s, which "
                            "can't be created because target disk %d is down",
                            cmd->getBucketId().toString().c_str(),
                            entry->disk));
            LOGBT(warning, cmd->getBucketId().toString(),
                  "%s", code.getMessage().c_str());
            api::GetBucketDiffReply::SP reply(
                    new api::GetBucketDiffReply(*cmd));
            reply->setResult(code);
            sendUp(reply);
            return true;
        }
    } else {
        handlePersistenceMessage(cmd, entry->disk);
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
        replyDroppedOperation(msg, bucket, api::ReturnCode::ABORTED,
                              "bucket became inconsistent during merging");
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
        handlePersistenceMessage(reply, entry->disk);
    }
    return true;
}

bool
FileStorManager::onApplyBucketDiff(const shared_ptr<api::ApplyBucketDiffCommand>& cmd)
{
    StorBucketDatabase::WrappedEntry entry(mapOperationToDisk(*cmd, cmd->getBucket()));
    if (validateApplyDiffCommandBucket(*cmd, entry)) {
        handlePersistenceMessage(cmd, entry->disk);
    }
    return true;
}

bool
FileStorManager::onApplyBucketDiffReply(const shared_ptr<api::ApplyBucketDiffReply>& reply)
{
    StorBucketDatabase::WrappedEntry entry(mapOperationToDisk(
                *reply, reply->getBucket()));
    if (validateDiffReplyBucket(entry, reply->getBucket())) {
        handlePersistenceMessage(reply, entry->disk);
    }
    return true;
}

bool
FileStorManager::onJoinBuckets(const std::shared_ptr<api::JoinBucketsCommand>& cmd)
{
    document::Bucket bucket(cmd->getBucket());
    StorBucketDatabase::WrappedEntry entry(_component.getBucketDatabase(bucket.getBucketSpace()).get(
                bucket.getBucketId(), "FileStorManager::onJoinBuckets"));
    uint16_t disk;
    if (entry.exist()) {
        disk = entry->disk;
    } else {
        disk = _component.getPreferredAvailablePartition(bucket);
    }
    return handlePersistenceMessage(cmd, disk);
}

bool
FileStorManager::onSplitBucket(const std::shared_ptr<api::SplitBucketCommand>& cmd)
{
    StorBucketDatabase::WrappedEntry entry(mapOperationToDisk(*cmd, cmd->getBucket()));
    if (entry.exist()) {
        handlePersistenceMessage(cmd, entry->disk);
    }
    return true;
}

bool
FileStorManager::onSetBucketState(
        const std::shared_ptr<api::SetBucketStateCommand>& cmd)
{
    StorBucketDatabase::WrappedEntry entry(mapOperationToDisk(*cmd, cmd->getBucket()));
    if (entry.exist()) {
        handlePersistenceMessage(cmd, entry->disk);
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
            handlePersistenceMessage(cmd, entry->disk);
        }
        return true;
    }
    case CreateIteratorCommand::ID:
    {
        shared_ptr<CreateIteratorCommand> cmd(std::static_pointer_cast<CreateIteratorCommand>(msg));
        StorBucketDatabase::WrappedEntry entry(mapOperationToDisk(*cmd, cmd->getBucket()));
        if (entry.exist()) {
            handlePersistenceMessage(cmd, entry->disk);
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
        handlePersistenceMessage(cmd, cmd->getPartition());
        return true;
    }
    case ReadBucketInfo::ID:
    {
        shared_ptr<ReadBucketInfo> cmd(std::static_pointer_cast<ReadBucketInfo>(msg));
        StorBucketDatabase::WrappedEntry entry(mapOperationToDisk(*cmd, cmd->getBucket()));
        if (entry.exist()) {
            handlePersistenceMessage(cmd, entry->disk);
        }
        return true;
    }
    case InternalBucketJoinCommand::ID:
    {
        shared_ptr<InternalBucketJoinCommand> cmd(std::static_pointer_cast<InternalBucketJoinCommand>(msg));
        StorBucketDatabase::WrappedEntry entry(mapOperationToDisk(*cmd, cmd->getBucket()));
        if (entry.exist()) {
            handlePersistenceMessage(cmd, entry->disk);
        }
        return true;
    }
    case RepairBucketCommand::ID:
    {
        shared_ptr<RepairBucketCommand> cmd(std::static_pointer_cast<RepairBucketCommand>(msg));
        StorBucketDatabase::WrappedEntry entry(mapOperationToDisk(*cmd, cmd->getBucket()));
        if (entry.exist()) {
            handlePersistenceMessage(cmd, entry->disk);
        }
        return true;
    }
    case BucketDiskMoveCommand::ID:
    {
        shared_ptr<BucketDiskMoveCommand> cmd(std::static_pointer_cast<BucketDiskMoveCommand>(msg));
        StorBucketDatabase::WrappedEntry entry(mapOperationToDisk(*cmd, cmd->getBucket()));
        if (entry.exist()) {
            handlePersistenceMessage(cmd, entry->disk);
        }
        return true;
    }
    case RecheckBucketInfoCommand::ID:
    {
        shared_ptr<RecheckBucketInfoCommand> cmd(std::static_pointer_cast<RecheckBucketInfoCommand>(msg));
        StorBucketDatabase::WrappedEntry entry(mapOperationToDisk(*cmd, cmd->getBucket()));
        if (entry.exist()) {
            handlePersistenceMessage(cmd, entry->disk);
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
    sendReply(api::StorageReply::SP(cmd->makeReply().release()));
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
    for (uint32_t i = 0; i < _disks.size(); ++i) {
        for (uint32_t j = 0; j < _disks[i].size(); ++j) {
            if (_disks[i][j].get() != NULL) {
                _disks[i][j]->flush();
                LOG(debug, "flushed disk[%d][%d]", i, j);
            }
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
FileStorManager::reportHtmlStatus(std::ostream& out,
                                  const framework::HttpUrlPath& path) const
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

    if (_disks.size()) {
        out << "<p>Using " << _disks[0].size() << " threads per disk</p>\n";
    }

    _filestorHandler->getStatus(out, path);
}

bool
FileStorManager::isMerging(const document::Bucket& bucket) const
{
    return _filestorHandler->isMerging(bucket);
}

namespace {
    struct Deactivator {
        StorBucketDatabase::Decision operator()(document::BucketId::Type, StorBucketDatabase::Entry& data)
        {
            data.info.setActive(false);
            return StorBucketDatabase::UPDATE;
        }
    };
}

void
FileStorManager::updateState()
{
    auto clusterStateBundle = _component.getStateUpdater().getClusterStateBundle();
    lib::ClusterState::CSP state(clusterStateBundle->getBaselineClusterState());
    lib::Node node(_component.getNodeType(), _component.getIndex());
    bool nodeUp = state->getNodeState(node).getState().oneOf("uir");

    LOG(debug, "FileStorManager received cluster state '%s'", state->toString().c_str());
        // If edge where we go down
    if (_nodeUpInLastNodeStateSeenByProvider && !nodeUp) {
        LOG(debug, "Received cluster state where this node is down; de-activating all buckets in database");
        Deactivator deactivator;
        _component.getBucketSpaceRepo().forEachBucket(deactivator, "FileStorManager::updateState");
    }
    for (const auto &elem : _component.getBucketSpaceRepo()) {
        BucketSpace bucketSpace(elem.first);
        spi::ClusterState spiState(*elem.second->getClusterState(), _component.getIndex(), *elem.second->getDistribution());
        _provider->setClusterState(bucketSpace, spiState);
    }
    _nodeUpInLastNodeStateSeenByProvider = nodeUp;
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

} // storage
