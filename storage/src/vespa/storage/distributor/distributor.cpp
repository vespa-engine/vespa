// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//
#include "blockingoperationstarter.h"
#include "distributor.h"
#include "distributor_bucket_space.h"
#include "distributormetricsset.h"
#include "idealstatemetricsset.h"
#include "operation_sequencer.h"
#include "ownership_transfer_safe_time_point_calculator.h"
#include "throttlingoperationstarter.h"
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/storage/common/global_bucket_space_distribution_converter.h>
#include <vespa/storage/common/hostreporter/hostinfo.h>
#include <vespa/storage/common/node_identity.h>
#include <vespa/storage/common/nodestateupdater.h>
#include <vespa/storage/distributor/maintenance/simplebucketprioritydatabase.h>
#include <vespa/storageframework/generic/status/xmlstatusreporter.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vespalib/util/memoryusage.h>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP(".distributor-main");

using namespace std::chrono_literals;

namespace storage::distributor {

class Distributor::Status {
    const DelegatedStatusRequest& _request;
    std::mutex              _lock;
    std::condition_variable _cond;
    bool                    _done;

public:
    Status(const DelegatedStatusRequest& request) noexcept
        : _request(request),
          _lock(),
          _cond(),
          _done(false)
    {}

    std::ostream& getStream() {
        return _request.outputStream;
    }
    const framework::HttpUrlPath& getPath() const {
        return _request.path;
    }
    const framework::StatusReporter& getReporter() const {
        return _request.reporter;
    }

