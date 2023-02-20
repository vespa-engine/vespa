// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "filestorhandlerimpl.h"
#include "filestormetrics.h"
#include "mergestatus.h"
#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storage/bucketdb/storbucketdb.h>
#include <vespa/storage/common/bucketmessages.h>
#include <vespa/storage/common/statusmessages.h>
#include <vespa/storage/common/messagebucket.h>
#include <vespa/storage/persistence/asynchandler.h>
#include <vespa/storage/persistence/messages.h>
#include <vespa/storageapi/message/stat.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/string_escape.h>
#include <xxhash.h>

#include <vespa/log/log.h>
LOG_SETUP(".persistence.filestor.handler.impl");

using document::BucketSpace;
using vespalib::xml_attribute_escaped;
using vespalib::xml_content_escaped;

namespace storage {

namespace {

uint32_t per_stripe_merge_limit(uint32_t num_threads, uint32_t num_stripes) noexcept {
    // Rationale: to avoid starving client ops we want to ensure that not all persistence
    // threads in any given stripe can be blocked by processing merges all at the same time.
    // We therefore allocate half of the per-stripe threads to non-merge operations.
    // Note that if the _total_ number of threads is small and odd (e.g. 3 or 5), it's still
    // possible to have a stripe where all threads are busy processing merges because there
    // is only 1 thread in the stripe in total.
    return std::max(1u, (num_threads / num_stripes) / 2);
}

}

FileStorHandlerImpl::FileStorHandlerImpl(MessageSender& sender, FileStorMetrics& metrics,
                                         ServiceLayerComponentRegister& compReg)
    : FileStorHandlerImpl(1, 1, sender, metrics, compReg, vespalib::SharedOperationThrottler::DynamicThrottleParams())
{
}

FileStorHandlerImpl::FileStorHandlerImpl(uint32_t numThreads, uint32_t numStripes, MessageSender& sender,
                                         FileStorMetrics& metrics,
                                         ServiceLayerComponentRegister& compReg,
                                         const vespalib::SharedOperationThrottler::DynamicThrottleParams& dyn_throttle_params)
    : _component(compReg, "filestorhandlerimpl"),
      _state(FileStorHandler::AVAILABLE),
      _metrics(&metrics),
      _dynamic_operation_throttler(vespalib::SharedOperationThrottler::make_dynamic_throttler(dyn_throttle_params)),
      _unlimited_operation_throttler(vespalib::SharedOperationThrottler::make_unlimited_throttler()),
      _active_throttler(_unlimited_operation_throttler.get()), // Will be set by FileStorManager
      _stripes(),
      _messageSender(sender),
      _bucketIdFactory(_component.getBucketIdFactory()),
      _max_active_merges_per_stripe(per_stripe_merge_limit(numThreads, numStripes)),
      _paused(false),
      _throttle_apply_bucket_diff_ops(false),
      _last_active_operations_stats()
{
    assert(numStripes > 0);
    _stripes.reserve(numStripes);
    for (size_t i(0); i < numStripes; i++) {
        _stripes.emplace_back(*this, sender);
    }

    uint32_t j(0);
    for (Stripe & stripe : _stripes) {
        stripe.setMetrics(_metrics->stripes[j++].get());
    }

    // Add update hook, so we will get callbacks each 5 seconds to update metrics.
    _component.registerMetricUpdateHook(*this, 5s);
}

FileStorHandlerImpl::~FileStorHandlerImpl()
{
    waitUntilNoLocks();
}

void
FileStorHandlerImpl::addMergeStatus(const document::Bucket& bucket, std::shared_ptr<MergeStatus> status)
{
    std::lock_guard mlock(_mergeStatesLock);
    if (_mergeStates.find(bucket) != _mergeStates.end()) {
        LOG(warning, "A merge status already existed for %s. Overwriting it.", bucket.toString().c_str());
    }
    _mergeStates[bucket] = status;
}

std::shared_ptr<MergeStatus>
FileStorHandlerImpl::editMergeStatus(const document::Bucket& bucket)
{
    std::lock_guard mlock(_mergeStatesLock);
    std::shared_ptr<MergeStatus> status = _mergeStates[bucket];
    if ( ! status ) {
        throw vespalib::IllegalStateException("No merge state exist for " + bucket.toString(), VESPA_STRLOC);
    }
    return status;
}

bool
FileStorHandlerImpl::isMerging(const document::Bucket& bucket) const
{
    std::lock_guard mlock(_mergeStatesLock);
    return (_mergeStates.find(bucket) != _mergeStates.end());
}

void
FileStorHandlerImpl::clearMergeStatus(const document::Bucket& bucket)
{
    clearMergeStatus(bucket, nullptr);
}

void
FileStorHandlerImpl::clearMergeStatus(const document::Bucket& bucket, const api::ReturnCode& code)
{
    clearMergeStatus(bucket, &code);
}

void
FileStorHandlerImpl::clearMergeStatus(const document::Bucket& bucket, const api::ReturnCode* code)
{
    std::lock_guard mlock(_mergeStatesLock);
    auto it = _mergeStates.find(bucket);
    if (it == _mergeStates.end()) {
        if (code != nullptr) {
            LOG(debug, "Merge state not present at the time of clear. "
                "Could not fail merge of bucket %s with code %s.",
                bucket.toString().c_str(), code->toString().c_str());
        } else {
            LOG(debug, "No merge state to clear for bucket %s.",
                bucket.toString().c_str());
        }
        return;
    }
    if (code != nullptr) {
        std::shared_ptr<MergeStatus> statusPtr(it->second);
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
    LOG(debug, "Wait until queues and bucket locks released.");
    flush();
    LOG(debug, "All queues and bucket locks released.");

    if (killPendingMerges) {
        std::map<document::Bucket, std::shared_ptr<MergeStatus>> my_merge_states;
        {
            std::lock_guard mergeGuard(_mergeStatesLock);
            std::swap(_mergeStates, my_merge_states);
        }
        api::ReturnCode code(api::ReturnCode::ABORTED, "Storage node is shutting down");
        for (auto & entry : my_merge_states) {
            MergeStatus& s(*entry.second);
            if (s.pendingGetDiff) {
                s.pendingGetDiff->setResult(code);
                _messageSender.sendReply(s.pendingGetDiff);
            }
            if (s.pendingApplyDiff) {
                s.pendingApplyDiff->setResult(code);
                _messageSender.sendReply(s.pendingApplyDiff);
            }
            if (s.reply) {
                s.reply->setResult(code);
                _messageSender.sendReply(s.reply);
            }
        }
    }
}

void
FileStorHandlerImpl::setDiskState(DiskState state)
{
    // Mark disk closed
    setState(state);
    if (state != FileStorHandler::AVAILABLE) {
        flush();
    }
}

FileStorHandler::DiskState
FileStorHandlerImpl::getDiskState() const
{
    return getState();
}

void
FileStorHandlerImpl::close()
{
    if (getDiskState() == FileStorHandler::AVAILABLE) {
        LOG(debug, "AVAILABLE -> CLOSED");
        setDiskState(FileStorHandler::CLOSED);
    }
    LOG(debug, "Closing");
    for (auto & stripe : _stripes) {
        stripe.broadcast();
    }
    LOG(debug, "Closed");
}

uint32_t
FileStorHandlerImpl::getQueueSize() const
{
    size_t sum(0);
    for (const auto & stripe : _stripes) {
        sum += stripe.get_cached_queue_size();
    }
    return sum;
}

bool
FileStorHandlerImpl::schedule(const std::shared_ptr<api::StorageMessage>& msg)
{
    if (getState() == FileStorHandler::AVAILABLE) {
        document::Bucket bucket = getStorageMessageBucket(*msg);
        stripe(bucket).schedule(MessageEntry(msg, bucket));
        return true;
    }
    return false;
}

FileStorHandler::ScheduleAsyncResult
FileStorHandlerImpl::schedule_and_get_next_async_message(const std::shared_ptr<api::StorageMessage>& msg)
{
    if (getState() == FileStorHandler::AVAILABLE) {
        document::Bucket bucket = getStorageMessageBucket(*msg);
        return ScheduleAsyncResult(stripe(bucket).schedule_and_get_next_async_message(MessageEntry(msg, bucket)));
    }
    return {};
}

void
FileStorHandlerImpl::reconfigure_dynamic_throttler(const vespalib::SharedOperationThrottler::DynamicThrottleParams& params)
{
    _dynamic_operation_throttler->reconfigure_dynamic_throttling(params);
}

void
FileStorHandlerImpl::use_dynamic_operation_throttling(bool use_dynamic) noexcept
{
    // Use release semantics instead of relaxed to ensure transitive visibility even in
    // non-persistence threads that try to invoke the throttler (i.e. RPC threads).
    _active_throttler.store(use_dynamic ? _dynamic_operation_throttler.get()
                                        : _unlimited_operation_throttler.get(),
                            std::memory_order_release);
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
    case api::MessageType::SETBUCKETSTATE_ID:
        return true;
    default:
        return false;
    }
}

void
FileStorHandlerImpl::abortQueuedOperations(const AbortBucketOperationsCommand& cmd)
{
    // Do queue clearing and active operation waiting in two passes
    // to allow disk threads to drain running operations in parallel.
    api::ReturnCode abortedCode(api::ReturnCode::ABORTED,
                                "Sending distributor no longer owns bucket operation was bound to, "
                                "or storage node went down");
    std::vector<std::shared_ptr<api::StorageReply>> aborted;
    for (auto & stripe : _stripes) {
        stripe.abort(aborted, cmd);
    }
    for (auto & msgReply : aborted) {
        msgReply->setResult(abortedCode);
        _messageSender.sendReply(msgReply);
    }

    for (auto & stripe : _stripes) {
        stripe.waitInactive(cmd);
    }
}

void
FileStorHandlerImpl::update_active_operations_metrics()
{
    auto& metrics = _metrics->active_operations;
    auto stats = get_active_operations_stats(true);
    auto& last_stats = _last_active_operations_stats;
    auto delta_stats = stats;
    if (last_stats.has_value()) {
        delta_stats -= last_stats.value();
    }
    last_stats = stats;
    uint32_t size_samples = delta_stats.get_size_samples();
    if (size_samples != 0) {
        double min_size = delta_stats.get_min_size().value_or(0);
        double max_size = delta_stats.get_max_size().value_or(0);
        double avg_size = ((double) delta_stats.get_total_size()) / size_samples;
        metrics.size.addValueBatch(avg_size, size_samples, min_size, max_size);
    }
    uint32_t latency_samples = delta_stats.get_latency_samples();
    if (latency_samples != 0) {
        double min_latency = delta_stats.get_min_latency().value_or(0.0);
        double max_latency = delta_stats.get_max_latency().value_or(0.0);
        double avg_latency = delta_stats.get_total_latency() / latency_samples;
        metrics.latency.addValueBatch(avg_latency, latency_samples, min_latency, max_latency);
    }
}

void
FileStorHandlerImpl::updateMetrics(const MetricLockGuard &)
{
    std::lock_guard lockGuard(_mergeStatesLock);
    _metrics->pendingMerges.addValue(_mergeStates.size());
    _metrics->queueSize.addValue(getQueueSize());
    _metrics->throttle_window_size.addValue(operation_throttler().current_window_size());
    _metrics->throttle_waiting_threads.addValue(operation_throttler().waiting_threads());
    _metrics->throttle_active_tokens.addValue(operation_throttler().current_active_token_count());

    for (const auto & stripe : _metrics->stripes) {
        const auto & m = stripe->averageQueueWaitingTime;
        _metrics->averageQueueWaitingTime.addTotalValueWithCount(m.getTotal(), m.getCount());
    }
    update_active_operations_metrics();
}

bool
FileStorHandlerImpl::tryHandlePause() const
{
    if (isPaused()) {
        // Wait a single time to see if filestor gets unpaused.
        if (!isClosed()) {
            std::unique_lock g(_pauseMonitor);
            _pauseCond.wait_for(g, 100ms);
        }
        return !isPaused();
    }
    return true;
}

bool
FileStorHandlerImpl::messageTimedOutInQueue(const api::StorageMessage& msg, vespalib::duration waitTime)
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
FileStorHandlerImpl::getNextMessage(uint32_t stripeId, vespalib::steady_time deadline)
{
    if (!tryHandlePause()) {
        return {}; // Still paused, return to allow tick.
    }

    return _stripes[stripeId].getNextMessage(deadline);
}

std::shared_ptr<FileStorHandler::BucketLockInterface>
FileStorHandlerImpl::Stripe::lock(const document::Bucket &bucket, api::LockingRequirements lockReq) {
    std::unique_lock guard(*_lock);

    while (isLocked(guard, bucket, lockReq)) {
        LOG(spam, "Contending for filestor lock for %s with %s access",
            bucket.getBucketId().toString().c_str(), api::to_string(lockReq));
        _cond->wait_for(guard, 100ms);
    }

    return std::make_shared<BucketLock>(guard, *this, bucket, 255, api::MessageType::INTERNAL_ID, 0, lockReq);
}

namespace {

struct MultiLockGuard {
    using monitor_guard = FileStorHandlerImpl::monitor_guard;

