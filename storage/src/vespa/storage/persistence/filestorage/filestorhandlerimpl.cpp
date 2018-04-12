// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "filestorhandlerimpl.h"
#include "filestormetrics.h"
#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/storage/bucketdb/storbucketdb.h>
#include <vespa/storage/common/bucketmessages.h>
#include <vespa/storage/common/statusmessages.h>
#include <vespa/storage/common/bucketoperationlogger.h>
#include <vespa/storage/common/messagebucket.h>
#include <vespa/storage/persistence/messages.h>
#include <vespa/storageapi/message/stat.h>
#include <vespa/storageapi/message/batch.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/util/exceptions.h>

#include <vespa/log/log.h>
LOG_SETUP(".persistence.filestor.handler.impl");

using document::BucketSpace;

namespace storage {

FileStorHandlerImpl::FileStorHandlerImpl(uint32_t numStripes, MessageSender& sender, FileStorMetrics& metrics,
                                         const spi::PartitionStateList& partitions,
                                         ServiceLayerComponentRegister& compReg)
    : _partitions(partitions),
      _component(compReg, "filestorhandlerimpl"),
      _diskInfo(),
      _messageSender(sender),
      _bucketIdFactory(_component.getBucketIdFactory()),
      _getNextMessageTimeout(100),
      _paused(false)
{
    _diskInfo.reserve(_component.getDiskCount());
    for (uint32_t i(0); i < _component.getDiskCount(); i++) {
        _diskInfo.emplace_back(*this, sender, numStripes);
    }
    for (uint32_t i=0; i<_diskInfo.size(); ++i) {
        _diskInfo[i].metrics = metrics.disks[i].get();
        assert(_diskInfo[i].metrics != 0);
        uint32_t j(0);
        for (Stripe & stripe : _diskInfo[i].getStripes()) {
            stripe.setMetrics(metrics.disks[i]->stripes[j++].get());
        }
    }

    if (_diskInfo.size() == 0) {
        throw vespalib::IllegalArgumentException("No disks configured", VESPA_STRLOC);
    }
        // Add update hook, so we will get callbacks each 5 seconds to update
        // metrics.
    _component.registerMetricUpdateHook(*this, framework::SecondTime(5));
}

FileStorHandlerImpl::~FileStorHandlerImpl() = default;

void
FileStorHandlerImpl::addMergeStatus(const document::Bucket& bucket, MergeStatus::SP status)
{
    vespalib::LockGuard mlock(_mergeStatesLock);
    if (_mergeStates.find(bucket) != _mergeStates.end()) {;
        LOG(warning, "A merge status already existed for %s. Overwriting it.", bucket.toString().c_str());
    }
    _mergeStates[bucket] = status;
}

MergeStatus&
FileStorHandlerImpl::editMergeStatus(const document::Bucket& bucket)
{
    vespalib::LockGuard mlock(_mergeStatesLock);
    MergeStatus::SP status = _mergeStates[bucket];
    if (status.get() == 0) {
        throw vespalib::IllegalStateException("No merge state exist for " + bucket.toString(), VESPA_STRLOC);
    }
    return *status;
}

bool
FileStorHandlerImpl::isMerging(const document::Bucket& bucket) const
{
    vespalib::LockGuard mlock(_mergeStatesLock);
    return (_mergeStates.find(bucket) != _mergeStates.end());
}

uint32_t
FileStorHandlerImpl::getNumActiveMerges() const
{
    vespalib::LockGuard mlock(_mergeStatesLock);
    return _mergeStates.size();
}

void
FileStorHandlerImpl::clearMergeStatus(const document::Bucket& bucket, const api::ReturnCode* code)
{
    vespalib::LockGuard mlock(_mergeStatesLock);
    auto it = _mergeStates.find(bucket);
    if (it == _mergeStates.end()) {
        if (code != 0) {
            LOG(debug, "Merge state not present at the time of clear. "
                "Could not fail merge of bucket %s with code %s.",
                bucket.toString().c_str(), code->toString().c_str());
        } else {
            LOG(debug, "No merge state to clear for bucket %s.",
                bucket.toString().c_str());
        }
        return;
    }
    if (code != 0) {
        MergeStatus::SP statusPtr(it->second);
        assert(statusPtr.get());
        MergeStatus& status(*statusPtr);
        if (status.reply.get()) {
            status.reply->setResult(*code);
            LOG(debug, "Aborting merge. Replying merge of %s with code %s.",
                bucket.toString().c_str(), code->toString().c_str());
            _messageSender.sendReply(status.reply);
        }
        if (status.pendingGetDiff.get()) {
            status.pendingGetDiff->setResult(*code);
            LOG(debug, "Aborting merge. Replying getdiff of %s with code %s.",
                bucket.toString().c_str(), code->toString().c_str());
            _messageSender.sendReply(status.pendingGetDiff);
        }
        if (status.pendingApplyDiff.get()) {
            status.pendingApplyDiff->setResult(*code);
            LOG(debug, "Aborting merge. Replying applydiff of %s with code %s.",
                bucket.toString().c_str(), code->toString().c_str());
            _messageSender.sendReply(status.pendingApplyDiff);
        }
    }
    _mergeStates.erase(bucket);
}

void
FileStorHandlerImpl::flush(bool killPendingMerges)
{
    for (uint32_t i=0; i<_diskInfo.size(); ++i) {
        LOG(debug, "Wait until queues and bucket locks released for disk '%d'", i);
        _diskInfo[i].flush();
        LOG(debug, "All queues and bucket locks released for disk '%d'", i);
    }

    if (killPendingMerges) {
        api::ReturnCode code(api::ReturnCode::ABORTED, "Storage node is shutting down");
        for (auto & entry : _mergeStates)
        {
            MergeStatus& s(*entry.second);
            if (s.pendingGetDiff.get() != 0) {
                s.pendingGetDiff->setResult(code);
                _messageSender.sendReply(s.pendingGetDiff);
            }
            if (s.pendingApplyDiff.get() != 0) {
                s.pendingApplyDiff->setResult(code);
                _messageSender.sendReply(s.pendingApplyDiff);
            }
            if (s.reply.get() != 0) {
                s.reply->setResult(code);
                _messageSender.sendReply(s.reply);
            }
        }
        _mergeStates.clear();
    }
}

void
FileStorHandlerImpl::reply(api::StorageMessage& msg, DiskState state) const
{
    if (!msg.getType().isReply()) {
        std::shared_ptr<api::StorageReply> rep = static_cast<api::StorageCommand&>(msg).makeReply();
        if (state == FileStorHandler::DISABLED) {
            rep->setResult(api::ReturnCode(api::ReturnCode::DISK_FAILURE, "Disk disabled"));
        } else {
            rep->setResult(api::ReturnCode(api::ReturnCode::ABORTED, "Shutting down storage node."));
        }
        _messageSender.sendReply(rep);
    }
}

void
FileStorHandlerImpl::setDiskState(uint16_t diskId, DiskState state)
{
    Disk& disk = _diskInfo[diskId];

    // Mark disk closed
    disk.setState(state);
    if (state != FileStorHandler::AVAILABLE) {
        disk.flush();
    }
}

FileStorHandler::DiskState
FileStorHandlerImpl::getDiskState(uint16_t disk) const
{
    return _diskInfo[disk].getState();
}

void
FileStorHandlerImpl::close()
{
    for (uint32_t i=0; i<_diskInfo.size(); ++i) {
        if (getDiskState(i) == FileStorHandler::AVAILABLE) {
            LOG(debug, "AVAILABLE -> CLOSED disk[%d]", i);
            setDiskState(i, FileStorHandler::CLOSED);
        }
        LOG(debug, "Closing disk[%d]", i);
        _diskInfo[i].broadcast();
        LOG(debug, "Closed disk[%d]", i);
    }
}

uint32_t
FileStorHandlerImpl::getQueueSize() const
{
    size_t count = 0;
    for (const auto & disk : _diskInfo) {
        count += disk.getQueueSize();
    }
    return count;
}

bool
FileStorHandlerImpl::schedule(const std::shared_ptr<api::StorageMessage>& msg, uint16_t diskId)
{
    assert(diskId < _diskInfo.size());
    Disk& disk(_diskInfo[diskId]);
    return disk.schedule(msg);
}

bool
FileStorHandlerImpl::messageMayBeAborted(const api::StorageMessage& msg)
{
    if (msg.getType().isReply()) {
        return false;
    }
    // Create/DeleteBucket have already updated the bucket database before
    // being scheduled and must be allowed through to avoid getting out of
    // sync between the service layer and the provider.
    switch (msg.getType().getId()) {
    case api::MessageType::PUT_ID:
    case api::MessageType::REMOVE_ID:
    case api::MessageType::REVERT_ID:
    case api::MessageType::MERGEBUCKET_ID:
    case api::MessageType::GETBUCKETDIFF_ID:
    case api::MessageType::APPLYBUCKETDIFF_ID:
    case api::MessageType::SPLITBUCKET_ID:
    case api::MessageType::JOINBUCKETS_ID:
    case api::MessageType::UPDATE_ID:
    case api::MessageType::REMOVELOCATION_ID:
    case api::MessageType::BATCHPUTREMOVE_ID:
    case api::MessageType::BATCHDOCUMENTUPDATE_ID:
    case api::MessageType::SETBUCKETSTATE_ID:
        return true;
    default:
        return false;
    }
}

void
FileStorHandlerImpl::abortQueuedCommandsForBuckets(Disk& disk, const AbortBucketOperationsCommand& cmd)
{
    api::ReturnCode abortedCode(api::ReturnCode::ABORTED,
                                "Sending distributor no longer owns bucket operation was bound to, "
                                "or storage node went down");
    auto aborted = disk.abort(cmd);
    for (auto & msgReply : aborted) {
        msgReply->setResult(abortedCode);
        _messageSender.sendReply(msgReply);
    }
}

void
FileStorHandlerImpl::abortQueuedOperations(const AbortBucketOperationsCommand& cmd)
{
    // Do queue clearing and active operation waiting in two passes
    // to allow disk threads to drain running operations in parallel.
    for (Disk & disk : _diskInfo) {
        abortQueuedCommandsForBuckets(disk, cmd);
    }
    for (Disk & disk : _diskInfo) {
        disk.waitInactive(cmd);
    }    
}

void
FileStorHandlerImpl::updateMetrics(const MetricLockGuard &)
{
    for (Disk & disk : _diskInfo) {
        vespalib::MonitorGuard lockGuard(_mergeStatesLock);
        disk.metrics->pendingMerges.addValue(_mergeStates.size());
        disk.metrics->queueSize.addValue(disk.getQueueSize());

        for (auto & entry : disk.metrics->averageQueueWaitingTime.getMetricMap()) {
            metrics::LoadType loadType(entry.first, "ignored");
            for (const auto & stripe : disk.metrics->stripes) {
                const auto & m = stripe->averageQueueWaitingTime[loadType];
                entry.second->addTotalValueWithCount(m.getTotal(), m.getCount());
            }
        }
    }
}

uint32_t
FileStorHandlerImpl::getNextStripeId(uint32_t disk) {
    return _diskInfo[disk].getNextStripeId();
}


FileStorHandler::LockedMessage &
FileStorHandlerImpl::getNextMessage(uint16_t diskId, uint32_t stripeId, FileStorHandler::LockedMessage& lck)
{
    document::Bucket bucket(lck.first->getBucket());

    LOG(spam, "Disk %d retrieving message for buffered bucket %s", diskId, bucket.getBucketId().toString().c_str());

    assert(diskId < _diskInfo.size());
    Disk&  disk(_diskInfo[diskId]);

    if (disk.isClosed()) {
        lck.second.reset();
        return lck;
    }

    return disk.getNextMessage(stripeId, lck);
}

bool
FileStorHandlerImpl::tryHandlePause(uint16_t disk) const
{
    if (isPaused()) {
        // Wait a single time to see if filestor gets unpaused.
        if (!_diskInfo[disk].isClosed()) {
            vespalib::MonitorGuard g(_pauseMonitor);
            g.wait(100);
        }
        return !isPaused();
    }
    return true;
}

bool
FileStorHandlerImpl::messageTimedOutInQueue(const api::StorageMessage& msg, uint64_t waitTime)
{
    if (msg.getType().isReply()) {
        return false; // Replies must always be processed and cannot time out.
    }
    return (waitTime >= static_cast<const api::StorageCommand&>(msg).getTimeout());
}

std::unique_ptr<api::StorageReply>
FileStorHandlerImpl::makeQueueTimeoutReply(api::StorageMessage& msg)
{
    assert(!msg.getType().isReply());
    std::unique_ptr<api::StorageReply> msgReply = static_cast<api::StorageCommand&>(msg).makeReply();
    msgReply->setResult(api::ReturnCode(api::ReturnCode::TIMEOUT, "Message waited too long in storage queue"));
    return msgReply;
}

FileStorHandler::LockedMessage
FileStorHandlerImpl::getNextMessage(uint16_t disk, uint32_t stripeId)
{
    assert(disk < _diskInfo.size());
    if (!tryHandlePause(disk)) {
        return {}; // Still paused, return to allow tick.
    }

    return _diskInfo[disk].getNextMessage(stripeId, _getNextMessageTimeout);
}

std::shared_ptr<FileStorHandler::BucketLockInterface>
FileStorHandlerImpl::Stripe::lock(const document::Bucket &bucket)
{
    vespalib::MonitorGuard guard(_lock);

    while (isLocked(guard, bucket)) {
        LOG(spam, "Contending for filestor lock for %s", bucket.getBucketId().toString().c_str());
        guard.wait(100);
    }

    auto locker = std::make_shared<BucketLock>(guard, *this, bucket, 255, api::MessageType::INTERNAL_ID, 0);

    guard.broadcast();
    return locker;
}

namespace {
    struct MultiLockGuard {
        std::map<uint16_t, vespalib::Monitor*> monitors;
        std::vector<std::shared_ptr<vespalib::MonitorGuard> > guards;