    void notifyCompleted() {
        {
            std::lock_guard guard(_lock);
            _done = true;
        }
        _cond.notify_all();
    }
    void waitForCompletion() {
        std::unique_lock guard(_lock);
        while (!_done) {
            _cond.wait(guard);
        }
    }
};

Distributor::Distributor(DistributorComponentRegister& compReg,
                         const NodeIdentity& node_identity,
                         framework::TickingThreadPool& threadPool,
                         DoneInitializeHandler& doneInitHandler,
                         bool manageActiveBucketCopies,
                         HostInfo& hostInfoReporterRegistrar,
                         ChainedMessageSender* messageSender)
    : StorageLink("distributor"),
      DistributorInterface(),
      framework::StatusReporter("distributor", "Distributor"),
      _clusterStateBundle(lib::ClusterState()),
      _bucketSpaceRepo(std::make_unique<DistributorBucketSpaceRepo>(node_identity.node_index())),
      _readOnlyBucketSpaceRepo(std::make_unique<DistributorBucketSpaceRepo>(node_identity.node_index())),
      _component(*this, *_bucketSpaceRepo, *_readOnlyBucketSpaceRepo, compReg, "distributor"),
      _metrics(std::make_shared<DistributorMetricSet>()),
      _operationOwner(*this, _component.getClock()),
      _maintenanceOperationOwner(*this, _component.getClock()),
      _operation_sequencer(std::make_unique<OperationSequencer>()),
      _pendingMessageTracker(compReg),
      _bucketDBUpdater(*this, *_bucketSpaceRepo, *_readOnlyBucketSpaceRepo, *this, compReg),
      _distributorStatusDelegate(compReg, *this, *this),
      _bucketDBStatusDelegate(compReg, *this, _bucketDBUpdater),
      _idealStateManager(*this, *_bucketSpaceRepo, *_readOnlyBucketSpaceRepo, compReg, manageActiveBucketCopies),
      _messageSender(messageSender),
      _externalOperationHandler(_component, _component, getMetrics(), getMessageSender(),
                                *_operation_sequencer, *this, _component,
                                _idealStateManager, _operationOwner),
      _threadPool(threadPool),
      _initializingIsUp(true),
      _doneInitializeHandler(doneInitHandler),
      _doneInitializing(false),
      _bucketPriorityDb(std::make_unique<SimpleBucketPriorityDatabase>()),
      _scanner(std::make_unique<SimpleMaintenanceScanner>(*_bucketPriorityDb, _idealStateManager, *_bucketSpaceRepo)),
      _throttlingStarter(std::make_unique<ThrottlingOperationStarter>(_maintenanceOperationOwner)),
      _blockingStarter(std::make_unique<BlockingOperationStarter>(_pendingMessageTracker, *_operation_sequencer,
                                                                  *_throttlingStarter)),
      _scheduler(std::make_unique<MaintenanceScheduler>(_idealStateManager, *_bucketPriorityDb, *_blockingStarter)),
      _schedulingMode(MaintenanceScheduler::NORMAL_SCHEDULING_MODE),
      _recoveryTimeStarted(_component.getClock()),
      _tickResult(framework::ThreadWaitInfo::NO_MORE_CRITICAL_WORK_KNOWN),
      _bucketIdHasher(std::make_unique<BucketGcTimeCalculator::BucketIdIdentityHasher>()),
      _metricUpdateHook(*this),
      _metricLock(),
      _maintenanceStats(),
      _bucketSpacesStats(),
      _bucketDbStats(),
      _hostInfoReporter(*this, *this),
      _ownershipSafeTimeCalc(std::make_unique<OwnershipTransferSafeTimePointCalculator>(0s)), // Set by config later
      _db_memory_sample_interval(30s),
      _last_db_memory_sample_time_point(),
      _inhibited_maintenance_tick_count(0),
      _must_send_updated_host_info(false)
{
    _component.registerMetric(*_metrics);
    _component.registerMetricUpdateHook(_metricUpdateHook, framework::SecondTime(0));
    _distributorStatusDelegate.registerStatusPage();
    _bucketDBStatusDelegate.registerStatusPage();
    hostInfoReporterRegistrar.registerReporter(&_hostInfoReporter);
    propagateDefaultDistribution(_component.getDistribution());
    propagateClusterStates();
};

Distributor::~Distributor()
{
    // XXX: why is there no _component.unregisterMetricUpdateHook()?
    closeNextLink();
}

int
Distributor::getDistributorIndex() const
{
    return _component.getIndex();
}

const PendingMessageTracker&
Distributor::getPendingMessageTracker() const
{
    return _pendingMessageTracker;
}

const lib::ClusterState*
Distributor::pendingClusterStateOrNull(const document::BucketSpace& space) const {
    return _bucketDBUpdater.pendingClusterStateOrNull(space);
}

void
Distributor::sendCommand(const std::shared_ptr<api::StorageCommand>& cmd)
{
    if (cmd->getType() == api::MessageType::MERGEBUCKET) {
        api::MergeBucketCommand& merge(static_cast<api::MergeBucketCommand&>(*cmd));
        _idealStateManager.getMetrics().nodesPerMerge.addValue(merge.getNodes().size());
    }
    sendUp(cmd);
}

void
Distributor::sendReply(const std::shared_ptr<api::StorageReply>& reply)
{
    sendUp(reply);
}

void
Distributor::setNodeStateUp()
{
    NodeStateUpdater::Lock::SP lock(_component.getStateUpdater().grabStateChangeLock());
    lib::NodeState ns(*_component.getStateUpdater().getReportedNodeState());
    ns.setState(lib::State::UP);
    _component.getStateUpdater().setReportedNodeState(ns);
}

void
Distributor::onOpen()
{
    LOG(debug, "Distributor::onOpen invoked");
    setNodeStateUp();
    framework::MilliSecTime maxProcessingTime(60 * 1000);
    framework::MilliSecTime waitTime(1000);
    if (_component.getDistributorConfig().startDistributorThread) {
        _threadPool.addThread(*this);
        _threadPool.start(_component.getThreadPool());
    } else {
        LOG(warning, "Not starting distributor thread as it's configured to "
                     "run. Unless you are just running a test tool, this is a "
                     "fatal error.");
    }
}

void Distributor::send_shutdown_abort_reply(const std::shared_ptr<api::StorageMessage>& msg) {
    api::StorageReply::UP reply(
            std::dynamic_pointer_cast<api::StorageCommand>(msg)->makeReply());
    reply->setResult(api::ReturnCode(api::ReturnCode::ABORTED, "Distributor is shutting down"));
    sendUp(std::shared_ptr<api::StorageMessage>(reply.release()));
}

void Distributor::onClose() {
    for (auto& msg : _messageQueue) {
        if (!msg->getType().isReply()) {
            send_shutdown_abort_reply(msg);
        }
    }
    _messageQueue.clear();
    while (!_client_request_priority_queue.empty()) {
        send_shutdown_abort_reply(_client_request_priority_queue.top());
        _client_request_priority_queue.pop();
    }

    LOG(debug, "Distributor::onClose invoked");
    _pendingMessageTracker.abort_deferred_tasks();
    _bucketDBUpdater.flush();
    _externalOperationHandler.close_pending();
    _operationOwner.onClose();
    _maintenanceOperationOwner.onClose();
}

void Distributor::send_up_without_tracking(const std::shared_ptr<api::StorageMessage>& msg) {
    if (_messageSender) {
        _messageSender->sendUp(msg);
    } else {
        StorageLink::sendUp(msg);
    }
}

void
Distributor::sendUp(const std::shared_ptr<api::StorageMessage>& msg)
{
    _pendingMessageTracker.insert(msg);
    send_up_without_tracking(msg);
}

void
Distributor::sendDown(const std::shared_ptr<api::StorageMessage>& msg)
{
    if (_messageSender) {
        _messageSender->sendDown(msg);
    } else {
        StorageLink::sendDown(msg);
    }
}

bool
Distributor::onDown(const std::shared_ptr<api::StorageMessage>& msg)
{
    if (_externalOperationHandler.try_handle_message_outside_main_thread(msg)) {
        return true;
    }
    framework::TickingLockGuard guard(_threadPool.freezeCriticalTicks());
    MBUS_TRACE(msg->getTrace(), 9,
               "Distributor: Added to message queue. Thread state: "
               + _threadPool.getStatus());
    _messageQueue.push_back(msg);
    guard.broadcast();
    return true;
}

void
Distributor::handleCompletedMerge(
        const std::shared_ptr<api::MergeBucketReply>& reply)
{
    _maintenanceOperationOwner.handleReply(reply);
}

bool
Distributor::isMaintenanceReply(const api::StorageReply& reply) const
{
    switch (reply.getType().getId()) {
    case api::MessageType::CREATEBUCKET_REPLY_ID:
    case api::MessageType::MERGEBUCKET_REPLY_ID:
    case api::MessageType::DELETEBUCKET_REPLY_ID:
    case api::MessageType::REQUESTBUCKETINFO_REPLY_ID:
    case api::MessageType::SPLITBUCKET_REPLY_ID:
    case api::MessageType::JOINBUCKETS_REPLY_ID:
    case api::MessageType::SETBUCKETSTATE_REPLY_ID:
    case api::MessageType::REMOVELOCATION_REPLY_ID:
        return true;
    default:
        return false;
    }
}

bool
Distributor::handleReply(const std::shared_ptr<api::StorageReply>& reply)
{
    document::Bucket bucket = _pendingMessageTracker.reply(*reply);

    if (reply->getResult().getResult() == api::ReturnCode::BUCKET_NOT_FOUND &&
        bucket.getBucketId() != document::BucketId(0) &&
        reply->getAddress())
    {
        recheckBucketInfo(reply->getAddress()->getIndex(), bucket);
    }

    if (reply->callHandler(_bucketDBUpdater, reply)) {
        return true;
    }

    if (_operationOwner.handleReply(reply)) {
        return true;
    }

    if (_maintenanceOperationOwner.handleReply(reply)) {
        _scanner->prioritizeBucket(bucket);
        return true;
    }

    // If it's a maintenance operation reply, it's most likely a reply to an
    // operation whose state was flushed from the distributor when its node
    // went down in the cluster state. Just swallow the reply to avoid getting
    // warnings about unhandled messages at the bottom of the link chain.
    return isMaintenanceReply(*reply);
}

bool
Distributor::generateOperation(
        const std::shared_ptr<api::StorageMessage>& msg,
        Operation::SP& operation)
{
    return _externalOperationHandler.handleMessage(msg, operation);
}

bool
Distributor::handleMessage(const std::shared_ptr<api::StorageMessage>& msg)
{
    if (msg->getType().isReply()) {
        auto reply = std::dynamic_pointer_cast<api::StorageReply>(msg);
        if (handleReply(reply)) {
            return true;
        }
    }

    if (msg->callHandler(_bucketDBUpdater, msg)) {
        return true;
    }

    Operation::SP operation;
    if (generateOperation(msg, operation)) {
        if (operation.get()) {
            _operationOwner.start(operation, msg->getPriority());
        }
        return true;
    }

    return false;
}

const lib::ClusterStateBundle&
Distributor::getClusterStateBundle() const
{
    return _clusterStateBundle;
}

void
Distributor::enableClusterStateBundle(const lib::ClusterStateBundle& state)
{
    lib::ClusterStateBundle oldState = _clusterStateBundle;
    _clusterStateBundle = state;
    propagateClusterStates();

    lib::Node myNode(lib::NodeType::DISTRIBUTOR, _component.getIndex());
    const auto &baselineState = *_clusterStateBundle.getBaselineClusterState();

    if (!_doneInitializing &&
        baselineState.getNodeState(myNode).getState() == lib::State::UP)
    {
        _doneInitializing = true;
        _doneInitializeHandler.notifyDoneInitializing();
    }
    enterRecoveryMode();

    // Clear all active messages on nodes that are down.
    const uint16_t old_node_count = oldState.getBaselineClusterState()->getNodeCount(lib::NodeType::STORAGE);
    const uint16_t new_node_count = baselineState.getNodeCount(lib::NodeType::STORAGE);
    for (uint16_t i = 0; i < std::max(old_node_count, new_node_count); ++i) {
        const auto& node_state = baselineState.getNodeState(lib::Node(lib::NodeType::STORAGE, i)).getState();
        if (!node_state.oneOf(getStorageNodeUpStates())) {
            std::vector<uint64_t> msgIds = _pendingMessageTracker.clearMessagesForNode(i);
            LOG(debug, "Node %u is down, clearing %zu pending maintenance operations", i, msgIds.size());

            for (uint32_t j = 0; j < msgIds.size(); ++j) {
                _maintenanceOperationOwner.erase(msgIds[j]);
            }
        }
    }

    if (_bucketDBUpdater.bucketOwnershipHasChanged()) {
        using TimePoint = OwnershipTransferSafeTimePointCalculator::TimePoint;
        // Note: this assumes that std::chrono::system_clock and the framework
        // system clock have the same epoch, which should be a reasonable
        // assumption.
        const auto now = TimePoint(std::chrono::milliseconds(
                _component.getClock().getTimeInMillis().getTime()));
        _externalOperationHandler.rejectFeedBeforeTimeReached(
                _ownershipSafeTimeCalc->safeTimePoint(now));
    }
}

OperationRoutingSnapshot Distributor::read_snapshot_for_bucket(const document::Bucket& bucket) const {
    return _bucketDBUpdater.read_snapshot_for_bucket(bucket);
}

void
Distributor::notifyDistributionChangeEnabled()
{
    LOG(debug, "Pending cluster state for distribution change has been enabled");
    // Trigger a re-scan of bucket database, just like we do when a new cluster
    // state has been enabled.
    enterRecoveryMode();
}

void
Distributor::enterRecoveryMode()
{
    LOG(debug, "Entering recovery mode");
    _schedulingMode = MaintenanceScheduler::RECOVERY_SCHEDULING_MODE;
    _scanner->reset();
    _bucketDBMetricUpdater.reset();
    // TODO reset _bucketDbStats?
    invalidate_bucket_spaces_stats();

    _recoveryTimeStarted = framework::MilliSecTimer(_component.getClock());
}

void
Distributor::leaveRecoveryMode()
{
    if (isInRecoveryMode()) {
        LOG(debug, "Leaving recovery mode");
        _metrics->recoveryModeTime.addValue(
                _recoveryTimeStarted.getElapsedTimeAsDouble());
        if (_doneInitializing) {
            _must_send_updated_host_info = true;
        }
    }
    _schedulingMode = MaintenanceScheduler::NORMAL_SCHEDULING_MODE;
}

template <typename NodeFunctor>
void Distributor::for_each_available_content_node_in(const lib::ClusterState& state, NodeFunctor&& func) {
    const auto node_count = state.getNodeCount(lib::NodeType::STORAGE);
    for (uint16_t i = 0; i < node_count; ++i) {
        lib::Node node(lib::NodeType::STORAGE, i);
        if (state.getNodeState(node).getState().oneOf("uir")) {
            func(node);
        }
    }
}

BucketSpacesStatsProvider::BucketSpacesStats Distributor::make_invalid_stats_per_configured_space() const {
    BucketSpacesStatsProvider::BucketSpacesStats invalid_space_stats;
    for (auto& space : *_bucketSpaceRepo) {
        invalid_space_stats.emplace(document::FixedBucketSpaces::to_string(space.first),
                                    BucketSpaceStats::make_invalid());
    }
    return invalid_space_stats;
}

void Distributor::invalidate_bucket_spaces_stats() {
    std::lock_guard guard(_metricLock);
    _bucketSpacesStats = BucketSpacesStatsProvider::PerNodeBucketSpacesStats();
    auto invalid_space_stats = make_invalid_stats_per_configured_space();

    const auto& baseline = *_clusterStateBundle.getBaselineClusterState();
    for_each_available_content_node_in(baseline, [this, &invalid_space_stats](const lib::Node& node) {
        _bucketSpacesStats[node.getIndex()] = invalid_space_stats;
    });
}

void
Distributor::storageDistributionChanged()
{
    if (!_distribution.get()
        || *_component.getDistribution() != *_distribution)
    {
        LOG(debug,
            "Distribution changed to %s, must refetch bucket information",
            _component.getDistribution()->toString().c_str());

        // FIXME this is not thread safe
        _nextDistribution = _component.getDistribution();
    } else {
        LOG(debug,
            "Got distribution change, but the distribution %s was the same as "
            "before: %s",
            _component.getDistribution()->toString().c_str(),
            _distribution->toString().c_str());
    }
}

void
Distributor::recheckBucketInfo(uint16_t nodeIdx, const document::Bucket &bucket) {
    _bucketDBUpdater.recheckBucketInfo(nodeIdx, bucket);
}

namespace {

class MaintenanceChecker : public PendingMessageTracker::Checker
{
public:
    bool found;