    std::map<uint16_t, std::mutex*> monitors;
    std::vector<std::shared_ptr<monitor_guard>> guards;

    MultiLockGuard();
    MultiLockGuard(const MultiLockGuard &) = delete;
    MultiLockGuard & operator=(const MultiLockGuard &) = delete;
    ~MultiLockGuard();

    void addLock(std::mutex & lock, uint16_t stripe_index) {
        monitors[stripe_index] = & lock;
    }
    void lock() {
        for (auto & entry : monitors) {
            guards.push_back(std::make_shared<monitor_guard>(*entry.second));
        }
    }
};

MultiLockGuard::MultiLockGuard() = default;
MultiLockGuard::~MultiLockGuard() = default;

document::DocumentId
getDocId(const api::StorageMessage& msg) {
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
            LOG_ABORT("should not be reached");
    }
}
uint32_t
findCommonBits(document::BucketId a, document::BucketId b) {
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
                                  std::vector<RemapInfo*>& targets, api::ReturnCode& returnCode)
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
            }
        } else {
            LOG(debug, "Did not remap %s with bucket %s from bucket %s",
                cmd.toString().c_str(), cmd.getBucketId().toString().c_str(), source.toString().c_str());
            LOG_ABORT("should not be reached");
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
    [[fallthrough]];
    case api::MessageType::SPLITBUCKET_ID:
        // Move to correct queue if op == MOVE
        // Fail with bucket not found if op is JOIN
        // Ok if op is SPLIT, as we have already done as requested.
    {
        api::BucketCommand& cmd(static_cast<api::BucketCommand&>(msg));
        if (cmd.getBucket() == source) {
            if (op == MOVE) {
            } else if (op == SPLIT) {
                returnCode = api::ReturnCode(api::ReturnCode::BUCKET_DELETED, "Bucket split while operation enqueued");
            } else {
                returnCode = api::ReturnCode(api::ReturnCode::BUCKET_DELETED, "Bucket was just joined");
            }
        }
        break;
    }
    case api::MessageType::STAT_ID:
    case api::MessageType::REVERT_ID:
    case api::MessageType::REMOVELOCATION_ID:
    case api::MessageType::SETBUCKETSTATE_ID:
    {
        // Move to correct queue if op == MOVE
        // Fail with bucket not found if op != MOVE
        api::BucketCommand& cmd(static_cast<api::BucketCommand&>(msg));
        if (cmd.getBucket() == source) {
            if (op != MOVE) {
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
                if (op != MOVE) {
                    returnCode = api::ReturnCode(api::ReturnCode::BUCKET_DELETED, splitOrJoin(op));
                }
            }
            break;
        case GetIterCommand::ID:
            bucket = static_cast<GetIterCommand&>(msg).getBucket();
            // Move to correct queue if op == MOVE
            // Fail with bucket not found if op != MOVE
            if (bucket == source) {
                if (op != MOVE) {
                    returnCode = api::ReturnCode(api::ReturnCode::BUCKET_DELETED, splitOrJoin(op));
                }
            }
            break;
        case ReadBucketInfo::ID:
        case RecheckBucketInfoCommand::ID:
        {
            LOG(debug, "While remapping load for bucket %s for reason %u, "
                       "we abort read bucket info request for this bucket.",
                source.getBucketId().toString().c_str(), op);
            break;
        }
        case RunTaskCommand::ID:
            LOG(debug, "While remapping load for bucket %s for reason %u, "
                       "we fail the RunTaskCommand.",
                source.getBucketId().toString().c_str(), op);
            returnCode = api::ReturnCode(api::ReturnCode::INTERNAL_FAILURE,
                                         "Will not run task that should be remapped.");
            break;
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
FileStorHandlerImpl::remapQueueNoLock(const RemapInfo& source, std::vector<RemapInfo*>& targets, Operation op)
{
    BucketIdx& idx(stripe(source.bucket).exposeBucketIdx());
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

        // If not OK, reply to this message with the following message
        api::ReturnCode returnCode(api::ReturnCode::OK);
        api::StorageMessage& msg(*entry._command);
        assert(entry._bucket == source.bucket);

        document::Bucket bucket = remapMessage(msg, source.bucket, op, targets, returnCode);

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
            assert(bucket == source.bucket || std::find_if(targets.begin(), targets.end(), [bucket](auto* e){
                return e->bucket == bucket;
            }) != targets.end());
            stripe(bucket).exposeQueue().emplace_back(std::move(entry));
        }
    }
    stripe(source.bucket).unsafe_update_cached_queue_size();
    for (const auto *target: targets) {
        stripe(target->bucket).unsafe_update_cached_queue_size();
    }
}

void
FileStorHandlerImpl::remapQueueAfterJoin(const RemapInfo& source, RemapInfo& target)
{
    remapQueue(source, target, FileStorHandlerImpl::JOIN);
}

void
FileStorHandlerImpl::remapQueueAfterSplit(const RemapInfo& source, RemapInfo& target1, RemapInfo& target2)
{
    remapQueue(source, target1, target2, FileStorHandlerImpl::SPLIT);
}

void
FileStorHandlerImpl::remapQueue(const RemapInfo& source, RemapInfo& target, Operation op)
{
    // Use a helper class to lock to solve issue that some buckets might be
    // the same bucket. Will fix order if we accept wrong order later.
    MultiLockGuard guard;

    guard.addLock(stripe(source.bucket).exposeLock(), stripe_index(source.bucket));

    if (target.bucket.getBucketId().getRawId() != 0) {
        guard.addLock(stripe(target.bucket).exposeLock(), stripe_index(target.bucket));
    }

    std::vector<RemapInfo*> targets;
    targets.push_back(&target);

    guard.lock();

    remapQueueNoLock(source, targets, op);
}

void
FileStorHandlerImpl::remapQueue(const RemapInfo& source, RemapInfo& target1, RemapInfo& target2, Operation op)
{
    // Use a helper class to lock to solve issue that some buckets might be
    // the same bucket. Will fix order if we accept wrong order later.
    MultiLockGuard guard;

    guard.addLock(stripe(source.bucket).exposeLock(), stripe_index(source.bucket));

    if (target1.bucket.getBucketId().getRawId() != 0) {
        guard.addLock(stripe(target1.bucket).exposeLock(), stripe_index(target1.bucket));
    }

    if (target2.bucket.getBucketId().getRawId() != 0) {
        guard.addLock(stripe(target2.bucket).exposeLock(), stripe_index(target2.bucket));
    }

    guard.lock();

    std::vector<RemapInfo*> targets;
    targets.push_back(&target1);
    targets.push_back(&target2);

    remapQueueNoLock(source, targets, op);
}

void
FileStorHandlerImpl::Stripe::failOperations(const document::Bucket &bucket, const api::ReturnCode& err)
{
    std::lock_guard guard(*_lock);

    BucketIdx& idx(bmi::get<2>(*_queue));
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
    update_cached_queue_size(guard);
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

void
FileStorHandlerImpl::sendReplyDirectly(const std::shared_ptr<api::StorageReply>& msg)
{
    _messageSender.sendReplyDirectly(msg);
}

FileStorHandlerImpl::MessageEntry::MessageEntry(const std::shared_ptr<api::StorageMessage>& cmd,
                                                const document::Bucket &bucket)
    : _command(cmd),
      _timer(),
      _bucket(bucket),
      _priority(cmd->getPriority())
{ }


FileStorHandlerImpl::MessageEntry::MessageEntry(const MessageEntry& entry) noexcept
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

FileStorHandlerImpl::MessageEntry::~MessageEntry() = default;
FileStorHandlerImpl::Stripe::~Stripe() = default;
FileStorHandlerImpl::Stripe::Stripe(Stripe &&) noexcept = default;

void
FileStorHandlerImpl::flush()
{
    for (auto & stripe : _stripes) {
        stripe.flush();
    }
}

uint64_t
FileStorHandlerImpl::dispersed_bucket_bits(const document::Bucket& bucket) noexcept {
    const uint64_t raw_id = bucket.getBucketId().getId();
    return XXH3_64bits(&raw_id, sizeof(uint64_t));
}

FileStorHandlerImpl::Stripe::Stripe(const FileStorHandlerImpl & owner, MessageSender & messageSender)
    : _owner(owner),
      _messageSender(messageSender),
      _metrics(nullptr),
      _lock(std::make_unique<std::mutex>()),
      _cond(std::make_unique<std::condition_variable>()),
      _queue(std::make_unique<PriorityQueue>()),
      _cached_queue_size(_queue->size()),
      _lockedBuckets(),
      _active_merges(0),
      _active_operations_stats()
{}

bool
FileStorHandlerImpl::Stripe::operation_type_should_be_throttled(api::MessageType::Id type_id) const noexcept
{
    // Note: SetBucketState is intentionally _not_ included in this set, even though it's
    // dispatched async. The rationale behind this is that SetBucketState is very cheap
    // to execute, usually comes in large waves (up to #buckets count) and processing all
    // requests should complete as quickly as possible. We also don't want such waves to
    // artificially boost the dynamic throttle window size due to a sudden throughput spike.
    //
    // Merge-related operations are transitively throttled by using the operation throttler
    // directly for all async ops within the MergeHandler.
    switch (type_id) {
    case api::MessageType::PUT_ID:
    case api::MessageType::REMOVE_ID:
    case api::MessageType::UPDATE_ID:
    case api::MessageType::REMOVELOCATION_ID:
    case api::MessageType::CREATEBUCKET_ID:
    case api::MessageType::DELETEBUCKET_ID:
        return true;
    case api::MessageType::APPLYBUCKETDIFF_ID:
    case api::MessageType::APPLYBUCKETDIFF_REPLY_ID:
        return _owner.throttle_apply_bucket_diff_ops();
    default:
        return false;
    }
}

FileStorHandler::LockedMessage
FileStorHandlerImpl::Stripe::getNextMessage(vespalib::steady_time deadline)
{
    std::unique_lock guard(*_lock);
    ThrottleToken throttle_token;
    // Try to grab a message+lock, immediately retrying once after a wait
    // if none can be found and then exiting if the same is the case on the
    // second attempt. This is key to allowing the run loop to register
    // ticks at regular intervals while not busy-waiting.
    for (int attempt = 0; (attempt < 2) && !_owner.isPaused(); ++attempt) {
        PriorityIdx& idx(bmi::get<1>(*_queue));
        PriorityIdx::iterator iter(idx.begin()), end(idx.end());
        bool was_throttled = false;

        while ((iter != end) && operationIsInhibited(guard, iter->_bucket, *iter->_command)) {
            iter++;
        }
        if (iter != end) {
            const bool should_throttle_op = operation_type_should_be_throttled(iter->_command->getType().getId());
            if (!should_throttle_op && throttle_token.valid()) {
                throttle_token.reset(); // Let someone else play with it.
            } else if (should_throttle_op && !throttle_token.valid()) {
                // Important: _non-blocking_ attempt at getting a throttle token.
                throttle_token = _owner.operation_throttler().try_acquire_one();
                if (!throttle_token.valid()) {
                    was_throttled = true;
                    _metrics->throttled_persistence_thread_polls.inc();
                }
            }
            if (!should_throttle_op || throttle_token.valid()) {
                return getMessage(guard, idx, iter, std::move(throttle_token));
            }
        }
        if (attempt == 0) {
            // Depending on whether we were blocked due to no usable ops in queue or throttling,
            // wait for either the queue or throttler to (hopefully) have some fresh stuff for us.
            if (!was_throttled) {
                _cond->wait_until(guard, deadline);
            } else {
                // Have to release lock before doing a blocking throttle token fetch, since it
                // prevents RPC threads from pushing onto the queue.
                guard.unlock();
                throttle_token = _owner.operation_throttler().blocking_acquire_one(deadline);
                guard.lock();
                if (!throttle_token.valid()) {
                    _metrics->timeouts_waiting_for_throttle_token.inc();
                    return {}; // Already exhausted our timeout window.
                }
            }
        }
    }
    return {}; // No message fetched.
}

FileStorHandler::LockedMessage
FileStorHandlerImpl::Stripe::get_next_async_message(monitor_guard& guard)
{
    if (_owner.isPaused()) {
        return {};
    }
    PriorityIdx& idx(bmi::get<1>(*_queue));
    PriorityIdx::iterator iter(idx.begin()), end(idx.end());

    while ((iter != end) && operationIsInhibited(guard, iter->_bucket, *iter->_command)) {
        ++iter;
    }
    if ((iter != end) && AsyncHandler::is_async_unconditional_message(*(iter->_command))) {
        // This is executed in the context of an RPC thread, so only do a _non-blocking_
        // poll of the throttle policy.
        auto throttle_token = _owner.operation_throttler().try_acquire_one();
        if (throttle_token.valid()) {
            return getMessage(guard, idx, iter, std::move(throttle_token));
        } else {
            _metrics->throttled_rpc_direct_dispatches.inc();
        }
    }
    return {};
}

FileStorHandler::LockedMessage
FileStorHandlerImpl::Stripe::getMessage(monitor_guard & guard, PriorityIdx & idx, PriorityIdx::iterator iter,
                                        ThrottleToken throttle_token)
{
    std::chrono::milliseconds waitTime(uint64_t(iter->_timer.stop(_metrics->averageQueueWaitingTime)));

    std::shared_ptr<api::StorageMessage> msg = std::move(iter->_command);
    document::Bucket bucket(iter->_bucket);
    idx.erase(iter); // iter not used after this point.
    update_cached_queue_size(guard);

    if (!messageTimedOutInQueue(*msg, waitTime)) {
        auto locker = std::make_unique<BucketLock>(guard, *this, bucket, msg->getPriority(),
                                                   msg->getType().getId(), msg->getMsgId(),
                                                   msg->lockingRequirements());
        guard.unlock();
        return {std::move(locker), std::move(msg), std::move(throttle_token)};
    } else {
        std::shared_ptr<api::StorageReply> msgReply(makeQueueTimeoutReply(*msg));
        guard.unlock();
        _cond->notify_all();
        _messageSender.sendReply(msgReply);
        return {};
    }
}

void
FileStorHandlerImpl::Stripe::waitUntilNoLocks() const
{
    std::unique_lock guard(*_lock);
    while (!_lockedBuckets.empty()) {
        _cond->wait(guard);
    }
}

void
FileStorHandlerImpl::Stripe::waitInactive(const AbortBucketOperationsCommand& cmd) const {
    std::unique_lock guard(*_lock);
    while (hasActive(guard, cmd)) {
        _cond->wait(guard);
    }
}

bool
FileStorHandlerImpl::Stripe::hasActive(monitor_guard &, const AbortBucketOperationsCommand& cmd) const {
    for (auto& lockedBucket : _lockedBuckets) {
        if (cmd.shouldAbort(lockedBucket.first)) {
            LOG(spam, "Disk had active operation for aborted bucket %s, waiting for it to complete...",
                lockedBucket.first.toString().c_str());
            return true;
        }
    }
    return false;
}

void
FileStorHandlerImpl::Stripe::abort(std::vector<std::shared_ptr<api::StorageReply>> & aborted,
                                   const AbortBucketOperationsCommand& cmd)
{
    std::lock_guard lockGuard(*_lock);
    for (auto it(_queue->begin()); it != _queue->end();) {
        api::StorageMessage& msg(*it->_command);
        if (messageMayBeAborted(msg) && cmd.shouldAbort(it->_bucket)) {
            aborted.emplace_back(static_cast<api::StorageCommand&>(msg).makeReply());
            it = _queue->erase(it);
        } else {
            ++it;
        }
    }
    update_cached_queue_size(lockGuard);
}

bool
FileStorHandlerImpl::Stripe::schedule(MessageEntry messageEntry)
{
    {
        std::lock_guard guard(*_lock);
        _queue->emplace_back(std::move(messageEntry));
        update_cached_queue_size(guard);
    }
    _cond->notify_one();
    return true;
}

FileStorHandler::LockedMessage
FileStorHandlerImpl::Stripe::schedule_and_get_next_async_message(MessageEntry entry)
{
    std::unique_lock guard(*_lock);
    _queue->emplace_back(std::move(entry));
    update_cached_queue_size(guard);
    auto lockedMessage = get_next_async_message(guard);
    if ( ! lockedMessage.msg) {
        if (guard.owns_lock()) {
            guard.unlock();
        }
        _cond->notify_one();
    }
    return lockedMessage;
}

void
FileStorHandlerImpl::Stripe::flush()
{
    std::unique_lock guard(*_lock);
    while (!(_queue->empty() && _lockedBuckets.empty())) {
        LOG(debug, "Still %ld in queue and %ld locked buckets", _queue->size(), _lockedBuckets.size());
        _cond->wait_for(guard, 100ms);
    }
}

namespace {

bool
message_type_is_merge_related(api::MessageType::Id msg_type_id) {
    switch (msg_type_id) {
    case api::MessageType::MERGEBUCKET_ID:
    case api::MessageType::MERGEBUCKET_REPLY_ID:
    case api::MessageType::GETBUCKETDIFF_ID:
    case api::MessageType::GETBUCKETDIFF_REPLY_ID:
    case api::MessageType::APPLYBUCKETDIFF_ID:
    case api::MessageType::APPLYBUCKETDIFF_REPLY_ID:
        return true;
    default: return false;
    }
}

}

void
FileStorHandlerImpl::Stripe::release(const document::Bucket & bucket,
                                     api::LockingRequirements reqOfReleasedLock,
                                     api::StorageMessage::Id lockMsgId,
                                     bool was_active_merge)
{
    std::unique_lock guard(*_lock);
    auto iter = _lockedBuckets.find(bucket);
    assert(iter != _lockedBuckets.end());
    auto& entry = iter->second;
    Clock::time_point start_time;
    bool wasExclusive = (reqOfReleasedLock == api::LockingRequirements::Exclusive);

    if (wasExclusive) {
        assert(entry._exclusiveLock);
        assert(entry._exclusiveLock->msgId == lockMsgId);
        if (was_active_merge) {
            assert(_active_merges > 0);
            --_active_merges;
        }
        start_time = entry._exclusiveLock.value().timestamp;
        entry._exclusiveLock.reset();
    } else {
        assert(!entry._exclusiveLock);
        auto shared_iter = entry._sharedLocks.find(lockMsgId);
        assert(shared_iter != entry._sharedLocks.end());
        start_time = shared_iter->second.timestamp;
        entry._sharedLocks.erase(shared_iter);
    }
    Clock::time_point now_ts = Clock::now();
    double latency = std::chrono::duration<double, std::milli>(now_ts - start_time).count();
    _active_operations_stats.guard().stats().operation_done(latency);
    if (!entry._exclusiveLock && entry._sharedLocks.empty()) {
        _lockedBuckets.erase(iter); // No more locks held
    }
    bool emptySharedLocks = entry._sharedLocks.empty();
    guard.unlock();
    if (wasExclusive) {
        _cond->notify_all();
    } else if (emptySharedLocks) {
        _cond->notify_one();
    }
}

void
FileStorHandlerImpl::Stripe::decrease_active_sync_merges_counter() noexcept
{
    std::unique_lock guard(*_lock);
    assert(_active_merges > 0);
    const bool may_have_blocked_merge = (_active_merges == _owner._max_active_merges_per_stripe);
    --_active_merges;
    if (may_have_blocked_merge) {
        guard.unlock();
        _cond->notify_all();
    }
}

void
FileStorHandlerImpl::Stripe::lock(const monitor_guard &, const document::Bucket & bucket,
                                  api::LockingRequirements lockReq, bool count_as_active_merge,
                                  const LockEntry & lockEntry) {
    auto& entry = _lockedBuckets[bucket];
    assert(!entry._exclusiveLock);
    if (lockReq == api::LockingRequirements::Exclusive) {
        assert(entry._sharedLocks.empty());
        if (count_as_active_merge) {
            ++_active_merges;
        }
        entry._exclusiveLock = lockEntry;
    } else {
        // TODO use a hash set with a custom comparator/hasher instead...?
        auto inserted = entry._sharedLocks.insert(std::make_pair(lockEntry.msgId, lockEntry));
        (void) inserted;
        assert(inserted.second);
    }
    _active_operations_stats.guard().stats().operation_started();
}

bool
FileStorHandlerImpl::Stripe::isLocked(const monitor_guard &, const document::Bucket& bucket,
                                      api::LockingRequirements lockReq) const noexcept
{
    if (bucket.getBucketId().getRawId() == 0) {
        return false;
    }
    auto iter = _lockedBuckets.find(bucket);
    if (iter == _lockedBuckets.end()) {
        return false;
    }
    if (iter->second._exclusiveLock) {
        return true;
    }
    // Shared locks can be taken alongside other shared locks, but exclusive locks
    // require that no shared locks are currently present.
    return ((lockReq == api::LockingRequirements::Exclusive)
            && !iter->second._sharedLocks.empty());
}

bool
FileStorHandlerImpl::Stripe::operationIsInhibited(const monitor_guard & guard, const document::Bucket& bucket,
                                                  const api::StorageMessage& msg) const noexcept
{
    if (message_type_is_merge_related(msg.getType().getId())
        && (_active_merges >= _owner._max_active_merges_per_stripe))
    {
        return true;
    }
    return isLocked(guard, bucket, msg.lockingRequirements());
}

ActiveOperationsStats
FileStorHandlerImpl::Stripe::get_active_operations_stats(bool reset_min_max) const
{
    auto guard = _active_operations_stats.guard();
    auto result = guard.stats();
    if (reset_min_max) {
        guard.stats().reset_min_max();
    }
    return result;
}

FileStorHandlerImpl::BucketLock::BucketLock(const monitor_guard& guard, Stripe& stripe,
                                            const document::Bucket& bucket, uint8_t priority,
                                            api::MessageType::Id msgType, api::StorageMessage::Id msgId,
                                            api::LockingRequirements lockReq)
    : _stripe(stripe),
      _bucket(bucket),
      _uniqueMsgId(msgId),
      _lockReq(lockReq),
      _counts_towards_merge_limit(false)
{
    if (_bucket.getBucketId().getRawId() != 0) {
        _counts_towards_merge_limit = message_type_is_merge_related(msgType);
        _stripe.lock(guard, _bucket, lockReq, _counts_towards_merge_limit, Stripe::LockEntry(priority, msgType, msgId));
        LOG(spam, "Locked bucket %s for message %" PRIu64 " with priority %u in mode %s",
            bucket.toString().c_str(), msgId, priority, api::to_string(lockReq));
    }
}


FileStorHandlerImpl::BucketLock::~BucketLock() {
    if (_bucket.getBucketId().getRawId() != 0) {
        _stripe.release(_bucket, _lockReq, _uniqueMsgId, _counts_towards_merge_limit);
        LOG(spam, "Unlocked bucket %s for message %" PRIu64 " in mode %s",
            _bucket.toString().c_str(), _uniqueMsgId, api::to_string(_lockReq));
    }
}

void
FileStorHandlerImpl::BucketLock::signal_operation_sync_phase_done() noexcept
{
    // Not atomic, only destructor can read/write this other than this function, and since
    // a strong ref must already be held to this object by the caller, we cannot race with it.
    if (_counts_towards_merge_limit){
        LOG(spam, "Synchronous phase for bucket %s is done; reducing active count proactively",
            _bucket.toString().c_str());
        _stripe.decrease_active_sync_merges_counter();
        _counts_towards_merge_limit = false;
    }
}

std::string
FileStorHandlerImpl::dumpQueue() const
{
    std::ostringstream os;
    for (const Stripe & stripe : _stripes) {
        stripe.dumpQueue(os);
    }
    return os.str();
}

void
FileStorHandlerImpl::dumpQueueHtml(std::ostream & os) const
{
    for (const Stripe & stripe : _stripes) {
        stripe.dumpQueueHtml(os);
    }
}

void
FileStorHandlerImpl::dumpActiveHtml(std::ostream & os) const
{
    for (const Stripe & stripe : _stripes) {
        stripe.dumpActiveHtml(os);
    }
}

void
FileStorHandlerImpl::Stripe::dumpQueueHtml(std::ostream & os) const
{
    std::lock_guard guard(*_lock);

    const PriorityIdx& idx = bmi::get<1>(*_queue);
    for (const auto & entry : idx) {
        os << "<li>" << xml_content_escaped(entry._command->toString()) << " (priority: "
           << static_cast<int>(entry._command->getPriority()) << ")</li>\n";
    }
}

namespace {

void
dump_lock_entry(const document::BucketId& bucketId, const FileStorHandlerImpl::Stripe::LockEntry& entry,
                api::LockingRequirements lock_mode, FileStorHandlerImpl::Clock::time_point now_ts, std::ostream& os) {
    os << api::MessageType::get(entry.msgType).getName() << ":" << entry.msgId << " ("
       << bucketId << ", " << api::to_string(lock_mode)
       << " lock) Running for " << std::chrono::duration_cast<std::chrono::seconds>(now_ts - entry.timestamp).count() << " secs<br/>\n";
}

}

void
FileStorHandlerImpl::Stripe::dumpActiveHtml(std::ostream & os) const
{
    Clock::time_point now = Clock::now();
    std::lock_guard guard(*_lock);
    for (const auto & e : _lockedBuckets) {
        if (e.second._exclusiveLock) {
            dump_lock_entry(e.first.getBucketId(), *e.second._exclusiveLock,
                            api::LockingRequirements::Exclusive, now, os);
        }
        for (const auto& shared : e.second._sharedLocks) {
            dump_lock_entry(e.first.getBucketId(), shared.second,
                            api::LockingRequirements::Shared, now, os);
        }
    }
}

void
FileStorHandlerImpl::Stripe::dumpQueue(std::ostream & os) const
{
    std::lock_guard guard(*_lock);

    const PriorityIdx& idx = bmi::get<1>(*_queue);
    for (const auto & entry : idx) {
        os << entry._bucket.getBucketId() << ": "
           << xml_content_escaped(entry._command->toString())
           << " (priority: " << static_cast<int>(entry._command->getPriority()) << ")\n";
    }
}

void
FileStorHandlerImpl::getStatus(std::ostream& out, const framework::HttpUrlPath& path) const
{
    bool verbose = path.hasAttribute("verbose");
    out << "<h1>Filestor handler</h1>\n";

    out << "<h2>Disk " << "</h2>\n";
    out << "Queue size: " << getQueueSize() << "<br>\n";
    out << "Disk state: ";
    switch (getState()) {
        case FileStorHandler::AVAILABLE: out << "AVAILABLE"; break;
        case FileStorHandler::CLOSED: out << "CLOSED"; break;
    }
    out << "<h4>Active operations</h4>\n";
    dumpActiveHtml(out);
    if (verbose) {
        out << "<h4>Input queue</h4>\n";
        out << "<ul>\n";
        dumpQueueHtml(out);
        out << "</ul>\n";
    }

    std::lock_guard mergeGuard(_mergeStatesLock);
    out << "<p>Active merge operations: " << _mergeStates.size() << "</p>\n";
    if (verbose) {
        out << "<h4>Active merges</h4>\n";
        if (_mergeStates.empty()) {
            out << "None\n";
        }
        for (auto & entry : _mergeStates) {
            out << "<b>" << entry.first.toString() << "</b><br>\n";
        }
    }
}

ActiveOperationsStats
FileStorHandlerImpl::get_active_operations_stats(bool reset_min_max) const
{
    ActiveOperationsStats result;
    for (const auto & stripe : _stripes) {
        auto stats = stripe.get_active_operations_stats(reset_min_max);
        result.merge(stats);
    }
    return result;
}

void
FileStorHandlerImpl::waitUntilNoLocks()
{
    for (const auto & stripe : _stripes) {
        stripe.waitUntilNoLocks();
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
    std::unique_lock guard(_pauseMonitor);
    _paused.store(false, std::memory_order_relaxed);
    _pauseCond.notify_all();
}

} // storage
