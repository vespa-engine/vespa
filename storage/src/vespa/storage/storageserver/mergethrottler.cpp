// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mergethrottler.h"
#include <vespa/storageframework/generic/thread/thread.h>
#include <vespa/storageframework/generic/clock/clock.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/storage/common/nodestateupdater.h>
#include <vespa/storage/common/dummy_mbus_messages.h>
#include <vespa/storage/persistence/messages.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/messagebus/dynamicthrottlepolicy.h>
#include <vespa/messagebus/error.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/config/helper/configfetcher.hpp>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/string_escape.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".mergethrottler");

using vespa::config::content::core::StorServerConfig;

namespace storage {

namespace {

struct NodeComparator {
    bool operator()(const api::MergeBucketCommand::Node& a,
                    const api::MergeBucketCommand::Node& b) const noexcept
    {
        return a.index < b.index;
    }
};

}

MergeThrottler::ChainedMergeState::ChainedMergeState()
    : _cmd(),
      _cmdString(),
      _clusterStateVersion(0),
      _estimated_memory_usage(0),
      _inCycle(false),
      _executingLocally(false),
      _unwinding(false),
      _cycleBroken(false),
      _aborted(false)
{ }

MergeThrottler::ChainedMergeState::ChainedMergeState(const api::StorageMessage::SP& cmd, bool executing)
    : _cmd(cmd),
      _cmdString(cmd->toString()),
      _clusterStateVersion(static_cast<const api::MergeBucketCommand&>(*cmd).getClusterStateVersion()),
      _estimated_memory_usage(static_cast<const api::MergeBucketCommand&>(*cmd).estimated_memory_footprint()),
      _inCycle(false),
      _executingLocally(executing),
      _unwinding(false),
      _cycleBroken(false),
      _aborted(false)
{ }
MergeThrottler::ChainedMergeState::~ChainedMergeState() = default;

MergeThrottler::Metrics::Metrics(metrics::MetricSet* owner)
    : metrics::MetricSet("mergethrottler", {}, "", owner),
      averageQueueWaitingTime("averagequeuewaitingtime", {}, "Average time a merge spends in the throttler queue", this),
      queueSize("queuesize", {}, "Length of merge queue", this),
      active_window_size("active_window_size", {}, "Number of merges active within the pending window size", this),
      estimated_merge_memory_usage("estimated_merge_memory_usage", {}, "An estimated upper bound of the "
                                   "memory usage (in bytes) of the merges currently in the active window", this),
      merge_memory_limit("merge_memory_limit", {}, "The active soft limit (in bytes) for memory used by merge operations on this node", this),
      bounced_due_to_back_pressure("bounced_due_to_back_pressure", {}, "Number of merges bounced due to resource exhaustion back-pressure", this),
      chaining("mergechains", this),
      local("locallyexecutedmerges", this)
{ }
MergeThrottler::Metrics::~Metrics() = default;

MergeThrottler::MergeFailureMetrics::MergeFailureMetrics(metrics::MetricSet* owner)
    : metrics::MetricSet("failures", {}, "Detailed failure statistics", owner),
      sum("total", {}, "Sum of all failures", this),
      notready("notready", {}, "The number of merges discarded because distributor was not ready", this),
      timeout("timeout", {}, "The number of merges that failed because they timed out towards storage", this),
      aborted("aborted", {}, "The number of merges that failed because the storage node was (most likely) shutting down", this),
      wrongdistribution("wrongdistribution", {}, "The number of merges that were discarded (flushed) because they were initiated at an older cluster state than the current", this),
      bucketnotfound("bucketnotfound", {}, "The number of operations that failed because the bucket did not exist", this),
      busy("busy", {}, "The number of merges that failed because the storage node was busy", this),
      exists("exists", {}, "The number of merges that were rejected due to a merge operation for their bucket already being processed", this),
      rejected("rejected", {}, "The number of merges that were rejected", this),
      other("other", {}, "The number of other failures", this)
{
    sum.addMetricToSum(notready);
    sum.addMetricToSum(timeout);
    sum.addMetricToSum(aborted);
    sum.addMetricToSum(wrongdistribution);
    sum.addMetricToSum(bucketnotfound);
    sum.addMetricToSum(busy);
    sum.addMetricToSum(exists);
    sum.addMetricToSum(rejected);
    sum.addMetricToSum(other);
}
MergeThrottler::MergeFailureMetrics::~MergeFailureMetrics() = default;


MergeThrottler::MergeOperationMetrics::MergeOperationMetrics(const std::string& name, metrics::MetricSet* owner)
    : metrics::MetricSet(name, {}, vespalib::make_string("Statistics for %s", name.c_str()), owner),
      ok("ok", {}, vespalib::make_string("The number of successful merges for '%s'", name.c_str()), this),
      failures(this)
{
}
MergeThrottler::MergeOperationMetrics::~MergeOperationMetrics() = default;

MergeThrottler::MergeNodeSequence::MergeNodeSequence(const api::MergeBucketCommand& cmd, uint16_t thisIndex)
    : _cmd(cmd),
      _sortedNodes(cmd.getNodes()),
      _sortedIndex(UINT16_MAX),
      _unordered_index(UINT16_MAX),
      _thisIndex(thisIndex),
      _use_unordered_forwarding(cmd.use_unordered_forwarding())
{
    // Sort the node vector so that we can find out if we're the
    // last node in the chain or if we should forward the merge
    std::sort(_sortedNodes.begin(), _sortedNodes.end(), NodeComparator());
    assert(!_sortedNodes.empty() && (_sortedNodes.size() < UINT16_MAX));
    for (uint16_t i = 0; i < static_cast<uint16_t>(_sortedNodes.size()); ++i) {
        if (_sortedNodes[i].index == _thisIndex) {
            _sortedIndex = i;
            break;
        }
    }
    const auto& nodes = unordered_nodes();
    for (uint16_t i = 0; i < static_cast<uint16_t>(nodes.size()); ++i) {
        if (nodes[i].index == _thisIndex) {
            _unordered_index = i;
            break;
        }
    }
}

uint16_t
MergeThrottler::MergeNodeSequence::getNextNodeInChain() const noexcept
{
    assert(_cmd.getChain().size() < _sortedNodes.size());
    if (_use_unordered_forwarding) {
        return unordered_nodes()[_cmd.getChain().size() + 1].index;
    }
    // assert(_sortedNodes[_cmd.getChain().size()].index == _thisIndex);
    if (_sortedNodes[_cmd.getChain().size()].index != _thisIndex) {
        // Some added paranoia output
        LOG(error, "For %s;_sortedNodes[%zu].index (%u) != %u",
            _cmd.toString().c_str(), _cmd.getChain().size(),
            _sortedNodes[_cmd.getChain().size()].index, _thisIndex);
        assert(!"_sortedNodes[_cmd.getChain().size()].index != _thisIndex) failed");
    }
    return _sortedNodes[_cmd.getChain().size() + 1].index;
}

bool
MergeThrottler::MergeNodeSequence::isChainCompleted() const noexcept
{
    if (_cmd.getChain().size() != _sortedNodes.size()) return false;

    if (_use_unordered_forwarding) {
        return true; // Expect chain to be correct if size matches node sequence size. TODO can't we always do this?
    }

    for (size_t i = 0; i < _cmd.getChain().size(); ++i) {
        if (_cmd.getChain()[i] != _sortedNodes[i].index) {
            return false;
        }
    }
    return true;
}

bool
MergeThrottler::MergeNodeSequence::chain_contains_this_node() const noexcept
{
    for (size_t i = 0; i < _cmd.getChain().size(); ++i) {
        if (_cmd.getChain()[i] == _thisIndex) {
            return true;
        }
    }
    return false;
}

MergeThrottler::MergeThrottler(
        const StorServerConfig& bootstrap_config,
        StorageComponentRegister& comp_reg,
        const vespalib::HwInfo& hw_info)
    : StorageLink("Merge Throttler"),
      framework::HtmlStatusReporter("merges", "Merge Throttler"),
      _hw_info(hw_info),
      _merges(),
      _queue(),
      _maxQueueSize(1024),
      _throttlePolicy(std::make_unique<mbus::DynamicThrottlePolicy>()),
      _queueSequence(0),
      _messageLock(),
      _stateLock(),
      _metrics(std::make_unique<Metrics>()),
      _component(comp_reg, "mergethrottler"),
      _thread(),
      _rendezvous(RendezvousState::NONE),
      _throttle_until_time(),
      _backpressure_duration(std::chrono::seconds(30)),
      _active_merge_memory_used_bytes(0),
      _max_merge_memory_usage_bytes(0), // 0 ==> unlimited
      _use_dynamic_throttling(false),
      _closing(false)
{
    _throttlePolicy->setMinWindowSize(20);
    _throttlePolicy->setMaxWindowSize(20);
    on_configure(bootstrap_config);
    _component.registerStatusPage(*this);
    _component.registerMetric(*_metrics);
}

void
MergeThrottler::on_configure(const StorServerConfig& new_config)
{
    std::lock_guard lock(_stateLock);
    _use_dynamic_throttling = (new_config.mergeThrottlingPolicy.type
                               == StorServerConfig::MergeThrottlingPolicy::Type::DYNAMIC);
    if (new_config.maxMergesPerNode < 1) {
        throw config::InvalidConfigException("Cannot have a max merge count of less than 1");
    }
    if (new_config.maxMergeQueueSize < 0) {
        throw config::InvalidConfigException("Max merge queue size cannot be less than 0");
    }
    if (new_config.resourceExhaustionMergeBackPressureDurationSecs < 0.0) {
        throw config::InvalidConfigException("Merge back-pressure duration cannot be less than 0");
    }
    if (_use_dynamic_throttling) {
        auto min_win_sz = std::max(new_config.mergeThrottlingPolicy.minWindowSize, 1);
        auto max_win_sz = std::max(new_config.mergeThrottlingPolicy.maxWindowSize, 1);
        if (min_win_sz > max_win_sz) {
            min_win_sz = max_win_sz;
        }
        auto win_sz_increment = std::max(1.0, new_config.mergeThrottlingPolicy.windowSizeIncrement);
        _throttlePolicy->setMinWindowSize(min_win_sz);
        _throttlePolicy->setMaxWindowSize(max_win_sz);
        _throttlePolicy->setWindowSizeIncrement(win_sz_increment);
        LOG(debug, "Using dynamic throttling window min/max [%d, %d], win size increment %.2g",
            min_win_sz, max_win_sz, win_sz_increment);
    } else {
        // Use legacy config values when static throttling is enabled.
        _throttlePolicy->setMinWindowSize(new_config.maxMergesPerNode);
        _throttlePolicy->setMaxWindowSize(new_config.maxMergesPerNode);
    }
    LOG(debug, "Setting new max queue size to %d", new_config.maxMergeQueueSize);
    _maxQueueSize = new_config.maxMergeQueueSize;
    _backpressure_duration = std::chrono::duration_cast<std::chrono::steady_clock::duration>(
            std::chrono::duration<double>(new_config.resourceExhaustionMergeBackPressureDurationSecs));
    if (new_config.mergeThrottlingMemoryLimit.maxUsageBytes > 0) {
        _max_merge_memory_usage_bytes = static_cast<size_t>(new_config.mergeThrottlingMemoryLimit.maxUsageBytes);
    } else if ((new_config.mergeThrottlingMemoryLimit.maxUsageBytes == 0) && (_hw_info.memory().sizeBytes() > 0)) {
        _max_merge_memory_usage_bytes = deduced_memory_limit(new_config);
    } else {
        _max_merge_memory_usage_bytes = 0; // Implies unlimited
    }
    _metrics->merge_memory_limit.set(static_cast<int64_t>(_max_merge_memory_usage_bytes));
}

MergeThrottler::~MergeThrottler()
{
    LOG(debug, "Deleting link %s", toString().c_str());
    if (StorageLink::getState() == StorageLink::OPENED) {
        LOG(error, "Deleted MergeThrottler before calling close()");
        close();
        flush();
    }
    closeNextLink();

    // Sanity checking to find shutdown bug where not all messages have been flushed
    assert(_merges.empty());
    assert(_queue.empty());
    assert(_messagesUp.empty());
    assert(_messagesDown.empty());
}

void
MergeThrottler::onOpen()
{
    _thread = _component.startThread(*this, 30s, 1s);
}

void
MergeThrottler::onClose()
{
    {
        std::lock_guard guard(_messageLock);
        // Note: used to prevent taking locks in different order if onFlush
        // and abortOutdatedMerges are called concurrently, as these need to
        // take both locks in differing orders.
        _closing = true;
    }
    if (LOG_WOULD_LOG(debug)) {
        std::lock_guard lock(_stateLock);
        LOG(debug, "onClose; active: %zu, queued: %zu",
            _merges.size(), _queue.size());
    }
    if (_thread) {
        _thread->interruptAndJoin(_messageCond);
        _thread.reset();
    }
}

void
MergeThrottler::onFlush(bool /*downwards*/)
{
    // Lock state before messages since the latter must be unlocked
    // before the guard starts hauling messages up the chain.
    MessageGuard msgGuard(_stateLock, *this);
    std::lock_guard lock(_messageLock);

    // Abort active merges, queued and up/down pending
    std::vector<api::StorageMessage::SP> flushable;

    for (auto& merge : _merges) {
        // Only generate a reply if the throttler owns the command
        if (merge.second.getMergeCmd().get()) {
            flushable.push_back(merge.second.getMergeCmd());
        } else {
            LOG(debug, "Not generating flush-reply for %s since we don't "
                "own the command", merge.first.toString().c_str());
        }

        DummyMbusReply dummyReply;
        _throttlePolicy->processReply(dummyReply);
    }
    for (auto& entry : _queue) {
        flushable.push_back(entry._msg);
    }
    // Just pass-through everything in the up-queue, since the messages
    // are either replies or commands _we_ have sent and thus cannot
    // send a meaningful reply for
    for (auto& msg : _messagesUp) {
        msgGuard.sendUp(msg);
    }

    std::back_insert_iterator<
        std::vector<api::StorageMessage::SP>
    > inserter(flushable);
    std::copy(_messagesDown.begin(), _messagesDown.end(), inserter);

    for (auto& msg : flushable) {
        // Down-bound merge may be a reply, in which case we ignore it
        // since we can't actually do anything with it now
        if (msg->getType() == api::MessageType::MERGEBUCKET) {
            auto reply = std::make_shared<api::MergeBucketReply>(static_cast<const api::MergeBucketCommand&>(*msg));
            reply->setResult(api::ReturnCode(api::ReturnCode::ABORTED, "Storage node is shutting down"));
            LOG(debug, "Aborted merge since we're flushing: %s", msg->toString().c_str());
            msgGuard.sendUp(reply);
        } else {
            assert(msg->getType() == api::MessageType::MERGEBUCKET_REPLY);
            LOG(debug, "Ignored merge reply since we're flushing: %s", msg->toString().c_str());
        }
    }

    LOG(debug, "Flushed %zu unfinished or pending merge operations", flushable.size());

    _merges.clear();
    _queue.clear();
    _messagesUp.clear();
    _messagesDown.clear();
}

void
MergeThrottler::forwardCommandToNode(
        const api::MergeBucketCommand& mergeCmd,
        uint16_t nodeIndex,
        MessageGuard& msgGuard)
{
    // Push this node onto the chain trace
    std::vector<uint16_t> newChain(mergeCmd.getChain());
    newChain.push_back(_component.getIndex());

    std::shared_ptr<api::MergeBucketCommand> fwdMerge(
            std::make_shared<api::MergeBucketCommand>(
                    mergeCmd.getBucket(),
                    mergeCmd.getNodes(),
                    mergeCmd.getMaxTimestamp(),
                    mergeCmd.getClusterStateVersion(),
                    newChain));
    const auto *cluster_np = _component.cluster_context().cluster_name_ptr();
    fwdMerge->setAddress(api::StorageMessageAddress::create(cluster_np, lib::NodeType::STORAGE, nodeIndex));
    fwdMerge->setSourceIndex(mergeCmd.getSourceIndex());
    fwdMerge->setPriority(mergeCmd.getPriority());
    fwdMerge->setTimeout(mergeCmd.getTimeout());
    fwdMerge->set_use_unordered_forwarding(mergeCmd.use_unordered_forwarding());
    fwdMerge->set_estimated_memory_footprint(mergeCmd.estimated_memory_footprint());
    msgGuard.sendUp(fwdMerge);
}

void
MergeThrottler::removeActiveMerge(ActiveMergeMap::iterator mergeIter)
{
    LOG(debug, "Removed merge for %s from internal state", mergeIter->first.toString().c_str());
    assert(_active_merge_memory_used_bytes >= mergeIter->second._estimated_memory_usage);
    _active_merge_memory_used_bytes -= mergeIter->second._estimated_memory_usage;
    _merges.erase(mergeIter);
    update_active_merge_window_size_metric();
    update_active_merge_memory_usage_metric();
}

api::StorageMessage::SP
MergeThrottler::getNextQueuedMerge()
{
    if (_queue.empty()) {
        return {};
    }

    auto iter = _queue.begin();
    auto entry = *iter;
    entry._startTimer.stop(_metrics->averageQueueWaitingTime);
    _queue.erase(iter);
    return entry._msg;
}

const api::MergeBucketCommand&
MergeThrottler::peek_merge_queue() const noexcept
{
    assert(!_queue.empty());
    return dynamic_cast<const api::MergeBucketCommand&>(*_queue.begin()->_msg);
}

void
MergeThrottler::enqueue_merge_for_later_processing(
        const api::StorageMessage::SP& msg,
        MessageGuard& msgGuard)
{
    LOG(spam, "Enqueuing %s", msg->toString().c_str());
    auto& mergeCmd = static_cast<const api::MergeBucketCommand&>(*msg);
    MergeNodeSequence nodeSeq(mergeCmd, _component.getIndex());
    if (!validateNewMerge(mergeCmd, nodeSeq, msgGuard)) {
        return;
    }
    const bool is_forwarded_merge = !mergeCmd.from_distributor();
    _queue.emplace(msg, _queueSequence++, is_forwarded_merge);
    _metrics->queueSize.set(static_cast<int64_t>(_queue.size()));
}

bool
MergeThrottler::canProcessNewMerge() const
{
    DummyMbusRequest dummyMsg;
    return _throttlePolicy->canSend(dummyMsg, _merges.size());
}

bool
MergeThrottler::isMergeAlreadyKnown(const api::StorageMessage::SP& msg) const
{
    auto& mergeCmd = static_cast<const api::MergeBucketCommand&>(*msg);
    return _merges.find(mergeCmd.getBucket()) != _merges.end();
}

bool
MergeThrottler::rejectMergeIfOutdated(
        const api::StorageMessage::SP& msg,
        uint32_t rejectLessThanVersion,
        MessageGuard& msgGuard) const
{
    // Only reject merge commands! never reject replies (for obvious reasons..)
    assert(msg->getType() == api::MessageType::MERGEBUCKET);

    auto& cmd = static_cast<const api::MergeBucketCommand&>(*msg);

    if (cmd.getClusterStateVersion() == 0
        || cmd.getClusterStateVersion() >= rejectLessThanVersion)
    {
        return false;
    }
    vespalib::asciistream oss;
    oss << "Rejected merge due to outdated cluster state; merge has "
        << "version " << cmd.getClusterStateVersion()
        << ", storage node has version "
        << rejectLessThanVersion;
    sendReply(cmd,
              api::ReturnCode(api::ReturnCode::WRONG_DISTRIBUTION, oss.str()),
              msgGuard, _metrics->chaining);
    LOG(debug, "Immediately rejected %s, due to it having state version < %u",
        cmd.toString().c_str(), rejectLessThanVersion);
    return true;
}

void
MergeThrottler::updateOperationMetrics(
        const api::ReturnCode& result,
        MergeOperationMetrics& metrics) const
{
    switch (result.getResult()) {
    case api::ReturnCode::OK:
        metrics.ok.inc();
        break;
    case api::ReturnCode::NOT_READY:
        metrics.failures.notready.inc();
        break;
    case api::ReturnCode::TIMEOUT:
        metrics.failures.timeout.inc();
        break;
    case api::ReturnCode::ABORTED:
        metrics.failures.aborted.inc();
        break;
    case api::ReturnCode::WRONG_DISTRIBUTION:
        metrics.failures.wrongdistribution.inc();
        break;
    case api::ReturnCode::EXISTS:
        metrics.failures.exists.inc();
        break;
    case api::ReturnCode::REJECTED:
        metrics.failures.rejected.inc();
        break;
    default:
        if (result.isBusy()) {
            metrics.failures.busy.inc();
        } else if (result.isBucketDisappearance()) {
            metrics.failures.bucketnotfound.inc();
        } else {
            metrics.failures.other.inc();
        }
    }
}

void
MergeThrottler::sendReply(const api::MergeBucketCommand& cmd,
                          const api::ReturnCode& result,
                          MessageGuard& msgGuard,
                          MergeOperationMetrics& metrics) const
{
    updateOperationMetrics(result, metrics);
    std::shared_ptr<api::MergeBucketReply> reply(
            std::make_shared<api::MergeBucketReply>(cmd));
    reply->setResult(result);
    msgGuard.sendUp(reply);
}

void
MergeThrottler::rejectOutdatedQueuedMerges(
        MessageGuard& msgGuard,
        uint32_t rejectLessThanVersion)
{
    // Flush all queued merges that have an outdated version
    auto queueEnd = _queue.end();
    for (auto i = _queue.begin(); i != queueEnd;) {
        auto erase_iter = i;
        ++i;
        if (rejectMergeIfOutdated(erase_iter->_msg, rejectLessThanVersion, msgGuard)){
            _queue.erase(erase_iter);
        }
    }
}

// If there's a merge queued and the throttling policy allows for
// the merge to be processed, do so.
bool
MergeThrottler::attemptProcessNextQueuedMerge(MessageGuard& msgGuard)
{
    if (_queue.empty()) {
        return false;
    }
    if ( ! (canProcessNewMerge() && accepting_merge_is_within_memory_limits(peek_merge_queue()))) {
        // Should never reach a non-sending state when there are
        // no to-be-replied merges that can trigger a new processing
        assert(!_merges.empty());
        return false;
    }

    // If we get here, there must be something to dequeue.
    api::StorageMessage::SP msg = getNextQueuedMerge();
    assert(msg);
    // In case of resends and whatnot, it's possible for a merge
    // command to be in the queue while another higher priority
    // command for the same bucket sneaks in front of it and gets
    // a slot. Send BUSY in this case to make the distributor retry
    // later, at which point the existing merge has hopefully gone
    // through and the new one will be effectively a no-op to perform
    if (!isMergeAlreadyKnown(msg)) {
        LOG(spam, "Processing queued merge %s", msg->toString().c_str());
        processNewMergeCommand(msg, msgGuard);
    } else {
        vespalib::asciistream oss;
        oss << "Queued merge " << msg->toString() << " is out of date; it has already "
               "been started by someone else since it was queued";
        LOG(debug, "%s", oss.c_str());
        sendReply(dynamic_cast<const api::MergeBucketCommand&>(*msg),
                  api::ReturnCode(api::ReturnCode::BUSY, oss.str()),
                  msgGuard, _metrics->chaining);
    }
    return true;
}

bool
MergeThrottler::processQueuedMerges(MessageGuard& msgGuard)
{
    bool processed = attemptProcessNextQueuedMerge(msgGuard);
    if (!processed) {
        return false;
    }

    while (processed) {
        processed = attemptProcessNextQueuedMerge(msgGuard);
    }

    return true;
}

void
MergeThrottler::handleRendezvous(std::unique_lock<std::mutex> & guard, std::condition_variable & cond)
{
    if (_rendezvous != RendezvousState::NONE) {
        LOG(spam, "rendezvous requested by external thread; establishing");
        assert(_rendezvous == RendezvousState::REQUESTED);
        _rendezvous = RendezvousState::ESTABLISHED;
        cond.notify_all();
        while (_rendezvous != RendezvousState::RELEASED) {
            cond.wait(guard);
        }
        LOG(spam, "external thread rendezvous released");
        _rendezvous = RendezvousState::NONE;
        cond.notify_all();
    }
}

void
MergeThrottler::run(framework::ThreadHandle& thread)
{
    while (!thread.interrupted()) {
        thread.registerTick(framework::PROCESS_CYCLE);
        std::vector<api::StorageMessage::SP> up;
        std::vector<api::StorageMessage::SP> down;
        {
            std::unique_lock msgLock(_messageLock);
            // If a rendezvous is requested, we must do this here _before_ we
            // swap the message queues. This is so the caller can remove aborted
            // messages from the queues when it knows exactly where this thread
            // is paused and that there cannot be any messages in flight from this
            // runner thread causing race conditions.
            while (_messagesDown.empty()
                   && _messagesUp.empty()
                   && !thread.interrupted()
                   && _rendezvous == RendezvousState::NONE)
            {
               _messageCond.wait_for(msgLock, 1000ms);
                thread.registerTick(framework::WAIT_CYCLE);
            }
            handleRendezvous(msgLock, _messageCond);
            down.swap(_messagesDown);
            up.swap(_messagesUp);
        }

        LOG(spam, "messages up: %zu, down: %zu",
            up.size(), down.size());

        // Message lock has been relinquished. Now actually do something
        // with the messages (which are now owned by this thread). All internal
        // ops are protected by _stateLock.
        MessageGuard msgGuard(_stateLock, *this);
        for (std::size_t i = 0; i < down.size(); ++i) {
            handleMessageDown(down[i], msgGuard);
        }
        for (std::size_t i = 0; i < up.size(); ++i) {
            handleMessageUp(up[i], msgGuard);
        }
    }
    LOG(debug, "Returning from MergeThrottler working thread");
}

bool MergeThrottler::merge_is_backpressure_throttled(const api::MergeBucketCommand& cmd) const {
    if (_throttle_until_time.time_since_epoch().count() == 0) {
        return false;
    }
    if (merge_has_this_node_as_source_only_node(cmd)) {
        return false;
    }
    if (backpressure_mode_active_no_lock()) {
        return true;
    }
    // Avoid sampling the clock when it can't do anything useful.
    _throttle_until_time = std::chrono::steady_clock::time_point{};
    return false;
}

bool MergeThrottler::merge_has_this_node_as_source_only_node(const api::MergeBucketCommand& cmd) const {
    auto self_is_source_only = [self = _component.getIndex()](auto& node) {
        return (node.index == self) && node.sourceOnly;
    };
    return std::any_of(cmd.getNodes().begin(), cmd.getNodes().end(), self_is_source_only);
}

bool MergeThrottler::backpressure_mode_active_no_lock() const {
    return (_component.getClock().getMonotonicTime() < _throttle_until_time);
}

void MergeThrottler::bounce_backpressure_throttled_merge(const api::MergeBucketCommand& cmd, MessageGuard& guard) {
    sendReply(cmd, api::ReturnCode(api::ReturnCode::BUSY,
                                   "Node is throttling merges due to resource exhaustion"),
              guard, _metrics->local);
    _metrics->bounced_due_to_back_pressure.inc();
}

void MergeThrottler::apply_timed_backpressure() {
    MessageGuard msg_guard(_stateLock, *this);
    _throttle_until_time = _component.getClock().getMonotonicTime() + _backpressure_duration;
    backpressure_bounce_all_queued_merges(msg_guard);
}

void MergeThrottler::backpressure_bounce_all_queued_merges(MessageGuard& guard) {
    for (auto& qm : _queue) {
        auto& merge_cmd = dynamic_cast<api::MergeBucketCommand&>(*qm._msg);
        bounce_backpressure_throttled_merge(merge_cmd, guard);
    }
    _queue.clear();
}

bool MergeThrottler::backpressure_mode_active() const {
    std::lock_guard lock(_stateLock);
    return backpressure_mode_active_no_lock();
}

bool MergeThrottler::allow_merge_despite_full_window(const api::MergeBucketCommand& cmd) const noexcept {
    // We cannot let forwarded unordered merges fall into the queue, as that might lead to a deadlock.
    // See comment in may_allow_into_queue() for rationale.
    if (!cmd.use_unordered_forwarding() || cmd.from_distributor()) {
        return false;
    }
    // We'll only get here if we're dealing with an unordered merge that has been forwarded
    // from another content node. In other words, it's a merge we want to handle immediately
    // instead of deferring in the queue for later processing. We already know that the merge
    // window is full, so we must either allow it in regardless or bounce it back. The latter
    // makes the most sense when dynamic throttling is enabled, as NACKed replies count
    // _against_ incrementing the throttling window, thereby implicitly helping to reduce the
    // merge pressure generated by other nodes.
    return !_use_dynamic_throttling;
}

bool MergeThrottler::accepting_merge_is_within_memory_limits(const api::MergeBucketCommand& cmd) const noexcept {
    // Soft-limit on expected memory usage, but always let at least one merge into the active window.
    if ((_max_merge_memory_usage_bytes > 0) && !_merges.empty()) {
        size_t future_usage = _active_merge_memory_used_bytes + cmd.estimated_memory_footprint();
        if (future_usage > _max_merge_memory_usage_bytes) {
            LOG(spam, "Adding merge with memory footprint %u would exceed node soft limit of %zu. "
                      "Current memory usage is %zu, future usage would have been %zu",
                cmd.estimated_memory_footprint(), _max_merge_memory_usage_bytes,
                _active_merge_memory_used_bytes, future_usage);
            return false;
        }
    }
    return true;
}

bool MergeThrottler::may_allow_into_queue(const api::MergeBucketCommand& cmd) const noexcept {
    // We cannot let forwarded unordered merges fall into the queue, as that might lead to a deadlock.
    // Consider the following scenario, with two nodes C0 and C1, each with a low window size of 1 (low
    // limit chosen for demonstration purposes, but is entirely generalizable):
    //  1. Node 0 receives merge M_x for nodes [0, 1], places in active window, forwards to node 1
    //  2. Node 1 receives merge M_y for nodes [1, 0], places in active window, forwards to node 0
    //  3. Node 0 receives merge M_y from node 1. Active window is full, so places in queue
    //  4. Node 1 receives merge M_x from node 0. Active window is full, so places in queue
    //  5. Neither M_x nor M_y will ever complete since they're waiting for resources that cannot be
    //     freed up before they themselves complete. Classic deadlock(tm).
    //
    // We do, however, allow enqueueing unordered merges that come straight from the distributor, as
    // those cannot cause a deadlock at that point in time.
    if (cmd.use_unordered_forwarding()) {
        return cmd.from_distributor();
    }
    return (_queue.size() < _maxQueueSize) || !cmd.from_distributor();
}

// Must be run from worker thread
void
MergeThrottler::handleMessageDown(
        const std::shared_ptr<api::StorageMessage>& msg,
        MessageGuard& msgGuard)
{
    if (msg->getType() == api::MessageType::MERGEBUCKET) {
        auto& mergeCmd = static_cast<const api::MergeBucketCommand&>(*msg);

        uint32_t ourVersion = _component.getStateUpdater().getClusterStateBundle()->getVersion();

        if (mergeCmd.getClusterStateVersion() > ourVersion) {
            LOG(debug, "Merge %s with newer cluster state than us arrived",
                mergeCmd.toString().c_str());
            rejectOutdatedQueuedMerges(msgGuard, mergeCmd.getClusterStateVersion());
        } else if (rejectMergeIfOutdated(msg, ourVersion, msgGuard)) {
            // Skip merge entirely
            return;
        }

        if (merge_is_backpressure_throttled(mergeCmd)) {
            bounce_backpressure_throttled_merge(mergeCmd, msgGuard);
            return;
        }

        if (isMergeAlreadyKnown(msg)) {
            processCycledMergeCommand(msg, msgGuard);
        } else if (accepting_merge_is_within_memory_limits(mergeCmd)
                   && (canProcessNewMerge()
                       || allow_merge_despite_full_window(mergeCmd)))
        {
            processNewMergeCommand(msg, msgGuard);
        } else if (may_allow_into_queue(mergeCmd)) {
            enqueue_merge_for_later_processing(msg, msgGuard);
        } else {
            // No more room at the inn. Return BUSY so that the
            // distributor will wait a bit before retrying
            LOG(debug, "Queue is full; busy-returning %s", mergeCmd.toString().c_str());
            sendReply(mergeCmd, api::ReturnCode(api::ReturnCode::BUSY, "Merge queue is full"),
                      msgGuard, _metrics->local);
        }
    } else {
        assert(msg->getType() == api::MessageType::MERGEBUCKET_REPLY);
        // Will create new unwind reply and send it back in the chain
        processMergeReply(msg, false, msgGuard);
    }
}

void
MergeThrottler::handleMessageUp(
        const std::shared_ptr<api::StorageMessage>& msg,
        MessageGuard& msgGuard)
{
    assert(msg->getType() == api::MessageType::MERGEBUCKET_REPLY);
    auto& mergeReply = static_cast<const api::MergeBucketReply&>(*msg);

    LOG(debug, "Processing %s from persistence layer",
        mergeReply.toString().c_str());

    if (mergeReply.getResult().getResult() != api::ReturnCode::OK) {
        LOG(debug, "Merging failed for %s (%s)",
            mergeReply.toString().c_str(),
            vespalib::string(mergeReply.getResult().getMessage()).c_str());
    }

    processMergeReply(msg, true, msgGuard);

    // Always send up original reply
    msgGuard.sendUp(msg);
}

bool
MergeThrottler::validateNewMerge(
        const api::MergeBucketCommand& mergeCmd,
        const MergeNodeSequence& nodeSeq,
        MessageGuard& msgGuard) const
{
    bool valid = false;
    vespalib::asciistream oss;

    if (nodeSeq.isIndexUnknown()) {
        // Sanity check failure! Merge has been sent to a node
        // not in the node set somehow. Whine to the sender.
        oss << mergeCmd.toString() << " sent to node "
            << _component.getIndex()
            << ", which is not in its forwarding chain";
        LOG(error, "%s", oss.str().data());
    } else if (mergeCmd.getChain().size() >= nodeSeq.unordered_nodes().size()) {
        // Chain is full but we haven't seen the merge! This means
        // the node has probably gone down with a merge it previously
        // forwarded only now coming back to haunt it.
        oss << mergeCmd.toString()
            << " is not in node's internal state, but has a "
            << "full chain, meaning it cannot be forwarded.";
        LOG(debug, "%s", oss.str().data());
    } else if (nodeSeq.chain_contains_this_node()) {
        oss << mergeCmd.toString()
            << " is not in node's internal state, but contains "
            << "this node in its non-full chain. This should not happen!";
        LOG(error, "%s", oss.str().data());
    } else {
        valid = true;
    }

    if (!valid) {
        sendReply(mergeCmd,
                  api::ReturnCode(api::ReturnCode::REJECTED, oss.str()),
                  msgGuard,
                  _metrics->local);
    }
    return valid;
}

void
MergeThrottler::processNewMergeCommand(
        const api::StorageMessage::SP& msg,
        MessageGuard& msgGuard)
{
    auto& mergeCmd = static_cast<const api::MergeBucketCommand&>(*msg);

    MergeNodeSequence nodeSeq(mergeCmd, _component.getIndex());

    if (!validateNewMerge(mergeCmd, nodeSeq, msgGuard)) {
        return;
    }

    // Caller guarantees that there is no merge registered for this bucket yet
    // and that we can fit it into our window.
    // Register the merge now so that it will contribute to filling up our
    // merge throttling window.
    assert(_merges.find(mergeCmd.getBucket()) == _merges.end());
    auto state = _merges.emplace(mergeCmd.getBucket(), ChainedMergeState(msg)).first;
    update_active_merge_window_size_metric();
    _active_merge_memory_used_bytes += mergeCmd.estimated_memory_footprint();
    update_active_merge_memory_usage_metric();

    LOG(debug, "Added merge %s to internal state", mergeCmd.toString().c_str());

    DummyMbusRequest dummyMsg;
    _throttlePolicy->processMessage(dummyMsg);

    bool execute = false;

    // If chain is empty and this node is not the lowest
    // index in the nodeset, immediately execute. Required for
    // backwards compatibility with older distributor versions.
    // TODO remove this
    if (mergeCmd.from_distributor()
        && !mergeCmd.use_unordered_forwarding()
        && (nodeSeq.getSortedNodes()[0].index != _component.getIndex()))
    {
        LOG(debug, "%s has empty chain and was sent to node that "
            "is not the lowest in its node set. Assuming 4.2 distributor "
            "source and performing merge.",
            mergeCmd.toString().c_str());
        execute = true;
    } else {
        if (!nodeSeq.isLastNode()) {
            // When we're not the last node and haven't seen the merge before,
            // we cannot possibly execute the merge yet. Forward to next.
            uint16_t nextNodeInChain = nodeSeq.getNextNodeInChain();
            LOG(debug, "Forwarding merge %s to storage node %u",
                mergeCmd.toString().c_str(), nextNodeInChain);

            forwardCommandToNode(mergeCmd, nextNodeInChain, msgGuard);
        } else if (!nodeSeq.isMergeExecutor()) {
            // Last node, but not the merge executor. Send a final forward
            // to the designated executor node.
            LOG(debug, "%s: node is last in chain, but not merge executor; doing final "
                "forwarding to node %u", mergeCmd.toString().c_str(),
                nodeSeq.getExecutorNodeIndex());

            forwardCommandToNode(
                    mergeCmd, nodeSeq.getExecutorNodeIndex(), msgGuard);
        } else {
            // We are the last node and the designated executor. Make it so!
            // Send down to persistence layer, which will trigger the actual
            // merge operation itself. A MergeBucketReply will be sent up the
            // link once it has been completed
            LOG(debug, "%s: node is last in the chain and designated merge "
                "executor; performing merge", mergeCmd.toString().c_str());
            execute = true;
        }
    }

    // If execute == true, message will be propagated down
    if (execute) {
        state->second.setExecutingLocally(true); // Set as currently executing
        // Relinquish ownership of this message. Otherwise, it would
        // be owned by both the throttler and the persistence layer
        state->second.setMergeCmd(api::StorageCommand::SP());
        msgGuard.sendDown(msg);
    }
}

bool
MergeThrottler::processCycledMergeCommand(
        const api::StorageMessage::SP& msg,
        MessageGuard& msgGuard)
{
    // Since we've already got state registered for this merge, the case
    // here is pretty simple: either we're the executor and the chain
    // is completed, in which case we execute the merge, OR we're not, in
    // which case it means a resend took place. In the latter case, we
    // really have no option but to reject the command.
    // Additionally, there is the case where a merge has been explicitly
    // aborted, in which case we have to immediately send an abortion reply
    // so the cycle can be unwound.

    auto& mergeCmd = static_cast<const api::MergeBucketCommand&>(*msg);

    MergeNodeSequence nodeSeq(mergeCmd, _component.getIndex());

    auto mergeIter = _merges.find(mergeCmd.getBucket());
    assert(mergeIter != _merges.end());

    if (mergeIter->second.isAborted()) {
        LOG(debug,
            "%s: received cycled merge where state indicates merge "
            "has been aborted",
            mergeCmd.toString().c_str());
        sendReply(mergeCmd,
                  api::ReturnCode(api::ReturnCode::ABORTED, "merge marked as "
                                  "aborted due to bucket ownership change"),
                  msgGuard,
                  _metrics->chaining);
        return true;
    }

    // Have to check if merge is already executing to remove chance
    // of resend from previous chain link to mess up our internal state
    if (nodeSeq.isChainCompleted()
        && !mergeIter->second.isExecutingLocally())
    {
        assert(mergeIter->second.getMergeCmd().get() != msg.get());

        mergeIter->second.setExecutingLocally(true);
        // Have to signal that we're in a cycle in order to do unwinding
        mergeIter->second.setInCycle(true);
        LOG(debug, "%s: received cycled merge command and this "
            "node is the designated executor. Performing merge.",
            mergeCmd.toString().c_str());

        // Message should be sent down
        msgGuard.sendDown(msg);
        return false;
    } else {
        LOG(debug, "%s failed: already active merge for this bucket",
            mergeCmd.toString().c_str());
        // Send BUSY, as this is what the persistence layer does for this case
        sendReply(mergeCmd, api::ReturnCode(api::ReturnCode::BUSY,
                          "Already active merge for this bucket"),
                  msgGuard, _metrics->chaining);
    }

    return true;
}

void
MergeThrottler::processMergeReply(
        const std::shared_ptr<api::StorageMessage>& msg,
        bool fromPersistenceLayer,
        MessageGuard& msgGuard)
{
    auto& mergeReply = dynamic_cast<const api::MergeBucketReply&>(*msg);

    auto mergeIter = _merges.find(mergeReply.getBucket());
    if (mergeIter == _merges.end()) {
        LOG(warning, "Received %s, which has no command mapped "
            "for it. Cannot send chained reply!",
            mergeReply.toString().c_str());
        return;
    }

    ChainedMergeState& mergeState = mergeIter->second;

    if (fromPersistenceLayer) {
        assert(mergeState.isExecutingLocally());
        mergeState.setExecutingLocally(false);
        mergeState.setUnwinding(true);

        // If we've cycled around, do NOT remove merge entry yet, as it
        // will be removed during the proper chain unwinding
        if (mergeState.isInCycle()) {
            assert(mergeState.getMergeCmd().get());
            LOG(debug, "Not removing %s yet, since we're in a chain cycle",
                mergeReply.toString().c_str());
            // Next time we encounter the merge, however, it should be removed
            mergeState.setInCycle(false);
            return;
        }
    } else {
        if (mergeState.isExecutingLocally()) {
            assert(mergeState.getMergeCmd().get());
            // If we get a reply for a merge that is not from the persistence layer
            // although it's still being processed there, it means the cycle has
            // been broken, e.g by a node going down/being restarted/etc.
            // Both unwind reply as well as reply to original will be sent
            // when we finally get a reply from the persistence layer
            mergeState.setInCycle(false);
            mergeState.setCycleBroken(true);
            LOG(debug, "Got non-persistence reply for a %s which is currently "
                "executing on this node; marking merge cycle as broken and replying "
                "to both unwind and chain source once we get a reply from persistence",
                mergeReply.toString().c_str());
            return;
        }
    }

    LOG(debug, "Found merge entry for %s, proceeding to unwind chain.",
        mergeReply.toString().c_str());
    // Send reply to the command associated with the merge, if requested.
    // If we have received the reply from the persistence layer, we should
    // not create a new reply since the one we got will already suffice
    // for sending back to the previous link in the chain, UNLESS the
    // cycle has been broken (see above), in which case we MUST send a reply
    // immediately, or there will be merges forever stuck on nodes earlier
    // in the chain
    if (!fromPersistenceLayer || mergeState.isCycleBroken()) {
        assert(mergeState.getMergeCmd().get());
        if (!mergeState.isCycleBroken()) {
            LOG(spam, "Creating new unwind reply to send back for %s",
                mergeState.getMergeCmd()->toString().c_str());
        } else {
            assert(fromPersistenceLayer);
            LOG(debug, "Creating new (broken cycle) unwind reply to send back for %s",
                mergeState.getMergeCmd()->toString().c_str());
        }

        sendReply(static_cast<const api::MergeBucketCommand&>(
                          *mergeState.getMergeCmd()),
                  mergeReply.getResult(), msgGuard, _metrics->chaining);
    } else {
        LOG(spam, "Not creating new unwind reply; using existing "
            "reply from persistence layer");
        updateOperationMetrics(mergeReply.getResult(), _metrics->local);
    }

    DummyMbusReply dummyReply;
    if (mergeReply.getResult().failed()) {
        // Must be sure to add an error if reply contained a failure, since
        // DynamicThrottlePolicy penalizes on failed transmissions
        dummyReply.addError(mbus::Error(mergeReply.getResult().getResult(),
                                        mergeReply.getResult().getMessage()));
    }
    _throttlePolicy->processReply(dummyReply);

    // Remove merge now that we've done our part to unwind the chain
    removeActiveMerge(mergeIter);
    processQueuedMerges(msgGuard);
}

bool
MergeThrottler::onSetSystemState(
        const std::shared_ptr<api::SetSystemStateCommand>& stateCmd)
{
    LOG(debug,
        "New cluster state arrived with version %u, flushing "
        "all outdated queued merges",
        stateCmd->getSystemState().getVersion());
    handleOutdatedMerges(*stateCmd);

    return false;
}

bool
MergeThrottler::onDown(const std::shared_ptr<api::StorageMessage>& msg)
{
    if (isMergeCommand(*msg) || isMergeReply(*msg)) {
        {
            std::lock_guard lock(_messageLock);
            _messagesDown.push_back(msg);
        }
        _messageCond.notify_all();
        return true;
    } else if (isDiffCommand(*msg)) {
        std::lock_guard lock(_stateLock);
        auto& cmd = static_cast<api::StorageCommand&>(*msg);
        if (bucketIsUnknownOrAborted(cmd.getBucket())) {
            sendUp(makeAbortReply(cmd, "no state recorded for bucket in merge "
                                  "throttler, source merge probably aborted earlier"));
            return true;
        }
    }
    return StorageLink::onDown(msg);
}

bool
MergeThrottler::isDiffCommand(const api::StorageMessage& msg) const
{
    return (msg.getType() == api::MessageType::GETBUCKETDIFF
            || msg.getType() == api::MessageType::APPLYBUCKETDIFF);
}

bool
MergeThrottler::isMergeCommand(const api::StorageMessage& msg) const
{
    return (msg.getType() == api::MessageType::MERGEBUCKET);
}

bool
MergeThrottler::isMergeReply(const api::StorageMessage& msg) const
{
    return (msg.getType() == api::MessageType::MERGEBUCKET_REPLY);
}

bool
MergeThrottler::bucketIsUnknownOrAborted(const document::Bucket& bucket) const
{
    auto it = _merges.find(bucket);
    if (it == _merges.end()) {
        return true;
    }
    return it->second.isAborted();
}

std::shared_ptr<api::StorageMessage>
MergeThrottler::makeAbortReply(api::StorageCommand& cmd,
                               std::string_view reason) const
{
    LOG(debug, "Aborting message %s with reason '%s'",
        cmd.toString().c_str(), std::string(reason).c_str());
    std::unique_ptr<api::StorageReply> reply(cmd.makeReply());
    reply->setResult(api::ReturnCode(api::ReturnCode::ABORTED, reason));
    return std::shared_ptr<api::StorageMessage>(reply.release());
}

bool
MergeThrottler::onUp(const std::shared_ptr<api::StorageMessage>& msg)
{
    if (isMergeReply(*msg)) {
        auto& mergeReply = dynamic_cast<const api::MergeBucketReply&>(*msg);

        LOG(spam, "Received %s from persistence layer",
            mergeReply.toString().c_str());

        {
            std::lock_guard lock(_messageLock);
            _messagesUp.push_back(msg);
        }
        _messageCond.notify_all();
        return true;
    }
    return false;
}

void
MergeThrottler::rendezvousWithWorkerThread(std::unique_lock<std::mutex> & guard, std::condition_variable & cond)
{
    LOG(spam, "establishing rendezvous with worker thread");
    assert(_rendezvous == RendezvousState::NONE);
    _rendezvous = RendezvousState::REQUESTED;
    cond.notify_all();
    while (_rendezvous != RendezvousState::ESTABLISHED) {
        cond.wait(guard);
    }
    LOG(spam, "rendezvous established with worker thread");
}

void
MergeThrottler::releaseWorkerThreadRendezvous(std::unique_lock<std::mutex> & guard, std::condition_variable & cond)
{
    _rendezvous = RendezvousState::RELEASED;
    cond.notify_all();
    while (_rendezvous != RendezvousState::NONE) {
        cond.wait(guard);
    }
}

class ThreadRendezvousGuard {
    MergeThrottler& _throttler;
    std::unique_lock<std::mutex> & _guard;
    std::condition_variable & _cond;
public:
    ThreadRendezvousGuard(MergeThrottler& throttler, std::unique_lock<std::mutex> & guard, std::condition_variable & cond)
        : _throttler(throttler),
          _guard(guard),
          _cond(cond)
    {
        _throttler.rendezvousWithWorkerThread(_guard, _cond);
    }