    MaintenanceChecker() : found(false) {};

    bool check(uint32_t msgType, uint16_t node, uint8_t pri) override {
        (void) node;
        (void) pri;
        for (uint32_t i = 0;
             IdealStateOperation::MAINTENANCE_MESSAGE_TYPES[i] != 0;
             ++i)
        {
            if (msgType == IdealStateOperation::MAINTENANCE_MESSAGE_TYPES[i]) {
                found = true;
                return false;
            }
        }
        return true;
    }
};

class SplitChecker : public PendingMessageTracker::Checker
{
public:
    bool found;
    uint8_t maxPri;

    SplitChecker(uint8_t maxP) : found(false), maxPri(maxP) {};

    bool check(uint32_t msgType, uint16_t node, uint8_t pri) override {
        (void) node;
        (void) pri;
        if (msgType == api::MessageType::SPLITBUCKET_ID && pri <= maxPri) {
            found = true;
            return false;
        }

        return true;
    }
};

}

void
Distributor::checkBucketForSplit(document::BucketSpace bucketSpace,
                                 const BucketDatabase::Entry& e,
                                 uint8_t priority)
{
    if (!getConfig().doInlineSplit()) {
       return;
    }

    // Verify that there are no existing pending splits at the
    // appropriate priority.
    SplitChecker checker(priority);
    for (uint32_t i = 0; i < e->getNodeCount(); ++i) {
        _pendingMessageTracker.checkPendingMessages(e->getNodeRef(i).getNode(),
                                                    document::Bucket(bucketSpace, e.getBucketId()),
                                                    checker);
        if (checker.found) {
            return;
        }
    }

    Operation::SP operation =
        _idealStateManager.generateInterceptingSplit(bucketSpace, e, priority);

    if (operation.get()) {
        _maintenanceOperationOwner.start(operation, priority);
    }
}

void
Distributor::enableNextDistribution()
{
    if (_nextDistribution.get()) {
        _distribution = _nextDistribution;
        propagateDefaultDistribution(_distribution);
        _nextDistribution = std::shared_ptr<lib::Distribution>();
        _bucketDBUpdater.storageDistributionChanged();
    }
}

void
Distributor::propagateDefaultDistribution(
        std::shared_ptr<const lib::Distribution> distribution)
{
    auto global_distr = GlobalBucketSpaceDistributionConverter::convert_to_global(*distribution);
    for (auto* repo : {_bucketSpaceRepo.get(), _readOnlyBucketSpaceRepo.get()}) {
        repo->get(document::FixedBucketSpaces::default_space()).setDistribution(distribution);
        repo->get(document::FixedBucketSpaces::global_space()).setDistribution(global_distr);
    }
}

void
Distributor::propagateClusterStates()
{
    for (auto* repo : {_bucketSpaceRepo.get(), _readOnlyBucketSpaceRepo.get()}) {
        for (auto& iter : *repo) {
            iter.second->setClusterState(_clusterStateBundle.getDerivedClusterState(iter.first));
        }
    }
}

void
Distributor::signalWorkWasDone()
{
    _tickResult = framework::ThreadWaitInfo::MORE_WORK_ENQUEUED;
}

bool
Distributor::workWasDone() const noexcept
{
    return !_tickResult.waitWanted();
}

namespace {

bool is_client_request(const api::StorageMessage& msg) noexcept {
    // Despite having been converted to StorageAPI messages, the following
    // set of messages are never sent to the distributor by other processes
    // than clients.
    switch (msg.getType().getId()) {
    case api::MessageType::GET_ID:
    case api::MessageType::PUT_ID:
    case api::MessageType::REMOVE_ID:
    case api::MessageType::VISITOR_CREATE_ID:
    case api::MessageType::VISITOR_DESTROY_ID:
    case api::MessageType::GETBUCKETLIST_ID:
    case api::MessageType::STATBUCKET_ID:
    case api::MessageType::UPDATE_ID:
    case api::MessageType::REMOVELOCATION_ID:
        return true;
    default:
        return false;
    }
}

}

void Distributor::handle_or_propagate_message(const std::shared_ptr<api::StorageMessage>& msg) {
    if (!handleMessage(msg)) {
        MBUS_TRACE(msg->getTrace(), 9, "Distributor: Not handling it. Sending further down.");
        sendDown(msg);
    }
}

void Distributor::startExternalOperations() {
    for (auto& msg : _fetchedMessages) {
        if (is_client_request(*msg)) {
            MBUS_TRACE(msg->getTrace(), 9, "Distributor: adding to client request priority queue");
            _client_request_priority_queue.emplace(std::move(msg));
        } else {
            MBUS_TRACE(msg->getTrace(), 9, "Distributor: Grabbed from queue to be processed.");
            handle_or_propagate_message(msg);
        }
    }

    const bool start_single_client_request = !_client_request_priority_queue.empty();
    if (start_single_client_request) {
        const auto& msg = _client_request_priority_queue.top();
        MBUS_TRACE(msg->getTrace(), 9, "Distributor: Grabbed from "
                   "client request priority queue to be processed.");
        handle_or_propagate_message(msg);
        _client_request_priority_queue.pop();
    }

    if (!_fetchedMessages.empty() || start_single_client_request) {
        signalWorkWasDone();
    }
    _fetchedMessages.clear();
}

std::unordered_map<uint16_t, uint32_t>
Distributor::getMinReplica() const
{
    std::lock_guard guard(_metricLock);
    return _bucketDbStats._minBucketReplica;
}

BucketSpacesStatsProvider::PerNodeBucketSpacesStats
Distributor::getBucketSpacesStats() const
{
    std::lock_guard guard(_metricLock);
    return _bucketSpacesStats;
}

void
Distributor::propagateInternalScanMetricsToExternal()
{
    std::lock_guard guard(_metricLock);

    // All shared values are written when _metricLock is held, so no races.
    if (_bucketDBMetricUpdater.hasCompletedRound()) {
        _bucketDbStats.propagateMetrics(_idealStateManager.getMetrics(),
                                        getMetrics());
        _idealStateManager.getMetrics().setPendingOperations(
                _maintenanceStats.global.pending);
    }
}

namespace {

BucketSpaceStats
toBucketSpaceStats(const NodeMaintenanceStats &stats)
{
    return BucketSpaceStats(stats.total, stats.syncing + stats.copyingIn);
}

using PerNodeBucketSpacesStats = BucketSpacesStatsProvider::PerNodeBucketSpacesStats;

PerNodeBucketSpacesStats
toBucketSpacesStats(const NodeMaintenanceStatsTracker &maintenanceStats)
{
    PerNodeBucketSpacesStats result;
    for (const auto &nodeEntry : maintenanceStats.perNodeStats()) {
        for (const auto &bucketSpaceEntry : nodeEntry.second) {
            auto bucketSpace = document::FixedBucketSpaces::to_string(bucketSpaceEntry.first);
            result[nodeEntry.first][bucketSpace] = toBucketSpaceStats(bucketSpaceEntry.second);
        }
    }
    return result;
}

size_t spaces_with_merges_pending(const PerNodeBucketSpacesStats& stats) {
    std::unordered_set<document::BucketSpace, document::BucketSpace::hash> spaces_with_pending;
    for (auto& node : stats) {
        for (auto& space : node.second) {
            if (space.second.valid() && space.second.bucketsPending() != 0) {
                // TODO avoid bucket space string roundtrip
                spaces_with_pending.emplace(document::FixedBucketSpaces::from_string(space.first));
            }
        }
    }
    return spaces_with_pending.size();
}

// TODO should we also trigger on !pending --> pending edge?
bool merge_no_longer_pending_edge(const PerNodeBucketSpacesStats& prev_stats,
                                  const PerNodeBucketSpacesStats& curr_stats) {
    const auto prev_pending = spaces_with_merges_pending(prev_stats);
    const auto curr_pending = spaces_with_merges_pending(curr_stats);
    return curr_pending < prev_pending;
}

}

void
Distributor::updateInternalMetricsForCompletedScan()
{
    std::lock_guard guard(_metricLock);

    _bucketDBMetricUpdater.completeRound();
    _bucketDbStats = _bucketDBMetricUpdater.getLastCompleteStats();
    _maintenanceStats = _scanner->getPendingMaintenanceStats();
    auto new_space_stats = toBucketSpacesStats(_maintenanceStats.perNodeStats);
    if (merge_no_longer_pending_edge(_bucketSpacesStats, new_space_stats)) {
        _must_send_updated_host_info = true;
    }
    _bucketSpacesStats = std::move(new_space_stats);
    maybe_update_bucket_db_memory_usage_stats();
}

void Distributor::maybe_update_bucket_db_memory_usage_stats() {
    auto now = _component.getClock().getMonotonicTime();
    if ((now - _last_db_memory_sample_time_point) > _db_memory_sample_interval) {
        for (auto& space : *_bucketSpaceRepo) {
            _bucketDBMetricUpdater.update_db_memory_usage(space.second->getBucketDatabase().memory_usage(), true);
        }
        for (auto& space : *_readOnlyBucketSpaceRepo) {
            _bucketDBMetricUpdater.update_db_memory_usage(space.second->getBucketDatabase().memory_usage(), false);
        }
        _last_db_memory_sample_time_point = now;
    } else {
        // Reuse previous memory statistics instead of sampling new.
        _bucketDBMetricUpdater.update_db_memory_usage(_bucketDbStats._mutable_db_mem_usage, true);
        _bucketDBMetricUpdater.update_db_memory_usage(_bucketDbStats._read_only_db_mem_usage, false);
    }
}

void
Distributor::scanAllBuckets()
{
    enterRecoveryMode();
    while (!scanNextBucket().isDone()) {}
}

MaintenanceScanner::ScanResult
Distributor::scanNextBucket()
{
    MaintenanceScanner::ScanResult scanResult(_scanner->scanNext());
    if (scanResult.isDone()) {
        updateInternalMetricsForCompletedScan();
        leaveRecoveryMode();
        send_updated_host_info_if_required();
        _scanner->reset();
    } else {
        const auto &distribution(_bucketSpaceRepo->get(scanResult.getBucketSpace()).getDistribution());
        _bucketDBMetricUpdater.visit(
                scanResult.getEntry(),
                distribution.getRedundancy());
    }
    return scanResult;
}

void Distributor::send_updated_host_info_if_required() {
    if (_must_send_updated_host_info) {
        _component.getStateUpdater().immediately_send_get_node_state_replies();
        _must_send_updated_host_info = false;
    }
}

void
Distributor::startNextMaintenanceOperation()
{
    _throttlingStarter->setMaxPendingRange(getConfig().getMinPendingMaintenanceOps(),
                                           getConfig().getMaxPendingMaintenanceOps());
    _scheduler->tick(_schedulingMode);
}

framework::ThreadWaitInfo
Distributor::doCriticalTick(framework::ThreadIndex)
{
    _tickResult = framework::ThreadWaitInfo::NO_MORE_CRITICAL_WORK_KNOWN;
    enableNextDistribution();
    enableNextConfig();
    fetchStatusRequests();
    fetchExternalMessages();
    return _tickResult;
}

framework::ThreadWaitInfo
Distributor::doNonCriticalTick(framework::ThreadIndex)
{
    _tickResult = framework::ThreadWaitInfo::NO_MORE_CRITICAL_WORK_KNOWN;
    handleStatusRequests();
    startExternalOperations();
    if (initializing()) {
        _bucketDBUpdater.resendDelayedMessages();
        return _tickResult;
    }
    // Ordering note: since maintenance inhibiting checks whether startExternalOperations()
    // did any useful work with incoming data, this check must be performed _after_ the call.
    if (!should_inhibit_current_maintenance_scan_tick()) {
        scanNextBucket();
        startNextMaintenanceOperation();
        if (isInRecoveryMode()) {
            signalWorkWasDone();
        }
        mark_maintenance_tick_as_no_longer_inhibited();
        _bucketDBUpdater.resendDelayedMessages();
    } else {
        mark_current_maintenance_tick_as_inhibited();
    }
    return _tickResult;
}

bool Distributor::should_inhibit_current_maintenance_scan_tick() const noexcept {
    return (workWasDone() && (_inhibited_maintenance_tick_count
                              < getConfig().max_consecutively_inhibited_maintenance_ticks()));
}

void Distributor::mark_current_maintenance_tick_as_inhibited() noexcept {
    ++_inhibited_maintenance_tick_count;
}

void Distributor::mark_maintenance_tick_as_no_longer_inhibited() noexcept {
    _inhibited_maintenance_tick_count = 0;
}

void
Distributor::enableNextConfig()
{
    _hostInfoReporter.enableReporting(getConfig().getEnableHostInfoReporting());
    _bucketDBMetricUpdater.setMinimumReplicaCountingMode(getConfig().getMinimumReplicaCountingMode());
    _ownershipSafeTimeCalc->setMaxClusterClockSkew(getConfig().getMaxClusterClockSkew());
    _pendingMessageTracker.setNodeBusyDuration(getConfig().getInhibitMergesOnBusyNodeDuration());
    _bucketDBUpdater.set_stale_reads_enabled(getConfig().allowStaleReadsDuringClusterStateTransitions());
    _externalOperationHandler.set_concurrent_gets_enabled(
            getConfig().allowStaleReadsDuringClusterStateTransitions());
    _externalOperationHandler.set_use_weak_internal_read_consistency_for_gets(
            getConfig().use_weak_internal_read_consistency_for_client_gets());
}

void
Distributor::fetchStatusRequests()
{
    if (_fetchedStatusRequests.empty()) {
        _fetchedStatusRequests.swap(_statusToDo);
    }
}

void
Distributor::fetchExternalMessages()
{
    assert(_fetchedMessages.empty());
    _fetchedMessages.swap(_messageQueue);
}

void
Distributor::handleStatusRequests()
{
    uint32_t sz = _fetchedStatusRequests.size();
    for (uint32_t i = 0; i < sz; ++i) {
        Status& s(*_fetchedStatusRequests[i]);
        s.getReporter().reportStatus(s.getStream(), s.getPath());
        s.notifyCompleted();
    }
    _fetchedStatusRequests.clear();
    if (sz > 0) {
        signalWorkWasDone();
    }
}

vespalib::string
Distributor::getReportContentType(const framework::HttpUrlPath& path) const
{
    if (path.hasAttribute("page")) {
        if (path.getAttribute("page") == "buckets") {
            return "text/html";
        } else {
            return "application/xml";
        }
    } else {
        return "text/html";
    }
}

std::string
Distributor::getActiveIdealStateOperations() const
{
    return _maintenanceOperationOwner.toString();
}

std::string
Distributor::getActiveOperations() const
{
    return _operationOwner.toString();
}

bool
Distributor::reportStatus(std::ostream& out,
                          const framework::HttpUrlPath& path) const
{
    if (!path.hasAttribute("page") || path.getAttribute("page") == "buckets") {
        framework::PartlyHtmlStatusReporter htmlReporter(*this);
        htmlReporter.reportHtmlHeader(out, path);
        if (!path.hasAttribute("page")) {
            out << "<a href=\"?page=pending\">Count of pending messages to "
                << "storage nodes</a><br><a href=\"?page=maintenance&show=50\">"
                << "List maintenance queue (adjust show parameter to see more "
                << "operations, -1 for all)</a><br>\n<a href=\"?page=buckets\">"
                << "List all buckets, highlight non-ideal state</a><br>\n";
        } else {
            const_cast<IdealStateManager&>(_idealStateManager)
                .getBucketStatus(out);
        }
        htmlReporter.reportHtmlFooter(out, path);
    } else {
        framework::PartlyXmlStatusReporter xmlReporter(*this, out, path);
        using namespace vespalib::xml;
        std::string page(path.getAttribute("page"));

        if (page == "pending") {
            xmlReporter << XmlTag("pending")
                        << XmlAttribute("externalload", _operationOwner.size())
                        << XmlAttribute("maintenance",
                                _maintenanceOperationOwner.size())
                        << XmlEndTag();
        } else if (page == "maintenance") {
            // Need new page
        }
    }

    return true;
}

bool
Distributor::handleStatusRequest(const DelegatedStatusRequest& request) const
{
    auto wrappedRequest = std::make_shared<Status>(request);
    {
        framework::TickingLockGuard guard(_threadPool.freezeCriticalTicks());
        _statusToDo.push_back(wrappedRequest);
        guard.broadcast();
    }
    wrappedRequest->waitForCompletion();
    return true;    
}

}