        MultiLockGuard() {}

        void addLock(vespalib::Monitor& monitor, uint16_t index) {
            monitors[index] = &monitor;
        }
        void lock() {
            for (auto & entry : monitors) {
                guards.push_back(std::make_shared<vespalib::MonitorGuard>(*entry.second));
            }
        }
    };
}

namespace {
    document::DocumentId getDocId(const api::StorageMessage& msg) {
        switch (msg.getType().getId()) {
            case api::MessageType::GET_ID:
                return static_cast<const api::GetCommand&>(msg).getDocumentId();
                break;
            case api::MessageType::PUT_ID:
                return static_cast<const api::PutCommand&>(msg).getDocumentId();
                break;
            case api::MessageType::UPDATE_ID:
                return static_cast<const api::UpdateCommand&>(msg).getDocumentId();
                break;
            case api::MessageType::REMOVE_ID:
                return static_cast<const api::RemoveCommand&>(msg).getDocumentId();
                break;
            default:
                assert(false);
                abort();
        }
    }
    uint32_t findCommonBits(document::BucketId a, document::BucketId b) {
        if (a.getUsedBits() > b.getUsedBits()) {
            a.setUsedBits(b.getUsedBits());
        } else {
            b.setUsedBits(a.getUsedBits());
        }
        for (uint32_t i=a.getUsedBits() - 1; i>0; --i) {
            if (a == b) return i + 1;
            a.setUsedBits(i);
            b.setUsedBits(i);
        }
        return (a == b ? 1 : 0);
    }
}

int
FileStorHandlerImpl::calculateTargetBasedOnDocId(const api::StorageMessage& msg, std::vector<RemapInfo*>& targets)
{
    document::DocumentId id(getDocId(msg));
    document::Bucket bucket(msg.getBucket().getBucketSpace(), _bucketIdFactory.getBucketId(id));

    for (uint32_t i = 0; i < targets.size(); i++) {
        if (targets[i]->bucket.getBucketId().getRawId() != 0 &&
            targets[i]->bucket.getBucketSpace() == bucket.getBucketSpace() &&
            targets[i]->bucket.getBucketId().contains(bucket.getBucketId())) {
            return i;
        }
    }

    return -1;
}

namespace {

const char *
splitOrJoin(FileStorHandlerImpl::Operation op) {
    return (op == FileStorHandlerImpl::Operation::SPLIT) ? "Bucket was just split" : "Bucket was just joined";
}

}

document::Bucket
FileStorHandlerImpl::remapMessage(api::StorageMessage& msg, const document::Bucket& source, Operation op,
                                  std::vector<RemapInfo*>& targets, uint16_t& targetDisk, api::ReturnCode& returnCode)
{
    document::Bucket newBucket = source;

    switch (msg.getType().getId()) {
    case api::MessageType::GET_ID:
    case api::MessageType::PUT_ID:
    case api::MessageType::UPDATE_ID:
    case api::MessageType::REMOVE_ID:
        // Move to correct queue
    {
        api::BucketCommand& cmd(static_cast<api::BucketCommand&>(msg));

        if (cmd.getBucket() == source) {
            if (op == SPLIT) {
                int idx = calculateTargetBasedOnDocId(msg, targets);

                if (idx > -1) {
                    cmd.remapBucketId(targets[idx]->bucket.getBucketId());
                    targets[idx]->foundInQueue = true;
                    targetDisk = targets[idx]->diskIndex;
#if defined(ENABLE_BUCKET_OPERATION_LOGGING)
                    {
                        vespalib::string desc = vespalib::make_string(
                                "Remapping %s from %s to %s, targetDisk = %u",
                                cmd.toString().c_str(), source.toString().c_str(),
                                targets[idx]->bid.toString().c_str(), targetDisk);
                        LOG_BUCKET_OPERATION_NO_LOCK(source, desc);
                        LOG_BUCKET_OPERATION_NO_LOCK(targets[idx]->bid, desc);
                    }
#endif
                    newBucket = targets[idx]->bucket;
                } else {
                    document::DocumentId did(getDocId(msg));
                    document::BucketId bucket = _bucketIdFactory.getBucketId(did);
                    uint32_t commonBits(findCommonBits(targets[0]->bucket.getBucketId(), bucket));
                    if (commonBits < source.getBucketId().getUsedBits()) {
                        std::ostringstream ost;
                        ost << bucket << " belongs in neither "
                            << targets[0]->bucket.getBucketId() << " nor " << targets[1]->bucket.getBucketId()
                            << ". Cannot remap it after split. It "
                            << "did not belong in the original "
                            << "bucket " << source.getBucketId();
                        LOG(error, "Error remapping %s after split %s",
                            cmd.getType().toString().c_str(), ost.str().c_str());
                        returnCode = api::ReturnCode(api::ReturnCode::REJECTED, ost.str());
                    } else {
                        std::ostringstream ost;
                        assert(targets.size() == 2);
                        ost << "Bucket " << source.getBucketId() << " was split and "
                            << "neither bucket " << targets[0]->bucket.getBucketId() << " nor "
                            << targets[1]->bucket.getBucketId() << " fit for this operation. "
                            << "Failing operation so distributor can create "
                            << "bucket on correct node.";
                        LOG(debug, "%s", ost.str().c_str());
                        returnCode = api::ReturnCode(api::ReturnCode::BUCKET_DELETED, ost.str());
                    }
                }
            } else {
                LOG(debug, "Remapping %s operation to bucket %s",
                    cmd.toString().c_str(), targets[0]->bucket.getBucketId().toString().c_str());
                cmd.remapBucketId(targets[0]->bucket.getBucketId());
                newBucket = targets[0]->bucket;
                targetDisk = targets[0]->diskIndex;
#ifdef ENABLE_BUCKET_OPERATION_LOGGING
                {
                    vespalib::string desc = vespalib::make_string(
                            "Remapping %s from %s to %s, targetDisk = %u",
                            cmd.toString().c_str(), source.toString().c_str(),
                            targets[0]->bid.toString().c_str(), targetDisk);
                    LOG_BUCKET_OPERATION_NO_LOCK(source, desc);
                    LOG_BUCKET_OPERATION_NO_LOCK(targets[0]->bid, desc);
                }
#endif
            }
        } else {
            LOG(debug, "Did not remap %s with bucket %s from bucket %s",
                cmd.toString().c_str(), cmd.getBucketId().toString().c_str(), source.toString().c_str());
            assert(false);
        }
        break;
    }
    case api::MessageType::MERGEBUCKET_ID:
    case api::MessageType::GETBUCKETDIFF_ID:
    case api::MessageType::GETBUCKETDIFF_REPLY_ID:
    case api::MessageType::APPLYBUCKETDIFF_ID:
    case api::MessageType::APPLYBUCKETDIFF_REPLY_ID:
        // Move to correct queue including filestor thread state
        // if op == MOVE. If op != MOVE, fail with bucket not found
        // and clear filestor thread state
    {
        api::BucketCommand& cmd(static_cast<api::BucketCommand&>(msg));
        if (cmd.getBucket() == source) {
            if (op != MOVE) {
                std::ostringstream ost;
                ost << "Bucket " << (op == SPLIT ? "split" : "joined")
                    << ". Cannot remap merge, so aborting it";
                api::ReturnCode code(api::ReturnCode::BUCKET_DELETED, ost.str());
                clearMergeStatus(cmd.getBucket(), &code);
            }
        }
        // Follow onto next to move queue or fail
    }
    //@fallthrough@
    case api::MessageType::SPLITBUCKET_ID:
        // Move to correct queue if op == MOVE
        // Fail with bucket not found if op is JOIN
        // Ok if op is SPLIT, as we have already done as requested.
    {
        api::BucketCommand& cmd(static_cast<api::BucketCommand&>(msg));
        if (cmd.getBucket() == source) {
            if (op == MOVE) {
                targetDisk = targets[0]->diskIndex;
            } else if (op == SPLIT) {
                returnCode = api::ReturnCode(api::ReturnCode::BUCKET_DELETED, "Bucket split while operation enqueued");
            } else {
                returnCode = api::ReturnCode(api::ReturnCode::BUCKET_DELETED, "Bucket was just joined");
            }
        }
        break;
    }
    case api::MessageType::STAT_ID:
    case api::MessageType::BATCHPUTREMOVE_ID:
    case api::MessageType::REVERT_ID:
    case api::MessageType::REMOVELOCATION_ID:
    case api::MessageType::SETBUCKETSTATE_ID:
    {
        // Move to correct queue if op == MOVE
        // Fail with bucket not found if op != MOVE
        api::BucketCommand& cmd(static_cast<api::BucketCommand&>(msg));
        if (cmd.getBucket() == source) {
            if (op == MOVE) {
                targetDisk = targets[0]->diskIndex;
            } else {
                returnCode = api::ReturnCode(api::ReturnCode::BUCKET_DELETED, splitOrJoin(op));
            }
        }
        break;
    }
    case api::MessageType::CREATEBUCKET_ID:
    case api::MessageType::DELETEBUCKET_ID:
    case api::MessageType::JOINBUCKETS_ID:
        // Move to correct queue if op == MOVE. Otherwise ignore.
    {
        api::BucketCommand& cmd(static_cast<api::BucketCommand&>(msg));
        if (cmd.getBucket() == source) {
            if (op == MOVE) {
                targetDisk = targets[0]->diskIndex;
            }
        }
        break;
    }
    case api::MessageType::INTERNAL_ID:
    {
        const api::InternalCommand& icmd(static_cast<const api::InternalCommand&>(msg));
        document::Bucket bucket;
        switch(icmd.getType()) {
        case RequestStatusPage::ID:
            // Ignore
            break;
        case CreateIteratorCommand::ID:
            bucket = static_cast<CreateIteratorCommand&>(msg).getBucket();
            // Move to correct queue if op == MOVE
            // Fail with bucket not found if op != MOVE
            if (bucket == source) {
                if (op == MOVE) {
                    targetDisk = targets[0]->diskIndex;
                } else {
                    returnCode = api::ReturnCode(api::ReturnCode::BUCKET_DELETED, splitOrJoin(op));
                }
            }
            break;
        case GetIterCommand::ID:
            bucket = static_cast<GetIterCommand&>(msg).getBucket();
            //@fallthrough@
        case RepairBucketCommand::ID:
            if (bucket.getBucketId().getRawId() == 0) {
                bucket = static_cast<RepairBucketCommand&>(msg).getBucket();
            }
            // Move to correct queue if op == MOVE
            // Fail with bucket not found if op != MOVE
            if (bucket == source) {
                if (op == MOVE) {
                    targetDisk = targets[0]->diskIndex;
                } else {
                    returnCode = api::ReturnCode(api::ReturnCode::BUCKET_DELETED, splitOrJoin(op));
                }
            }
            break;
        case BucketDiskMoveCommand::ID:
            // Fail bucket not found if op != MOVE
            // Fail and log error if op == MOVE
        {
            api::BucketCommand& cmd(static_cast<api::BucketCommand&>(msg));
            if (cmd.getBucket() == source) {
                if (op == MOVE) {
                    returnCode = api::ReturnCode(api::ReturnCode::INTERNAL_FAILURE,
                                                 "Multiple bucket disk move commands pending at the same time "
                                                 " towards bucket " + source.toString());
                } else {
                    returnCode = api::ReturnCode(api::ReturnCode::BUCKET_DELETED, splitOrJoin(op));
                }
            }
            break;
        }
        case ReadBucketInfo::ID:
        case RecheckBucketInfoCommand::ID:
        {
            LOG(debug, "While remapping load for bucket %s for reason %u, "
                       "we abort read bucket info request for this bucket.",
                source.getBucketId().toString().c_str(), op);
            break;
        }
        case InternalBucketJoinCommand::ID:
        default:
            // Fail and log error
        {
            LOG(error, "Attempted (and failed) to remap %s which should not be processed at this time",
                msg.toString(true).c_str());
            returnCode = api::ReturnCode(api::ReturnCode::INTERNAL_FAILURE,
                                         "No such message should be processed at this time.");
            break;
        }
        }
        break;
    }
    default:
    {
        returnCode = api::ReturnCode(api::ReturnCode::INTERNAL_FAILURE, "Unknown message type in persistence layer");
        LOG(error, "Unknown message type in persistence layer: %s", msg.toString().c_str());
    }
    } // End of switch

    return newBucket;
}

void
FileStorHandlerImpl::remapQueueNoLock(Disk& from, const RemapInfo& source,
                                      std::vector<RemapInfo*>& targets, Operation op)
{
    BucketIdx& idx(from.stripe(source.bucket).exposeBucketIdx());
    auto range(idx.equal_range(source.bucket));

    std::vector<MessageEntry> entriesFound;

    // Find all the messages for the given bucket.
    for (BucketIdx::iterator i = range.first; i != range.second; ++i) {
        assert(i->_bucket == source.bucket);

        entriesFound.push_back(std::move(*i));
    }

    // Remove them
    idx.erase(range.first, range.second);

    // Reinsert all that can be remapped.
    for (uint32_t i = 0; i < entriesFound.size(); ++i) {
        // If set to something other than source.diskIndex, move this message
        // to that queue.
        MessageEntry& entry = entriesFound[i];
        uint16_t targetDisk = source.diskIndex;

        // If not OK, reply to this message with the following message
        api::ReturnCode returnCode(api::ReturnCode::OK);
        api::StorageMessage& msg(*entry._command);
        assert(entry._bucket == source.bucket);

        document::Bucket bucket = remapMessage(msg, source.bucket, op, targets, targetDisk, returnCode);

        if (returnCode.getResult() != api::ReturnCode::OK) {
            // Fail message if errorcode set
            if (!msg.getType().isReply()) {
                std::shared_ptr<api::StorageReply> rep = static_cast<api::StorageCommand&>(msg).makeReply();
                LOG(spam, "Sending reply %s because remapping failed: %s",
                    msg.toString().c_str(), returnCode.toString().c_str());

                rep->setResult(returnCode);
                _messageSender.sendReply(rep);
            }
        } else {
            entry._bucket = bucket;
            // Move to correct disk queue if needed
            _diskInfo[targetDisk].stripe(bucket).exposeQueue().emplace_back(std::move(entry));
        }
    }

}

void
FileStorHandlerImpl::remapQueue(const RemapInfo& source, RemapInfo& target, Operation op)
{
    // Use a helper class to lock to solve issue that some buckets might be
    // the same bucket. Will fix order if we accept wrong order later.
    MultiLockGuard guard;

    Disk& from(_diskInfo[source.diskIndex]);
    guard.addLock(from.stripe(source.bucket).exposeLock(), source.diskIndex);

    Disk& to1(_diskInfo[target.diskIndex]);
    if (target.bucket.getBucketId().getRawId() != 0) {
        guard.addLock(to1.stripe(target.bucket).exposeLock(), target.diskIndex);
    }

    std::vector<RemapInfo*> targets;
    targets.push_back(&target);

    guard.lock();

    remapQueueNoLock(from, source, targets, op);
}

void
FileStorHandlerImpl::remapQueue(const RemapInfo& source, RemapInfo& target1, RemapInfo& target2, Operation op)
{
    // Use a helper class to lock to solve issue that some buckets might be
    // the same bucket. Will fix order if we accept wrong order later.
    MultiLockGuard guard;

    Disk& from(_diskInfo[source.diskIndex]);
    guard.addLock(from.stripe(source.bucket).exposeLock(), source.diskIndex);

    Disk& to1(_diskInfo[target1.diskIndex]);
    if (target1.bucket.getBucketId().getRawId() != 0) {
        guard.addLock(to1.stripe(target1.bucket).exposeLock(), target1.diskIndex);
    }

    Disk& to2(_diskInfo[target2.diskIndex]);
    if (target2.bucket.getBucketId().getRawId() != 0) {
        guard.addLock(to2.stripe(target2.bucket).exposeLock(), target2.diskIndex);
    }

    guard.lock();

    std::vector<RemapInfo*> targets;
    targets.push_back(&target1);
    targets.push_back(&target2);

    remapQueueNoLock(from, source, targets, op);
}

void
FileStorHandlerImpl::Stripe::failOperations(const document::Bucket &bucket, const api::ReturnCode& err)
{
    vespalib::MonitorGuard guard(_lock);

    BucketIdx& idx(bmi::get<2>(_queue));
    std::pair<BucketIdx::iterator, BucketIdx::iterator> range(idx.equal_range(bucket));

    for (auto iter = range.first; iter != range.second;) {
        // We want to post delete bucket to list before calling this
        // function in order to release bucket database lock. Thus we
        // cannot delete the delete bucket operation itself
        if (iter->_command->getType() != api::MessageType::DELETEBUCKET) {
            if (!iter->_command->getType().isReply()) {
                std::shared_ptr<api::StorageReply> msgReply = static_cast<api::StorageCommand&>(*iter->_command).makeReply();
                msgReply->setResult(err);
                _messageSender.sendReply(msgReply);
            }
            iter = idx.erase(iter);
        } else {
            ++iter;
        }
    }
}

void
FileStorHandlerImpl::sendCommand(const std::shared_ptr<api::StorageCommand>& msg)
{
    _messageSender.sendCommand(msg);
}

void
FileStorHandlerImpl::sendReply(const std::shared_ptr<api::StorageReply>& msg)
{
    _messageSender.sendReply(msg);
}

FileStorHandlerImpl::MessageEntry::MessageEntry(const std::shared_ptr<api::StorageMessage>& cmd,
                                                const document::Bucket &bucket)
    : _command(cmd),
      _timer(),
      _bucket(bucket),
      _priority(cmd->getPriority())
{ }


FileStorHandlerImpl::MessageEntry::MessageEntry(const MessageEntry& entry)
    : _command(entry._command),
      _timer(entry._timer),
      _bucket(entry._bucket),
      _priority(entry._priority)
{ }


FileStorHandlerImpl::MessageEntry::MessageEntry(MessageEntry && entry) noexcept
    : _command(std::move(entry._command)),
      _timer(entry._timer),
      _bucket(entry._bucket),
      _priority(entry._priority)
{ }

FileStorHandlerImpl::MessageEntry::~MessageEntry() { }

FileStorHandlerImpl::Disk::Disk(const FileStorHandlerImpl & owner, MessageSender & messageSender, uint32_t numThreads)
    : metrics(0),
      _nextStripeId(0),
      _stripes(numThreads, Stripe(owner, messageSender)),
      state(FileStorHandler::AVAILABLE)
{
    assert(numThreads > 0);
}

FileStorHandlerImpl::Disk::Disk(Disk && rhs) noexcept
    : metrics(std::move(rhs.metrics)),
      _nextStripeId(rhs._nextStripeId),
      _stripes(std::move(rhs._stripes)),
      state(rhs.state.load())
{ }

FileStorHandlerImpl::Disk::~Disk() = default;
FileStorHandlerImpl::Stripe::~Stripe() = default;

void
FileStorHandlerImpl::Disk::flush()
{
    for (auto & stripe : _stripes) {
        stripe.flush();
    }
}

void
FileStorHandlerImpl::Disk::broadcast()
{
    for (auto & stripe : _stripes) {
        stripe.broadcast();
    }
}

bool
FileStorHandlerImpl::Disk::schedule(const std::shared_ptr<api::StorageMessage>& msg)
{
    MessageEntry messageEntry(msg, getStorageMessageBucket(*msg));
    if (getState() == FileStorHandler::AVAILABLE) {
        stripe(messageEntry._bucket).schedule(std::move(messageEntry));
    } else {
        return false;
    }
    return true;
}

FileStorHandlerImpl::Stripe::Stripe(const FileStorHandlerImpl & owner, MessageSender & messageSender)
    : _owner(owner),
      _messageSender(messageSender)
{ }
FileStorHandler::LockedMessage
FileStorHandlerImpl::Stripe::getNextMessage(uint32_t timeout, Disk & disk)
{
    vespalib::MonitorGuard guard(_lock);
    // Try to grab a message+lock, immediately retrying once after a wait
    // if none can be found and then exiting if the same is the case on the
    // second attempt. This is key to allowing the run loop to register
    // ticks at regular intervals while not busy-waiting.
    for (int attempt = 0; (attempt < 2) && ! disk.isClosed() && !_owner.isPaused(); ++attempt) {
        PriorityIdx& idx(bmi::get<1>(_queue));
        PriorityIdx::iterator iter(idx.begin()), end(idx.end());

        while (iter != end && isLocked(guard, iter->_bucket)) {
            iter++;
        }
        if (iter != end) {
            return getMessage(guard, idx, iter);
        }
        if (attempt == 0) {
            guard.wait(timeout);
        }
    }
    return {}; // No message fetched.
}

FileStorHandler::LockedMessage &
FileStorHandlerImpl::Stripe::getNextMessage(FileStorHandler::LockedMessage& lck)
{
    const document::Bucket & bucket = lck.second->getBucket();
    vespalib::MonitorGuard guard(_lock);
    BucketIdx& idx = bmi::get<2>(_queue);
    std::pair<BucketIdx::iterator, BucketIdx::iterator> range = idx.equal_range(bucket);

    // No more for this bucket.
    if (range.first == range.second) {
        lck.second.reset();
        return lck;
    }

    api::StorageMessage & m(*range.first->_command);

    uint64_t waitTime(range.first->_timer.stop(_metrics->averageQueueWaitingTime[m.getLoadType()]));

    if (!messageTimedOutInQueue(m, waitTime)) {
        std::shared_ptr<api::StorageMessage> msg = std::move(range.first->_command);
        idx.erase(range.first);
        lck.second.swap(msg);
        guard.broadcast();
    } else {
        std::shared_ptr<api::StorageReply> msgReply = static_cast<api::StorageCommand&>(m).makeReply();
        idx.erase(range.first);
        guard.broadcast();
        guard.unlock();
        msgReply->setResult(api::ReturnCode(api::ReturnCode::TIMEOUT, "Message waited too long in storage queue"));
        _messageSender.sendReply(msgReply);

        lck.second.reset();
    }
    return lck;
}

FileStorHandler::LockedMessage
FileStorHandlerImpl::Stripe::getMessage(vespalib::MonitorGuard & guard, PriorityIdx & idx, PriorityIdx::iterator iter) {

    api::StorageMessage & m(*iter->_command);
    uint64_t waitTime(iter->_timer.stop(_metrics->averageQueueWaitingTime[m.getLoadType()]));

    std::shared_ptr<api::StorageMessage> msg = std::move(iter->_command);
    document::Bucket bucket(iter->_bucket);
    idx.erase(iter); // iter not used after this point.

    if (!messageTimedOutInQueue(*msg, waitTime)) {
        auto locker = std::make_unique<BucketLock>(guard, *this, bucket, msg->getPriority(),
                                                   msg->getType().getId(), msg->getMsgId());
        guard.unlock();
        return FileStorHandler::LockedMessage(std::move(locker), std::move(msg));
    } else {
        std::shared_ptr<api::StorageReply> msgReply(makeQueueTimeoutReply(*msg));
        guard.broadcast(); // XXX: needed here?
        guard.unlock();
        _messageSender.sendReply(msgReply);
        return {};
    }
}

void
FileStorHandlerImpl::Disk::waitUntilNoLocks() const
{
    for (const auto & stripe : _stripes) {
        stripe.waitUntilNoLocks();
    }
}

void
FileStorHandlerImpl::Stripe::waitUntilNoLocks() const
{
    vespalib::MonitorGuard lockGuard(_lock);
    while (!_lockedBuckets.empty()) {
        lockGuard.wait();
    }
}

void
FileStorHandlerImpl::Disk::waitInactive(const AbortBucketOperationsCommand& cmd) const {
    for (auto & stripe : _stripes) {
        stripe.waitInactive(cmd);
    }
}

void
FileStorHandlerImpl::Stripe::waitInactive(const AbortBucketOperationsCommand& cmd) const {
    vespalib::MonitorGuard lockGuard(_lock);
    while (hasActive(lockGuard, cmd)) {
        lockGuard.wait();
    }
}

bool
FileStorHandlerImpl::Stripe::hasActive(vespalib::MonitorGuard &, const AbortBucketOperationsCommand& cmd) const {
    for (auto& lockedBucket : _lockedBuckets) {
        if (cmd.shouldAbort(lockedBucket.first)) {
            LOG(spam, "Disk had active operation for aborted bucket %s, waiting for it to complete...",
                lockedBucket.first.toString().c_str());
            return true;
        }
    }
    return false;
}

std::vector<std::shared_ptr<api::StorageReply>>
FileStorHandlerImpl::Disk::abort(const AbortBucketOperationsCommand& cmd)
{
    std::vector<std::shared_ptr<api::StorageReply>> aborted;
    for (auto & stripe : _stripes) {
        stripe.abort(aborted, cmd);
    }
    return aborted;
}

void FileStorHandlerImpl::Stripe::abort(std::vector<std::shared_ptr<api::StorageReply>> & aborted,
                                        const AbortBucketOperationsCommand& cmd)
{
    vespalib::MonitorGuard lockGuard(_lock);
    for (auto it(_queue.begin()); it != _queue.end();) {
        api::StorageMessage& msg(*it->_command);
        if (messageMayBeAborted(msg) && cmd.shouldAbort(it->_bucket)) {
            aborted.emplace_back(static_cast<api::StorageCommand&>(msg).makeReply());
            it = _queue.erase(it);
        } else {
            ++it;
        }
    }
}

bool FileStorHandlerImpl::Stripe::schedule(MessageEntry messageEntry)
{
    vespalib::MonitorGuard lockGuard(_lock);
    _queue.emplace_back(std::move(messageEntry));
    lockGuard.broadcast();
    return true;
}

void
FileStorHandlerImpl::Stripe::flush()
{
    vespalib::MonitorGuard lockGuard(_lock);
    while (!(_queue.empty() && _lockedBuckets.empty())) {
        LOG(debug, "Still %ld in queue and %ld locked buckets", _queue.size(), _lockedBuckets.size());
        lockGuard.wait(100);
    }
}
bool
FileStorHandlerImpl::Stripe::isLocked(const vespalib::MonitorGuard &, const document::Bucket& bucket) const noexcept
{
    return (bucket.getBucketId().getRawId() != 0) && (_lockedBuckets.find(bucket) != _lockedBuckets.end());
}

uint32_t
FileStorHandlerImpl::Disk::getQueueSize() const noexcept
{
    size_t sum(0);
    for (const auto & stripe : _stripes) {
        sum += stripe.getQueueSize();
    }
    return sum;
}

uint32_t
FileStorHandlerImpl::getQueueSize(uint16_t disk) const
{
    return _diskInfo[disk].getQueueSize();
}

FileStorHandlerImpl::BucketLock::BucketLock(const vespalib::MonitorGuard & guard, Stripe& stripe,
                                            const document::Bucket &bucket, uint8_t priority,
                                            api::MessageType::Id msgType, api::StorageMessage::Id msgId)
    : _stripe(stripe),
      _bucket(bucket)
{
    (void) guard;
    if (_bucket.getBucketId().getRawId() != 0) {
        // Lock the bucket and wait until it is not the current operation for
        // the disk itself.
        _stripe.lock(guard, _bucket, Stripe::LockEntry(priority, msgType, msgId));
        LOG(debug, "Locked bucket %s with priority %u",
            bucket.getBucketId().toString().c_str(), priority);

        LOG_BUCKET_OPERATION_SET_LOCK_STATE(
                _bucket.getBucketId(), "acquired filestor lock", false,
                debug::BucketOperationLogger::State::BUCKET_LOCKED);
    }
}


FileStorHandlerImpl::BucketLock::~BucketLock()
{
    if (_bucket.getBucketId().getRawId() != 0) {
        _stripe.release(_bucket);
        LOG(debug, "Unlocked bucket %s", _bucket.getBucketId().toString().c_str());
        LOG_BUCKET_OPERATION_SET_LOCK_STATE(
                _bucket.getBucketId(), "released filestor lock", true,
                debug::BucketOperationLogger::State::BUCKET_UNLOCKED);
    }
}

std::string
FileStorHandlerImpl::Disk::dumpQueue() const
{
    std::ostringstream os;
    for (const Stripe & stripe : _stripes) {
        stripe.dumpQueue(os);
    }
    return os.str();
}

void
FileStorHandlerImpl::Disk::dumpQueueHtml(std::ostream & os) const
{
    for (const Stripe & stripe : _stripes) {
        stripe.dumpQueueHtml(os);
    }
}

void
FileStorHandlerImpl::Disk::dumpActiveHtml(std::ostream & os) const
{
    for (const Stripe & stripe : _stripes) {
        stripe.dumpActiveHtml(os);
    }
}

void
FileStorHandlerImpl::Stripe::dumpQueueHtml(std::ostream & os) const
{
    vespalib::MonitorGuard guard(_lock);

    const PriorityIdx& idx = bmi::get<1>(_queue);
    for (const auto & entry : idx) {
        os << "<li>" << entry._command->toString() << " (priority: "
           << (int)entry._command->getPriority() << ")</li>\n";
    }
}

void
FileStorHandlerImpl::Stripe::dumpActiveHtml(std::ostream & os) const
{
    uint32_t now = time(nullptr);
    vespalib::MonitorGuard guard(_lock);
    for (const auto & e : _lockedBuckets) {
        os << api::MessageType::get(e.second.msgType).getName() << ":" << e.second.msgId << " (" << e.first.getBucketId()
           << ") Running for " << (now - e.second.timestamp) << " secs<br/>\n";
    }
}

void
FileStorHandlerImpl::Stripe::dumpQueue(std::ostream & os) const
{
    vespalib::MonitorGuard guard(_lock);

    const PriorityIdx& idx = bmi::get<1>(_queue);
    for (const auto & entry : idx) {
        os << entry._bucket.getBucketId() << ": " << entry._command->toString() << " (priority: "
           << (int)entry._command->getPriority() << ")\n";
    }
}

void
FileStorHandlerImpl::getStatus(std::ostream& out, const framework::HttpUrlPath& path) const
{
    bool verbose = path.hasAttribute("verbose");
    out << "<h1>Filestor handler</h1>\n";
    for (uint32_t i=0; i<_diskInfo.size(); ++i) {
        out << "<h2>Disk " << i << "</h2>\n";
        const Disk& disk(_diskInfo[i]);
        out << "Queue size: " << disk.getQueueSize() << "<br>\n";
        out << "Disk state: ";
        switch (disk.getState()) {
            case FileStorHandler::AVAILABLE: out << "AVAILABLE"; break;
            case FileStorHandler::DISABLED: out << "DISABLED"; break;
            case FileStorHandler::CLOSED: out << "CLOSED"; break;
        }
        out << "<h4>Active operations</h4>\n";
        disk.dumpActiveHtml(out);
        if (!verbose) continue;
        out << "<h4>Input queue</h4>\n";
        out << "<ul>\n";
        disk.dumpQueueHtml(out);
        out << "</ul>\n";
    }

    vespalib::LockGuard mergeGuard(_mergeStatesLock);
    out << "<tr><td>Active merge operations</td><td>" << _mergeStates.size() << "</td></tr>\n";
    if (verbose) {
        out << "<h4>Active merges</h4>\n";
        if (_mergeStates.size() == 0) {
            out << "None\n";
        }
        for (auto & entry : _mergeStates) {
            out << "<b>" << entry.first.toString() << "</b><br>\n";
            //    << "<p>" << it->second << "</p>\n"; // Gets very spammy with the complete state here..
        }
    }
}

void
FileStorHandlerImpl::waitUntilNoLocks()
{
    for (const auto & disk : _diskInfo) {
        disk.waitUntilNoLocks();
    }
}

ResumeGuard
FileStorHandlerImpl::pause()
{
    _paused.store(true, std::memory_order_relaxed);
    waitUntilNoLocks();
    return ResumeGuard(*this);
}

void
FileStorHandlerImpl::resume()
{
    vespalib::MonitorGuard g(_pauseMonitor);
    _paused.store(false, std::memory_order_relaxed);
    g.broadcast();
}

} // storage