    ~ThreadRendezvousGuard() {
        _throttler.releaseWorkerThreadRendezvous(_guard, _cond);
    }
};

void
MergeThrottler::handleOutdatedMerges(const api::SetSystemStateCommand& cmd)
{
    // When aborting merges, we must--before allowing message to go
    // through--ensure that there are no queued or active merges for
    // any of the aborted buckets. We must also rendezvous with the
    // worker thread to ensure it does not have any concurrent messages
    // in flight that can slip by our radar.
    // Ideally, we'd be able to just rely on the existing version check when
    // receiving merges, but this uses the _server_ object's cluster state,
    // which isn't set yet at the time we get the new state command, so
    // there exists a time window where outdated merges can be accepted. Blarg!
    std::unique_lock guard(_messageLock);
    ThreadRendezvousGuard rzGuard(*this, guard, _messageCond);

    if (_closing) return; // Shutting down anyway.

    // No other code than this function and onFlush() should ever take both the
    // message monitor and state lock at the same time, and onFlush() should
    // never be called unless _closing is true. So it's impossible for this to
    // deadlock given these assumptions, despite using differing acquisition
    // ordering.
    try {
        MessageGuard stateGuard(_stateLock, *this);

        uint32_t minimumVersion = cmd.getSystemState().getVersion();
        rejectOperationsInThreadQueue(stateGuard, minimumVersion);
        rejectOutdatedQueuedMerges(stateGuard, minimumVersion);
        markActiveMergesAsAborted(minimumVersion);
    } catch (std::exception& e) {
        LOG(error, "Received exception during merge aborting: %s", e.what());
        LOG_ABORT("should not be reached");
    }

    // Rendezvous released on scope exit
}

void
MergeThrottler::rejectOperationsInThreadQueue(
        MessageGuard& guard,
        uint32_t minimumStateVersion)
{
    std::vector<api::StorageMessage::SP> messagesToLetThrough;
    for (uint32_t i = 0; i < _messagesDown.size(); ++i) {
        api::StorageMessage::SP& msg(_messagesDown[i]);
        if (isMergeCommand(*msg)
            && rejectMergeIfOutdated(msg, minimumStateVersion, guard))
        {
        } else {
            messagesToLetThrough.push_back(msg);
        }
    }
    _messagesDown.swap(messagesToLetThrough);
}

void
MergeThrottler::markActiveMergesAsAborted(uint32_t minimumStateVersion)
{
    // Since actually sending abort replies for the merges already chained
    // would pretty seriously mess up the assumptions we've made in the
    // rest of the code, merely mark the merges as aborted. This will ensure
    // that no diff commands can get through for them and that cycled merges
    // are cut short.
    for (auto& activeMerge : _merges) {
        if (activeMerge.second._clusterStateVersion < minimumStateVersion) {
            LOG(spam,
                "Marking merge state for bucket %s as aborted",
                activeMerge.first.toString().c_str());
            activeMerge.second.setAborted(true);
        }
    }
}

void
MergeThrottler::set_max_merge_memory_usage_bytes_locking(uint32_t max_memory_bytes) noexcept {
    std::lock_guard lock(_stateLock);
    _max_merge_memory_usage_bytes = max_memory_bytes;
}

uint32_t
MergeThrottler::max_merge_memory_usage_bytes_locking() const noexcept {
    std::lock_guard lock(_stateLock);
    return _max_merge_memory_usage_bytes;
}

void
MergeThrottler::set_hw_info_locking(const vespalib::HwInfo& hw_info) {
    std::lock_guard lock(_stateLock);
    _hw_info = hw_info;
}

size_t
MergeThrottler::deduced_memory_limit(const StorServerConfig& cfg) const noexcept {
    const auto min_limit = static_cast<size_t>(std::max(cfg.mergeThrottlingMemoryLimit.autoLowerBoundBytes, INT64_C(1)));
    const auto max_limit = std::max(static_cast<size_t>(std::max(cfg.mergeThrottlingMemoryLimit.autoUpperBoundBytes, INT64_C(1))), min_limit);
    const auto mem_scale_factor = std::max(cfg.mergeThrottlingMemoryLimit.autoPhysMemScaleFactor, 0.0);

    const auto node_mem   = static_cast<double>(_hw_info.memory().sizeBytes());
    const auto scaled_mem = static_cast<size_t>(node_mem * mem_scale_factor);

    return std::min(std::max(scaled_mem, min_limit), max_limit);
}

void
MergeThrottler::update_active_merge_window_size_metric() noexcept {
    _metrics->active_window_size.set(static_cast<int64_t>(_merges.size()));
}

void
MergeThrottler::update_active_merge_memory_usage_metric() noexcept {
    _metrics->estimated_merge_memory_usage.set(static_cast<int64_t>(_active_merge_memory_used_bytes));
}

void
MergeThrottler::print(std::ostream& out, bool /*verbose*/,
                      const std::string& /*indent*/) const
{
    out << "MergeThrottler";
}

void
MergeThrottler::reportHtmlStatus(std::ostream& out,
                                 const framework::HttpUrlPath&) const
{
    using vespalib::xml_content_escaped;

    std::lock_guard lock(_stateLock);
    if (_use_dynamic_throttling) {
        out << "<p>Dynamic throttle policy; window size min/max: ["
               << _throttlePolicy->getMinWindowSize() << ", "
               << _throttlePolicy->getMaxWindowSize()
               << "], current window size: "
               << _throttlePolicy->getMaxPendingCount()
               << "</p>\n";
    } else {
        out << "<p>Static throttle policy; max pending: "
            << _throttlePolicy->getMaxPendingCount()
            << "</p>\n";
    }
    out << "<p>Please see node metrics for performance numbers</p>\n";
    out << "<h3>Active merges ("
        << _merges.size()
        << ")</h3>\n";
    if (!_merges.empty()) {
        out << "<ul>\n";
        for (auto& m : _merges) {
            out << "<li>" << xml_content_escaped(m.second.getMergeCmdString());
            if (m.second.isExecutingLocally()) {
                out << " <strong>(";
                if (m.second.isInCycle()) {
                    out << "cycled - ";
                } else if (m.second.isCycleBroken()) {
                    out << "broken cycle (another node in the chain likely went down) - ";
                }
                out << "executing on this node)</strong>";
            } else if (m.second.isUnwinding()) {
                out << " <strong>(was executed here, now unwinding)</strong>";
            }
            if (m.second.isAborted()) {
                out << " <strong>aborted</strong>";
            }
            out << "</li>\n";
        }
    out << "</ul>\n";
    } else {
        out << "<p>None</p>\n";
    }

    out << "<h3>Queued merges (in priority order) ("
        << _queue.size()
        << ")</h3>\n";
    if (!_queue.empty()) {
        out << "<ol>\n";
        for (auto& qm : _queue) {
            // The queue always owns its messages, thus this is safe
            out << "<li>Pri "
                << static_cast<unsigned int>(qm._msg->getPriority())
                << ": " << xml_content_escaped(qm._msg->toString());
            out << "</li>\n";
        }
        out << "</ol>\n";
    } else {
        out << "<p>None</p>\n";
    }
}

} // namespace storage
