// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "filestorhandlerimpl.h"
#include "filestormetrics.h"
#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/storageapi/message/multioperation.h>
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

FileStorHandlerImpl::FileStorHandlerImpl(MessageSender& sender, FileStorMetrics& metrics,
                                         const spi::PartitionStateList& partitions,
                                         ServiceLayerComponentRegister& compReg)
    : _partitions(partitions),
      _component(compReg, "filestorhandlerimpl"),
      _diskInfo(_component.getDiskCount()),
      _messageSender(sender),
      _bucketIdFactory(_component.getBucketIdFactory()),
      _getNextMessageTimeout(100),
      _paused(false)
{
    for (uint32_t i=0; i<_diskInfo.size(); ++i) {
        _diskInfo[i].metrics = metrics.disks[i].get();
        assert(_diskInfo[i].metrics != 0);
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
        Disk& t(_diskInfo[i]);
        vespalib::MonitorGuard lockGuard(t.lock);
        while (t.getQueueSize() != 0 || !t.lockedBuckets.empty()) {
            LOG(debug, "Still %d in queue and %ld locked buckets for disk '%d'", t.getQueueSize(), t.lockedBuckets.size(), i);
            lockGuard.wait(100);
        }
        LOG(debug, "All queues and bucket locks released for disk '%d'", i);
    }

    if (killPendingMerges) {
        api::ReturnCode code(api::ReturnCode::ABORTED,
                             "Storage node is shutting down");
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
FileStorHandlerImpl::reply(api::StorageMessage& msg,
                           DiskState state) const
{
    if (!msg.getType().isReply()) {
        std::shared_ptr<api::StorageReply> rep(
                static_cast<api::StorageCommand&>(msg).makeReply().release());
        if (state == FileStorHandler::DISABLED) {
            rep->setResult(api::ReturnCode(api::ReturnCode::DISK_FAILURE, "Disk disabled"));
        } else {
            rep->setResult(api::ReturnCode(api::ReturnCode::ABORTED, "Shutting down storage node."));
        }
        _messageSender.sendReply(rep);
    }
}

void
FileStorHandlerImpl::setDiskState(uint16_t disk, DiskState state)
{
    Disk& t(_diskInfo[disk]);
    vespalib::MonitorGuard lockGuard(t.lock);

    // Mark disk closed
    t.setState(state);
    if (state != FileStorHandler::AVAILABLE) {
        while (t.queue.begin() != t.queue.end()) {
            reply(*t.queue.begin()->_command, state);
            t.queue.erase(t.queue.begin());
        }
    }
    lockGuard.broadcast();
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
        Disk& t(_diskInfo[i]);
        vespalib::MonitorGuard lockGuard(t.lock);
        lockGuard.broadcast();
        LOG(debug, "Closed disk[%d]", i);
    }
}

uint32_t
FileStorHandlerImpl::getQueueSize() const
{
    uint32_t count = 0;
    for (uint32_t i=0; i<_diskInfo.size(); ++i) {
        const Disk& t(_diskInfo[i]);
        vespalib::MonitorGuard lockGuard(t.lock);
        count += t.getQueueSize();
    }
    return count;
}

bool
FileStorHandlerImpl::schedule(const std::shared_ptr<api::StorageMessage>& msg,
                              uint16_t disk)
{
    assert(disk < _diskInfo.size());
    Disk& t(_diskInfo[disk]);
    MessageEntry messageEntry(msg, getStorageMessageBucket(*msg));
    vespalib::MonitorGuard lockGuard(t.lock);

    if (t.getState() == FileStorHandler::AVAILABLE) {
        MBUS_TRACE(msg->getTrace(), 5, vespalib::make_string(
                "FileStorHandler: Operation added to disk %d's queue with "
                "priority %u", disk, msg->getPriority()));

        t.queue.emplace_back(std::move(messageEntry));

        LOG(spam, "Queued operation %s with priority %u.",
            msg->getType().toString().c_str(),
            msg->getPriority());

        lockGuard.broadcast();
    } else {
        return false;
    }
    return true;
}

bool
FileStorHandlerImpl::messageMayBeAborted(const api::StorageMessage& msg) const
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
    case api::MessageType::MULTIOPERATION_ID:
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
FileStorHandlerImpl::abortQueuedCommandsForBuckets(
        Disk& disk,
        const AbortBucketOperationsCommand& cmd)
{
    Disk& t(disk);
    vespalib::MonitorGuard diskLock(t.lock);
    typedef PriorityQueue::iterator iter_t;
    api::ReturnCode abortedCode(api::ReturnCode::ABORTED,
                                "Sending distributor no longer owns "
                                "bucket operation was bound to");
    for (iter_t it(t.queue.begin()), e(t.queue.end()); it != e;) {
        api::StorageMessage& msg(*it->_command);
        if (messageMayBeAborted(msg) && cmd.shouldAbort(it->_bucket)) {
            LOG(debug,
                "Aborting operation %s as it is bound for bucket %s",
                msg.toString().c_str(),
                it->_bucket.getBucketId().toString().c_str());
            std::shared_ptr<api::StorageReply> msgReply(
                    static_cast<api::StorageCommand&>(msg).makeReply().release());
            msgReply->setResult(abortedCode);
            _messageSender.sendReply(msgReply);
            
            it = t.queue.erase(it);
        } else {
            ++it;
        }
    }
}

bool
FileStorHandlerImpl::diskHasActiveOperationForAbortedBucket(
        const Disk& disk,
        const AbortBucketOperationsCommand& cmd) const
{
    for (auto& lockedBucket : disk.lockedBuckets) {
        if (cmd.shouldAbort(lockedBucket.first)) {
            LOG(spam,
                "Disk had active operation for aborted bucket %s, "
                "waiting for it to complete...",
                lockedBucket.first.toString().c_str());
            return true;
        }
    }
    return false;
}

void
FileStorHandlerImpl::waitUntilNoActiveOperationsForAbortedBuckets(
        Disk& disk,
        const AbortBucketOperationsCommand& cmd)
{
    vespalib::MonitorGuard guard(disk.lock);
    while (diskHasActiveOperationForAbortedBucket(disk, cmd)) {
        guard.wait();
    }
    guard.broadcast();
}

void
FileStorHandlerImpl::abortQueuedOperations(
        const AbortBucketOperationsCommand& cmd)
{
    // Do queue clearing and active operation waiting in two passes
    // to allow disk threads to drain running operations in parallel.
    for (uint32_t i = 0; i < _diskInfo.size(); ++i) {
        abortQueuedCommandsForBuckets(_diskInfo[i], cmd);
    }
    for (uint32_t i = 0; i < _diskInfo.size(); ++i) {
        waitUntilNoActiveOperationsForAbortedBuckets(_diskInfo[i], cmd);
    }    
}

void
FileStorHandlerImpl::updateMetrics(const MetricLockGuard &)
{
    for (uint32_t i=0; i<_diskInfo.size(); ++i) {
        const Disk& t(_diskInfo[i]);
        vespalib::MonitorGuard lockGuard(t.lock);
        t.metrics->pendingMerges.addValue(_mergeStates.size());
        t.metrics->queueSize.addValue(t.getQueueSize());
    }
}

FileStorHandler::LockedMessage &
FileStorHandlerImpl::getNextMessage(uint16_t disk, FileStorHandler::LockedMessage& lck)
{
    document::Bucket bucket(lck.first->getBucket());

    LOG(spam, "Disk %d retrieving message for buffered bucket %s", disk, bucket.getBucketId().toString().c_str());

    assert(disk < _diskInfo.size());
    Disk& t(_diskInfo[disk]);

    if (getDiskState(disk) == FileStorHandler::CLOSED) {
        lck.second.reset();
        return lck;
    }

    vespalib::MonitorGuard lockGuard(t.lock);
    BucketIdx& idx = boost::multi_index::get<2>(t.queue);
    std::pair<BucketIdx::iterator, BucketIdx::iterator> range = idx.equal_range(bucket);

    // No more for this bucket.
    if (range.first == range.second) {
        lck.second.reset();
        return lck;
    }

    api::StorageMessage & m(*range.first->_command);
    mbus::Trace& trace = m.getTrace();

    MBUS_TRACE(trace, 9, "FileStorHandler: Message identified by disk thread looking for more requests to active bucket.");

    uint64_t waitTime(const_cast<metrics::MetricTimer&>(range.first->_timer).stop(
            t.metrics->averageQueueWaitingTime[m.getLoadType()]));

    LOG(debug, "Message %s waited %" PRIu64 " ms in storage queue (bucket %s), timeout %d",
        m.toString().c_str(), waitTime, bucket.getBucketId().toString().c_str(),
        static_cast<api::StorageCommand&>(m).getTimeout());

    if (m.getType().isReply() ||
        waitTime < static_cast<api::StorageCommand&>(m).getTimeout())
    {
        std::shared_ptr<api::StorageMessage> msg = std::move(range.first->_command);
        idx.erase(range.first);
        lck.second.swap(msg);
        lockGuard.broadcast();
        lockGuard.unlock();
        return lck;
    } else {
        std::shared_ptr<api::StorageReply> msgReply(static_cast<api::StorageCommand&>(m).makeReply().release());
        idx.erase(range.first);
        lockGuard.broadcast();
        lockGuard.unlock();
        msgReply->setResult(api::ReturnCode(api::ReturnCode::TIMEOUT, "Message waited too long in storage queue"));
        _messageSender.sendReply(msgReply);

        lck.second.reset();
        return lck;
    }
}

bool
FileStorHandlerImpl::tryHandlePause(uint16_t disk) const
{
    if (isPaused()) {
        // Wait a single time to see if filestor gets unpaused.
        if (getDiskState(disk) != FileStorHandler::CLOSED) {
            vespalib::MonitorGuard g(_pauseMonitor);
            g.wait(100);
        }
        return !isPaused();
    }
    return true;
}

bool
FileStorHandlerImpl::diskIsClosed(uint16_t disk) const
{
    return (getDiskState(disk) == FileStorHandler::CLOSED);
}

bool
FileStorHandlerImpl::messageTimedOutInQueue(const api::StorageMessage& msg,
                                            uint64_t waitTime) const
{
    if (msg.getType().isReply()) {
        return false; // Replies must always be processed and cannot time out.
    }
    return (waitTime >= static_cast<const api::StorageCommand&>(msg).getTimeout());
}

std::unique_ptr<FileStorHandler::BucketLockInterface>
FileStorHandlerImpl::takeDiskBucketLockOwnership(
        const vespalib::MonitorGuard & guard,
        Disk& disk,
        const document::Bucket &bucket,
        const api::StorageMessage& msg)
{
    return std::make_unique<BucketLock>(guard, disk, bucket, msg.getPriority(), msg.getSummary());
}

std::unique_ptr<api::StorageReply>
FileStorHandlerImpl::makeQueueTimeoutReply(api::StorageMessage& msg) const
{
    assert(!msg.getType().isReply());
    std::unique_ptr<api::StorageReply> msgReply(
            static_cast<api::StorageCommand&>(msg).makeReply().release());
    msgReply->setResult(api::ReturnCode(
                api::ReturnCode::TIMEOUT,
                "Message waited too long in storage queue"));
    return msgReply;
}

namespace {
    bool
    bucketIsLockedOnDisk(const document::Bucket &id, const FileStorHandlerImpl::Disk &t) {
        return (id.getBucketId().getRawId() != 0 && t.isLocked(id));
    }
}

FileStorHandler::LockedMessage
FileStorHandlerImpl::getNextMessage(uint16_t disk)
{
    assert(disk < _diskInfo.size());
    if (!tryHandlePause(disk)) {
        return {}; // Still paused, return to allow tick.
    }

    Disk& t(_diskInfo[disk]);

    vespalib::MonitorGuard lockGuard(t.lock);
    // Try to grab a message+lock, immediately retrying once after a wait
    // if none can be found and then exiting if the same is the case on the
    // second attempt. This is key to allowing the run loop to register
    // ticks at regular intervals while not busy-waiting.
    for (int attempt = 0; (attempt < 2) && ! diskIsClosed(disk); ++attempt) {
        PriorityIdx& idx(boost::multi_index::get<1>(t.queue));
        PriorityIdx::iterator iter(idx.begin()), end(idx.end());

        while (iter != end && bucketIsLockedOnDisk(iter->_bucket, t)) {
            iter++;
        }
        if (iter != end) {
            if (! isPaused()) {
                return getMessage(lockGuard, t, idx, iter);
            }
        }
        if (attempt == 0) {
            lockGuard.wait(_getNextMessageTimeout);
        }
    }
    return {}; // No message fetched.
}

FileStorHandler::LockedMessage
FileStorHandlerImpl::getMessage(vespalib::MonitorGuard & guard, Disk & t, PriorityIdx & idx, PriorityIdx::iterator iter) {

    api::StorageMessage & m(*iter->_command);
    const uint64_t waitTime(
            const_cast<metrics::MetricTimer &>(iter->_timer).stop(
                    t.metrics->averageQueueWaitingTime[m.getLoadType()]));

    mbus::Trace &trace(m.getTrace());
    MBUS_TRACE(trace, 9, "FileStorHandler: Message identified by disk thread.");
    LOG(debug, "Message %s waited %" PRIu64 " ms in storage queue, timeout %d",
        m.toString().c_str(), waitTime, static_cast<api::StorageCommand &>(m).getTimeout());

    std::shared_ptr<api::StorageMessage> msg = std::move(iter->_command);
    document::Bucket bucket(iter->_bucket);
    idx.erase(iter); // iter not used after this point.

    if (!messageTimedOutInQueue(*msg, waitTime)) {
        auto locker = takeDiskBucketLockOwnership(guard, t, bucket, *msg);
        guard.unlock();
        MBUS_TRACE(trace, 9, "FileStorHandler: Got lock on bucket");
        return std::move(FileStorHandler::LockedMessage(std::move(locker), std::move(msg)));
    } else {
        std::shared_ptr<api::StorageReply> msgReply(makeQueueTimeoutReply(*msg));
        guard.broadcast(); // XXX: needed here?
        guard.unlock();
        _messageSender.sendReply(msgReply);
        return {};
    }
}

std::shared_ptr<FileStorHandler::BucketLockInterface>
FileStorHandlerImpl::lock(const document::Bucket &bucket, uint16_t disk)
{
    assert(disk < _diskInfo.size());

    Disk& t(_diskInfo[disk]);
    LOG(spam,
        "Acquiring filestor lock for %s on disk %d",
        bucket.getBucketId().toString().c_str(),
        disk);

    vespalib::MonitorGuard lockGuard(t.lock);

    while (bucket.getBucketId().getRawId() != 0 && t.isLocked(bucket)) {
        LOG(spam,
            "Contending for filestor lock for %s",
            bucket.getBucketId().toString().c_str());
        lockGuard.wait(100);
    }

    std::shared_ptr<FileStorHandler::BucketLockInterface> locker(
            new BucketLock(lockGuard, t, bucket, 255, "External lock"));

    lockGuard.broadcast();
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
            for (std::map<uint16_t, vespalib::Monitor*>::iterator it
                    = monitors.begin(); it != monitors.end(); ++it)
            {
                guards.push_back(std::shared_ptr<vespalib::MonitorGuard>(
                        new vespalib::MonitorGuard(*it->second)));
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
                return static_cast<const api::UpdateCommand&>(msg)
                        .getDocumentId();
                break;
            case api::MessageType::REMOVE_ID:
                return static_cast<const api::RemoveCommand&>(msg)
                        .getDocumentId();
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
FileStorHandlerImpl::calculateTargetBasedOnDocId(
        const api::StorageMessage& msg,
        std::vector<RemapInfo*>& targets)
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

document::Bucket
FileStorHandlerImpl::remapMessage(
        api::StorageMessage& msg,
        const document::Bucket& source,
        Operation op,
        std::vector<RemapInfo*>& targets,
        uint16_t& targetDisk, api::ReturnCode& returnCode)
{
    document::Bucket newBucket = source;

    switch (msg.getType().getId()) {
    case api::MessageType::GET_ID:
    case api::MessageType::PUT_ID:
    case api::MessageType::UPDATE_ID:
    case api::MessageType::REMOVE_ID:
        // Move to correct queue
    {
        api::BucketCommand& cmd(
                static_cast<api::BucketCommand&>(msg));

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
                    uint32_t commonBits(
                            findCommonBits(targets[0]->bucket.getBucketId(), bucket));
                    if (commonBits < source.getBucketId().getUsedBits()) {
                        std::ostringstream ost;
                        ost << bucket << " belongs in neither "
                            << targets[0]->bucket.getBucketId() << " nor " << targets[1]->bucket.getBucketId()
                            << ". Cannot remap it after split. It "
                            << "did not belong in the original "
                            << "bucket " << source.getBucketId();
                        LOG(error, "Error remapping %s after split %s",
                            cmd.getType().toString().c_str(),
                            ost.str().c_str());
                        returnCode = api::ReturnCode(
                                    api::ReturnCode::REJECTED, ost.str());
                    } else {
                        std::ostringstream ost;
                        assert(targets.size() == 2);
                        ost << "Bucket " << source.getBucketId() << " was split and "
                            << "neither bucket " << targets[0]->bucket.getBucketId() << " nor "
                            << targets[1]->bucket.getBucketId() << " fit for this operation. "
                            << "Failing operation so distributor can create "
                            << "bucket on correct node.";
                        LOG(debug, "%s", ost.str().c_str());
                        returnCode = api::ReturnCode(
                                api::ReturnCode::BUCKET_DELETED,
                                ost.str());
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
                cmd.toString().c_str(), cmd.getBucketId().toString().c_str(),
                source.toString().c_str());
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
        api::BucketCommand& cmd(
                static_cast<api::BucketCommand&>(msg));
        if (cmd.getBucket() == source) {
            if (op != MOVE) {
                std::ostringstream ost;
                ost << "Bucket " << (op == SPLIT ? "split" : "joined")
                    << ". Cannot remap merge, so aborting it";
                api::ReturnCode code(api::ReturnCode::BUCKET_DELETED,
                                     ost.str());
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
        api::BucketCommand& cmd(
                static_cast<api::BucketCommand&>(msg));
        if (cmd.getBucket() == source) {
            if (op == MOVE) {
                targetDisk = targets[0]->diskIndex;
            } else if (op == SPLIT) {
                returnCode = api::ReturnCode(
                        api::ReturnCode::BUCKET_DELETED,
                        "Bucket split while operation enqueued");
            } else {
                returnCode = api::ReturnCode(
                        api::ReturnCode::BUCKET_DELETED,
                        "Bucket was just joined");
            }
        }
        break;
    }
    case api::MessageType::STAT_ID:
    case api::MessageType::MULTIOPERATION_ID:
    case api::MessageType::BATCHPUTREMOVE_ID:
    case api::MessageType::REVERT_ID:
    case api::MessageType::REMOVELOCATION_ID:
    case api::MessageType::SETBUCKETSTATE_ID:
    {
        // Move to correct queue if op == MOVE
        // Fail with bucket not found if op != MOVE
        api::BucketCommand& cmd(
                static_cast<api::BucketCommand&>(msg));
        if (cmd.getBucket() == source) {
            if (op == MOVE) {
                targetDisk = targets[0]->diskIndex;
            } else {
                returnCode = api::ReturnCode(
                        api::ReturnCode::BUCKET_DELETED,
                        op == SPLIT ? "Bucket was just split"
                                    : "Bucket was just joined");
            }
        }
        break;
    }
    case api::MessageType::CREATEBUCKET_ID:
    case api::MessageType::DELETEBUCKET_ID:
    case api::MessageType::JOINBUCKETS_ID:
        // Move to correct queue if op == MOVE. Otherwise ignore.
    {
        api::BucketCommand& cmd(
                static_cast<api::BucketCommand&>(msg));
        if (cmd.getBucket() == source) {
            if (op == MOVE) {
                targetDisk = targets[0]->diskIndex;
            }
        }
        break;
    }
    case api::MessageType::INTERNAL_ID:
    {
        const api::InternalCommand& icmd(
                static_cast<const api::InternalCommand&>(msg));
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
                    returnCode = api::ReturnCode(
                            api::ReturnCode::BUCKET_DELETED,
                            op == SPLIT
                            ? "Bucket was just split"
                            : "Bucket was just joined");
                }
            }
            break;
        case GetIterCommand::ID:
            bucket = static_cast<GetIterCommand&>(msg).getBucket();
            //@fallthrough@
        case RepairBucketCommand::ID:
            if (bucket.getBucketId().getRawId() == 0) {
                bucket = static_cast<RepairBucketCommand&>(msg)
                            .getBucket();
            }
            // Move to correct queue if op == MOVE
            // Fail with bucket not found if op != MOVE
            if (bucket == source) {
                if (op == MOVE) {
                    targetDisk = targets[0]->diskIndex;
                } else {
                    returnCode = api::ReturnCode(
                            api::ReturnCode::BUCKET_DELETED,
                            op == SPLIT
                            ? "Bucket was just split"
                            : "Bucket was just joined");
                }
            }
            break;
        case BucketDiskMoveCommand::ID:
            // Fail bucket not found if op != MOVE
            // Fail and log error if op == MOVE
        {
            api::BucketCommand& cmd(
                    static_cast<api::BucketCommand&>(msg));
            if (cmd.getBucket() == source) {
                if (op == MOVE) {
                    returnCode = api::ReturnCode(
                            api::ReturnCode::INTERNAL_FAILURE,
                            "Multiple bucket disk move "
                            "commands pending at the same time "
                            "towards bucket "
                            + source.toString());
                } else {
                    returnCode = api::ReturnCode(
                            api::ReturnCode::BUCKET_DELETED,
                            op == SPLIT
                            ? "Bucket was just split"
                            : "Bucket was just joined");
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
            LOG(error, "Attempted (and failed) to remap %s which should "
                "not be processed at this time",
                msg.toString(true).c_str());
            returnCode = api::ReturnCode(
                    api::ReturnCode::INTERNAL_FAILURE,
                    "No such message should be processed at "
                    "this time.");
            break;
        }
        }
        break;
    }
    default:
    {
        returnCode = api::ReturnCode(
                api::ReturnCode::INTERNAL_FAILURE,
                "Unknown message type in persistence layer");
        LOG(error,
            "Unknown message type in persistence layer: %s",
            msg.toString().c_str());
    }
    } // End of switch

    return newBucket;
}

void
FileStorHandlerImpl::remapQueueNoLock(
        Disk& from,
        const RemapInfo& source,
        std::vector<RemapInfo*>& targets,
        Operation op)
{
    BucketIdx& idx(boost::multi_index::get<2>(from.queue));
    std::pair<BucketIdx::iterator, BucketIdx::iterator> range(
            idx.equal_range(source.bucket));

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

        document::Bucket bucket = remapMessage(msg,
                                               source.bucket,
                                               op,
                                               targets,
                                               targetDisk,
                                               returnCode);

        if (returnCode.getResult() != api::ReturnCode::OK) {
            // Fail message if errorcode set
            if (!msg.getType().isReply()) {
                std::shared_ptr<api::StorageReply> rep(
                        static_cast<api::StorageCommand&>(msg)
                        .makeReply().release());
                LOG(spam, "Sending reply %s because remapping failed: %s",
                    msg.toString().c_str(),
                    returnCode.toString().c_str());

                rep->setResult(returnCode);
                _messageSender.sendReply(rep);
            }
        } else {
            entry._bucket = bucket;
            // Move to correct disk queue if needed
            _diskInfo[targetDisk].queue.emplace_back(std::move(entry));
        }
    }

}

void
FileStorHandlerImpl::remapQueue(
        const RemapInfo& source,
        RemapInfo& target,
        Operation op) {
    // Use a helper class to lock to solve issue that some buckets might be
    // the same bucket. Will fix order if we accept wrong order later.
    MultiLockGuard guard;

    Disk& from(_diskInfo[source.diskIndex]);
    guard.addLock(from.lock, source.diskIndex);

    Disk& to1(_diskInfo[target.diskIndex]);
    if (target.bucket.getBucketId().getRawId() != 0) {
        guard.addLock(to1.lock, target.diskIndex);
    }

    std::vector<RemapInfo*> targets;
    targets.push_back(&target);

    guard.lock();

    remapQueueNoLock(from, source, targets, op);
}

void
FileStorHandlerImpl::remapQueue(
        const RemapInfo& source,
        RemapInfo& target1,
        RemapInfo& target2,
        Operation op)
{
    // Use a helper class to lock to solve issue that some buckets might be
    // the same bucket. Will fix order if we accept wrong order later.
    MultiLockGuard guard;

    Disk& from(_diskInfo[source.diskIndex]);
    guard.addLock(from.lock, source.diskIndex);

    Disk& to1(_diskInfo[target1.diskIndex]);
    if (target1.bucket.getBucketId().getRawId() != 0) {
        guard.addLock(to1.lock, target1.diskIndex);
    }

    Disk& to2(_diskInfo[target2.diskIndex]);
    if (target2.bucket.getBucketId().getRawId() != 0) {
        guard.addLock(to2.lock, target2.diskIndex);
    }

    guard.lock();

    std::vector<RemapInfo*> targets;
    targets.push_back(&target1);
    targets.push_back(&target2);

    remapQueueNoLock(from, source, targets, op);
}

void
FileStorHandlerImpl::failOperations(
        const document::Bucket &bucket, uint16_t fromDisk,
        const api::ReturnCode& err)
{
    Disk& from(_diskInfo[fromDisk]);
    vespalib::MonitorGuard lockGuard(from.lock);

    BucketIdx& idx(boost::multi_index::get<2>(from.queue));
    std::pair<BucketIdx::iterator, BucketIdx::iterator> range(
            idx.equal_range(bucket));

    for (auto iter = range.first; iter != range.second;) {
        // We want to post delete bucket to list before calling this
        // function in order to release bucket database lock. Thus we
        // cannot delete the delete bucket operation itself
        if (iter->_command->getType() != api::MessageType::DELETEBUCKET) {
            if (!iter->_command->getType().isReply()) {
                std::shared_ptr<api::StorageReply> msgReply(
                        static_cast<api::StorageCommand&>(*iter->_command)
                            .makeReply().release());
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

FileStorHandlerImpl::Disk::Disk()
    : lock(),
      queue(),
      lockedBuckets(100),
      metrics(0),
      state(FileStorHandler::AVAILABLE)
{ }

FileStorHandlerImpl::Disk::~Disk() { }

bool
FileStorHandlerImpl::Disk::isLocked(const document::Bucket& bucket) const noexcept
{
    return (lockedBuckets.find(bucket) != lockedBuckets.end());
}

uint32_t
FileStorHandlerImpl::Disk::getQueueSize() const noexcept
{
    return queue.size();
}

uint32_t
FileStorHandlerImpl::getQueueSize(uint16_t disk) const
{
    const Disk& t(_diskInfo[disk]);
    vespalib::MonitorGuard lockGuard(t.lock);
    return t.getQueueSize();
}

FileStorHandlerImpl::BucketLock::BucketLock(
        const vespalib::MonitorGuard & guard,
        Disk& disk,
        const document::Bucket &bucket,
        uint8_t priority,
        const vespalib::stringref & statusString)
    : _disk(disk),
      _bucket(bucket)
{
    (void) guard;
    if (_bucket.getBucketId().getRawId() != 0) {
        // Lock the bucket and wait until it is not the current operation for
        // the disk itself.
        _disk.lockedBuckets.insert(
                std::make_pair(_bucket, Disk::LockEntry(priority, statusString)));
        LOG(debug,
            "Locked bucket %s with priority %u",
            bucket.getBucketId().toString().c_str(),
            priority);

        LOG_BUCKET_OPERATION_SET_LOCK_STATE(
                _bucket.getBucketId(), "acquired filestor lock", false,
                debug::BucketOperationLogger::State::BUCKET_LOCKED);
    }
}


FileStorHandlerImpl::BucketLock::~BucketLock()
{
    if (_bucket.getBucketId().getRawId() != 0) {
        vespalib::MonitorGuard lockGuard(_disk.lock);
        _disk.lockedBuckets.erase(_bucket);
        LOG(debug, "Unlocked bucket %s", _bucket.getBucketId().toString().c_str());
        LOG_BUCKET_OPERATION_SET_LOCK_STATE(
                _bucket.getBucketId(), "released filestor lock", true,
                debug::BucketOperationLogger::State::BUCKET_UNLOCKED);
        lockGuard.broadcast();
    }
}

std::string
FileStorHandlerImpl::dumpQueue(uint16_t disk) const
{
    std::ostringstream ost;

    const Disk& t(_diskInfo[disk]);
    vespalib::MonitorGuard lockGuard(t.lock);

    const PriorityIdx& idx = boost::multi_index::get<1>(t.queue);
    for (PriorityIdx::const_iterator it = idx.begin();
         it != idx.end();
         it++)
    {
        ost << it->_bucket.getBucketId() << ": " << it->_command->toString() << " (priority: "
            << (int)it->_command->getPriority() << ")\n";
    }

    return ost.str();
}

void
FileStorHandlerImpl::getStatus(std::ostream& out,
                               const framework::HttpUrlPath& path) const
{
    bool verbose = path.hasAttribute("verbose");
    out << "<h1>Filestor handler</h1>\n";
    for (uint32_t i=0; i<_diskInfo.size(); ++i) {
        out << "<h2>Disk " << i << "</h2>\n";
        const Disk& t(_diskInfo[i]);
        vespalib::MonitorGuard lockGuard(t.lock);
        out << "Queue size: " << t.getQueueSize() << "<br>\n";
        out << "Disk state: ";
        switch (t.getState()) {
        case FileStorHandler::AVAILABLE: out << "AVAILABLE"; break;
        case FileStorHandler::DISABLED: out << "DISABLED"; break;
        case FileStorHandler::CLOSED: out << "CLOSED"; break;
        }
        out << "<h4>Active operations</h4>\n";
        for (const auto& lockedBucket : t.lockedBuckets) {
            out << lockedBucket.second.statusString
                << " (" << lockedBucket.first.getBucketId()
                << ") Running for "
                << (_component.getClock().getTimeInSeconds().getTime()
                    - lockedBucket.second.timestamp)
                << " secs<br/>\n";
        }
        if (!verbose) continue;
        out << "<h4>Input queue</h4>\n";

        out << "<ul>\n";
        const PriorityIdx& idx = boost::multi_index::get<1>(t.queue);
        for (PriorityIdx::const_iterator it = idx.begin();
             it != idx.end();
             it++)
        {
            out << "<li>" << it->_command->toString() << " (priority: "
                << (int)it->_command->getPriority() << ")</li>\n";
        }
        out << "</ul>\n";
    }

    out << "<tr><td>Active merge operations</td><td>" << _mergeStates.size()
        << "</td></tr>\n";

    // Print merge states
    if (verbose) {
        out << "<h4>Active merges</h4>\n";
        if (_mergeStates.size() == 0) {
            out << "None\n";
        }
        for (std::map<document::Bucket, MergeStatus::SP>::const_iterator it
                 = _mergeStates.begin(); it != _mergeStates.end(); ++it)
        {
            out << "<b>" << it->first.toString() << "</b><br>\n";
            //    << "<p>" << it->second << "</p>\n"; // Gets very spammy with
            //    the complete state here..
        }
    }
}

void
FileStorHandlerImpl::waitUntilNoLocks()
{
    for (uint32_t i=0; i<_diskInfo.size(); ++i) {
        const Disk& t(_diskInfo[i]);
        vespalib::MonitorGuard lockGuard(t.lock);
        while (!t.lockedBuckets.empty()) {
            lockGuard.wait();
        }
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
