// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "blockingoperationstarter.h"
#include "distributor_bucket_space.h"
#include "distributor_stripe.h"
#include "distributormetricsset.h"
#include "idealstatemetricsset.h"
#include "node_supported_features_repo.h"
#include "operation_sequencer.h"
#include "ownership_transfer_safe_time_point_calculator.h"
#include "storage_node_up_states.h"
#include "stripe_host_info_notifier.h"
#include "throttlingoperationstarter.h"
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/storage/common/global_bucket_space_distribution_converter.h>
#include <vespa/storage/common/node_identity.h>
#include <vespa/storage/common/nodestateupdater.h>
#include <vespa/storage/distributor/maintenance/simplebucketprioritydatabase.h>
#include <vespa/storageframework/generic/status/xmlstatusreporter.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vespalib/util/memoryusage.h>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP(".distributor_stripe");

using namespace std::chrono_literals;

namespace storage::distributor {

DistributorStripe::DistributorStripe(DistributorComponentRegister& compReg,
                                     DistributorMetricSet& metrics,
                                     IdealStateMetricSet& ideal_state_metrics,
                                     const NodeIdentity& node_identity,
                                     ChainedMessageSender& messageSender,
                                     StripeHostInfoNotifier& stripe_host_info_notifier,
                                     const bool& done_initializing_ref,
                                     uint32_t stripe_index)
    : DistributorStripeInterface(),
      _clusterStateBundle(lib::ClusterState()),
      _bucketSpaceRepo(std::make_unique<DistributorBucketSpaceRepo>(node_identity.node_index())),
      _readOnlyBucketSpaceRepo(std::make_unique<DistributorBucketSpaceRepo>(node_identity.node_index())),
      _component(*this, *_bucketSpaceRepo, *_readOnlyBucketSpaceRepo, compReg, "distributor"),
      _total_config(_component.total_distributor_config_sp()),
      _metrics(metrics),
      _operationOwner(*this, _component.getClock()),
      _maintenanceOperationOwner(*this, _component.getClock()),
      _operation_sequencer(std::make_unique<OperationSequencer>()),
      _pendingMessageTracker(compReg, stripe_index),
      _bucketDBUpdater(_component, _component, *this, *this),
      _idealStateManager(_component, _component, ideal_state_metrics),
      _messageSender(messageSender),
      _stripe_host_info_notifier(stripe_host_info_notifier),
      _externalOperationHandler(_component, _component, getMetrics(), getMessageSender(),
                                *_operation_sequencer, *this, _component,
                                _idealStateManager, _operationOwner),
      _external_message_mutex(),
      _done_initializing_ref(done_initializing_ref),
      _bucketPriorityDb(std::make_unique<SimpleBucketPriorityDatabase>()),
      _scanner(std::make_unique<SimpleMaintenanceScanner>(*_bucketPriorityDb, _idealStateManager, *_bucketSpaceRepo)),
      _throttlingStarter(std::make_unique<ThrottlingOperationStarter>(_maintenanceOperationOwner)),
      _blockingStarter(std::make_unique<BlockingOperationStarter>(_component, *_operation_sequencer,
                                                                  *_throttlingStarter)),
      _scheduler(std::make_unique<MaintenanceScheduler>(_idealStateManager, *_bucketPriorityDb,
                                                        *_throttlingStarter, *_blockingStarter)),
      _schedulingMode(MaintenanceScheduler::NORMAL_SCHEDULING_MODE),
      _recoveryTimeStarted(_component.getClock()),
      _tickResult(framework::ThreadWaitInfo::NO_MORE_CRITICAL_WORK_KNOWN),
      _bucketIdHasher(std::make_unique<BucketGcTimeCalculator::BucketIdIdentityHasher>()),
      _node_supported_features_repo(std::make_shared<const NodeSupportedFeaturesRepo>()),
      _metricLock(),
      _maintenanceStats(),
      _bucketSpacesStats(),
      _bucketDbStats(),
      _ownershipSafeTimeCalc(std::make_unique<OwnershipTransferSafeTimePointCalculator>(0s)), // Set by config later
      _db_memory_sample_interval(30s),
      _last_db_memory_sample_time_point(),
      _inhibited_maintenance_tick_count(0),
      _stripe_index(stripe_index),
      _non_activation_maintenance_is_inhibited(false),
      _must_send_updated_host_info(false)
{
    propagateDefaultDistribution(_component.getDistribution());
    propagateClusterStates();
}

DistributorStripe::~DistributorStripe() = default;

int
DistributorStripe::getDistributorIndex() const
{
    return _component.getIndex();
}

const PendingMessageTracker&
DistributorStripe::getPendingMessageTracker() const
{
    return _pendingMessageTracker;
}

const lib::ClusterState*
DistributorStripe::pendingClusterStateOrNull(const document::BucketSpace& space) const {
    return _bucketDBUpdater.pendingClusterStateOrNull(space);
}

void
DistributorStripe::sendCommand(const std::shared_ptr<api::StorageCommand>& cmd)
{
    if (cmd->getType() == api::MessageType::MERGEBUCKET) {
        auto & merge(static_cast<api::MergeBucketCommand&>(*cmd));
        _idealStateManager.getMetrics().nodesPerMerge.addValue(merge.getNodes().size());
    }
    send_up_with_tracking(cmd);
}

void
DistributorStripe::sendReply(const std::shared_ptr<api::StorageReply>& reply)
{
    send_up_with_tracking(reply);
}

void DistributorStripe::send_shutdown_abort_reply(const std::shared_ptr<api::StorageMessage>& msg) {
    api::StorageReply::UP reply(
            std::dynamic_pointer_cast<api::StorageCommand>(msg)->makeReply());
    reply->setResult(api::ReturnCode(api::ReturnCode::ABORTED, "Distributor is shutting down"));
    send_up_with_tracking(std::shared_ptr<api::StorageMessage>(reply.release()));
}

void DistributorStripe::flush_and_close() {
    // This function is called from a different thread than that of the stripe
    // itself, so we need to take the same mutex to form a memory visibility pair.
    // It is important that no flushing ever sends any _requests_, as these
    // will most likely synchronously be bounced by the already shut down RPC
    // layer, causing a deadlock when the response call chain arrives back here.
    std::lock_guard lock(_external_message_mutex);
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

    LOG(debug, "DistributorStripe::onClose invoked");
    _pendingMessageTracker.abort_deferred_tasks();
    _bucketDBUpdater.flush();
    _externalOperationHandler.close_pending();
    _operationOwner.onClose();
    _maintenanceOperationOwner.onClose();
}

void DistributorStripe::send_up_without_tracking(const std::shared_ptr<api::StorageMessage>& msg) {
    _messageSender.sendUp(msg);
}

void
DistributorStripe::send_up_with_tracking(const std::shared_ptr<api::StorageMessage>& msg)
{
    _pendingMessageTracker.insert(msg);
    send_up_without_tracking(msg);
}

bool
DistributorStripe::handle_or_enqueue_message(const std::shared_ptr<api::StorageMessage>& msg)
{
    if (_externalOperationHandler.try_handle_message_outside_main_thread(msg)) {
        return true;
    }
    MBUS_TRACE(msg->getTrace(), 9, vespalib::make_string("DistributorStripe[%u]: Added to message queue.", _stripe_index));
    {
        std::lock_guard lock(_external_message_mutex);
        _messageQueue.push_back(msg);
        // Caller has the responsibility to wake up correct stripe
    }
    return true;
}

void
DistributorStripe::handleCompletedMerge(
        const std::shared_ptr<api::MergeBucketReply>& reply)
{
    _maintenanceOperationOwner.handleReply(reply);
}

bool
DistributorStripe::isMaintenanceReply(const api::StorageReply& reply)
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
DistributorStripe::handleReply(const std::shared_ptr<api::StorageReply>& reply)
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
DistributorStripe::generateOperation(
        const std::shared_ptr<api::StorageMessage>& msg,
        Operation::SP& operation)
{
    return _externalOperationHandler.handleMessage(msg, operation);
}

bool
DistributorStripe::handleMessage(const std::shared_ptr<api::StorageMessage>& msg)
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
DistributorStripe::getClusterStateBundle() const
{
    return _clusterStateBundle;
}

void
DistributorStripe::enableClusterStateBundle(const lib::ClusterStateBundle& state)
{
    lib::Node my_node(lib::NodeType::DISTRIBUTOR, getDistributorIndex());
    lib::ClusterStateBundle oldState = _clusterStateBundle;
    _clusterStateBundle = state;
    propagateClusterStates();

    const auto& baseline_state = *state.getBaselineClusterState();
    enterRecoveryMode();

    // Clear all active messages on nodes that are down.
    const uint16_t old_node_count = oldState.getBaselineClusterState()->getNodeCount(lib::NodeType::STORAGE);
    const uint16_t new_node_count = baseline_state.getNodeCount(lib::NodeType::STORAGE);
    for (uint16_t i = 0; i < std::max(old_node_count, new_node_count); ++i) {
        const auto& node_state = baseline_state.getNodeState(lib::Node(lib::NodeType::STORAGE, i)).getState();
        if (!node_state.oneOf(storage_node_up_states())) {
            std::vector<uint64_t> msgIds = _pendingMessageTracker.clearMessagesForNode(i);
            LOG(debug, "Node %u is down, clearing %zu pending maintenance operations", i, msgIds.size());

            for (const auto & msgId : msgIds) {
                _maintenanceOperationOwner.erase(msgId);
            }
        }
    }
}

OperationRoutingSnapshot DistributorStripe::read_snapshot_for_bucket(const document::Bucket& bucket) const {
    return _bucketDBUpdater.read_snapshot_for_bucket(bucket);
}

void
DistributorStripe::notifyDistributionChangeEnabled()
{
    LOG(debug, "Pending cluster state for distribution change has been enabled");
    // Trigger a re-scan of bucket database, just like we do when a new cluster
    // state has been enabled.
    enterRecoveryMode();
}

void
DistributorStripe::enterRecoveryMode()
{
    LOG(debug, "Entering recovery mode");
    _schedulingMode = MaintenanceScheduler::RECOVERY_SCHEDULING_MODE;
    _scanner->reset();
    // We enter recovery mode due to cluster state or distribution config changes.
    // Until we have completed a new DB scan round, we don't know the state of our
    // newly owned buckets and must not report stats for these out to the cluster
    // controller as they will be stale (valid only for the _previous_ state/config).
    // As a consequence, we must explicitly invalidate all such statistics in this edge.
    invalidate_internal_db_dependent_stats();

    _recoveryTimeStarted = framework::MilliSecTimer(_component.getClock());
}

void
DistributorStripe::leaveRecoveryMode()
{
    if (isInRecoveryMode()) {
        LOG(debug, "Leaving recovery mode");
        // FIXME don't use shared metric for this
        _metrics.recoveryModeTime.addValue(_recoveryTimeStarted.getElapsedTimeAsDouble());
        if (_done_initializing_ref) {
            _must_send_updated_host_info = true;
        }
    }
    _schedulingMode = MaintenanceScheduler::NORMAL_SCHEDULING_MODE;
}

void
DistributorStripe::invalidate_internal_db_dependent_stats()
{
    _bucketDBMetricUpdater.reset();
    {
        std::lock_guard guard(_metricLock);
        invalidate_bucket_spaces_stats(guard);
        invalidate_min_replica_stats(guard);
    }
}

template <typename NodeFunctor>
void DistributorStripe::for_each_available_content_node_in(const lib::ClusterState& state, NodeFunctor&& func) {
    const auto node_count = state.getNodeCount(lib::NodeType::STORAGE);
    for (uint16_t i = 0; i < node_count; ++i) {
        lib::Node node(lib::NodeType::STORAGE, i);
        if (state.getNodeState(node).getState().oneOf("uir")) {
            func(node);
        }
    }
}

BucketSpacesStatsProvider::BucketSpacesStats DistributorStripe::make_invalid_stats_per_configured_space() const {
    BucketSpacesStatsProvider::BucketSpacesStats invalid_space_stats;
    for (auto& space : *_bucketSpaceRepo) {
        invalid_space_stats.emplace(document::FixedBucketSpaces::to_string(space.first),
                                    BucketSpaceStats::make_invalid());
    }
    return invalid_space_stats;
}

void
DistributorStripe::invalidate_bucket_spaces_stats([[maybe_unused]] std::lock_guard<std::mutex>& held_metric_lock)
{
    _bucketSpacesStats = BucketSpacesStatsProvider::PerNodeBucketSpacesStats();
    auto invalid_space_stats = make_invalid_stats_per_configured_space();

    const auto& baseline = *_clusterStateBundle.getBaselineClusterState();
    for_each_available_content_node_in(baseline, [this, &invalid_space_stats](const lib::Node& node) {
        _bucketSpacesStats[node.getIndex()] = invalid_space_stats;
    });
}

void
DistributorStripe::invalidate_min_replica_stats([[maybe_unused]] std::lock_guard<std::mutex>& held_metric_lock)
{
    _bucketDbStats._minBucketReplica.clear();
    // Insert an explicit zero value for all nodes that are up in the pending/current cluster state
    const auto& baseline = *_clusterStateBundle.getBaselineClusterState();
    for_each_available_content_node_in(baseline, [this](const lib::Node& node) {
        _bucketDbStats._minBucketReplica[node.getIndex()] = 0;
    });
}

void
DistributorStripe::recheckBucketInfo(uint16_t nodeIdx, const document::Bucket &bucket) {
    _bucketDBUpdater.recheckBucketInfo(nodeIdx, bucket);
}

namespace {

class SplitChecker : public PendingMessageTracker::Checker
{
public:
    bool found;
    uint8_t maxPri;

    explicit SplitChecker(uint8_t maxP) : found(false), maxPri(maxP) {};

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
DistributorStripe::checkBucketForSplit(document::BucketSpace bucketSpace,
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

// TODO STRIPE must be invoked by top-level bucket db updater probably
void
DistributorStripe::propagateDefaultDistribution(
        std::shared_ptr<const lib::Distribution> distribution)
{
    auto global_distr = GlobalBucketSpaceDistributionConverter::convert_to_global(*distribution);
    for (auto* repo : {_bucketSpaceRepo.get(), _readOnlyBucketSpaceRepo.get()}) {
        repo->get(document::FixedBucketSpaces::default_space()).setDistribution(distribution);
        repo->get(document::FixedBucketSpaces::global_space()).setDistribution(global_distr);
    }
}

// Only called when stripe is in rendezvous freeze
void
DistributorStripe::update_distribution_config(const BucketSpaceDistributionConfigs& new_configs) {
    auto default_distr = new_configs.get_or_nullptr(document::FixedBucketSpaces::default_space());
    auto global_distr  = new_configs.get_or_nullptr(document::FixedBucketSpaces::global_space());
    assert(default_distr && global_distr);

    for (auto* repo : {_bucketSpaceRepo.get(), _readOnlyBucketSpaceRepo.get()}) {
        repo->get(document::FixedBucketSpaces::default_space()).setDistribution(default_distr);
        repo->get(document::FixedBucketSpaces::global_space()).setDistribution(global_distr);
    }
}

void
DistributorStripe::propagateClusterStates()
{
    for (auto* repo : {_bucketSpaceRepo.get(), _readOnlyBucketSpaceRepo.get()}) {
        repo->enable_cluster_state_bundle(_clusterStateBundle);
    }
}

void
DistributorStripe::signalWorkWasDone()
{
    _tickResult = framework::ThreadWaitInfo::MORE_WORK_ENQUEUED;
}

bool
DistributorStripe::workWasDone() const noexcept
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

void DistributorStripe::handle_or_propagate_message(const std::shared_ptr<api::StorageMessage>& msg) {
    if (!handleMessage(msg)) {
        MBUS_TRACE(msg->getTrace(), 9,
                   vespalib::make_string("DistributorStripe[%u]: Not handling it. Sending further down", _stripe_index));
        _messageSender.sendDown(msg);
    }
}

void DistributorStripe::startExternalOperations() {
    for (auto& msg : _fetchedMessages) {
        if (is_client_request(*msg)) {
            MBUS_TRACE(msg->getTrace(), 9,
                       vespalib::make_string("DistributorStripe[%u]: Adding to client request priority queue", _stripe_index));
            _client_request_priority_queue.emplace(std::move(msg));
        } else {
            MBUS_TRACE(msg->getTrace(), 9,
                       vespalib::make_string("DistributorStripe[%u]: Grabbed from queue to be processed", _stripe_index));
            handle_or_propagate_message(msg);
        }
    }

    const bool start_single_client_request = !_client_request_priority_queue.empty();
    if (start_single_client_request) {
        const auto& msg = _client_request_priority_queue.top();
        MBUS_TRACE(msg->getTrace(), 9,
                   vespalib::make_string("DistributorStripe[%u]: Grabbed from "
                   "client request priority queue to be processed", _stripe_index));
        handle_or_propagate_message(msg);
        _client_request_priority_queue.pop();
    }

    if (!_fetchedMessages.empty() || start_single_client_request) {
        signalWorkWasDone();
    }
    _fetchedMessages.clear();
}

std::unordered_map<uint16_t, uint32_t>
DistributorStripe::getMinReplica() const
{
    std::lock_guard guard(_metricLock);
    return _bucketDbStats._minBucketReplica;
}

BucketSpacesStatsProvider::PerNodeBucketSpacesStats
DistributorStripe::getBucketSpacesStats() const
{
    std::lock_guard guard(_metricLock);
    return _bucketSpacesStats;
}

SimpleMaintenanceScanner::PendingMaintenanceStats
DistributorStripe::pending_maintenance_stats() const {
    std::lock_guard guard(_metricLock);
    return _maintenanceStats;
}

void
DistributorStripe::propagateInternalScanMetricsToExternal()
{
    std::lock_guard guard(_metricLock);

    // All shared values are written when _metricLock is held, so no races.
    if (_bucketDBMetricUpdater.hasCompletedRound()) {
        auto& ideal_state_metrics = _idealStateManager.getMetrics();
        _bucketDbStats.propagateMetrics(ideal_state_metrics, getMetrics());
        ideal_state_metrics.setPendingOperations(_maintenanceStats.global.pending);
        const auto& total_stats = _maintenanceStats.perNodeStats.total_replica_stats();
        ideal_state_metrics.buckets_replicas_moving_out.set(total_stats.movingOut);
        ideal_state_metrics.buckets_replicas_copying_out.set(total_stats.copyingOut);
        ideal_state_metrics.buckets_replicas_copying_in.set(total_stats.copyingIn);
        ideal_state_metrics.buckets_replicas_syncing.set(total_stats.syncing);
        ideal_state_metrics.max_observed_time_since_last_gc_sec.set(
                _maintenanceStats.perNodeStats.max_observed_time_since_last_gc().count());
    }
}

namespace {

BucketSpaceStats
toBucketSpaceStats(const NodeMaintenanceStats &stats)
{
    return {stats.total, stats.syncing + stats.copyingIn};
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
DistributorStripe::updateInternalMetricsForCompletedScan()
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

void DistributorStripe::maybe_update_bucket_db_memory_usage_stats() {
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
DistributorStripe::scanAllBuckets()
{
    enterRecoveryMode();
    while (!scanNextBucket().isDone()) {}
}

MaintenanceScanner::ScanResult
DistributorStripe::scanNextBucket()
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

void DistributorStripe::send_updated_host_info_if_required() {
    if (_must_send_updated_host_info) {
        _stripe_host_info_notifier.notify_stripe_wants_to_send_host_info(_stripe_index);
        _must_send_updated_host_info = false;
    }
}

void
DistributorStripe::startNextMaintenanceOperation()
{
    _throttlingStarter->setMaxPendingRange(getConfig().getMinPendingMaintenanceOps(),
                                           getConfig().getMaxPendingMaintenanceOps());
    auto effective_scheduling_mode = ((_schedulingMode == MaintenanceScheduler::RECOVERY_SCHEDULING_MODE) ||
                                      non_activation_maintenance_is_inhibited())
                                              ? MaintenanceScheduler::RECOVERY_SCHEDULING_MODE
                                              : MaintenanceScheduler::NORMAL_SCHEDULING_MODE;
    _scheduler->tick(effective_scheduling_mode);
}

framework::ThreadWaitInfo
DistributorStripe::doNonCriticalTick(framework::ThreadIndex)
{
    _tickResult = framework::ThreadWaitInfo::NO_MORE_CRITICAL_WORK_KNOWN;
    {
        std::lock_guard lock(_external_message_mutex);
        fetchExternalMessages();
    }
    startExternalOperations();
    if (initializing()) {
        _bucketDBUpdater.resendDelayedMessages();
        return _tickResult;
    }
    // Ordering note: since maintenance inhibiting checks whether startExternalOperations()
    // did any useful work with incoming data, this check must be performed _after_ the call.
    if (!should_inhibit_current_maintenance_scan_tick()) {
        scanNextBucket();
        if (!_bucketDBUpdater.hasPendingClusterState()) {
            startNextMaintenanceOperation();
        }
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

bool DistributorStripe::tick() {
    auto wait_info = doNonCriticalTick(framework::ThreadIndex(0));
    return !wait_info.waitWanted(); // If we don't want to wait, we presumably did some useful stuff.
}

bool DistributorStripe::should_inhibit_current_maintenance_scan_tick() const noexcept {
    return (workWasDone() && (_inhibited_maintenance_tick_count
                              < getConfig().max_consecutively_inhibited_maintenance_ticks()));
}

void DistributorStripe::mark_current_maintenance_tick_as_inhibited() noexcept {
    ++_inhibited_maintenance_tick_count;
}

void DistributorStripe::mark_maintenance_tick_as_no_longer_inhibited() noexcept {
    _inhibited_maintenance_tick_count = 0;
}

namespace {

bool config_change_has_gc_enable_edge(const DistributorConfiguration& old_config,
                                      const DistributorConfiguration& new_config) noexcept {
    return ((old_config.getGarbageCollectionInterval().count() == 0) &&
            (new_config.getGarbageCollectionInterval().count() != 0));
}

}

void
DistributorStripe::update_total_distributor_config(std::shared_ptr<const DistributorConfiguration> config)
{
    auto old_config = std::move(_total_config);
    _total_config = std::move(config);
    propagate_config_snapshot_to_internal_components();
    if (config_change_has_gc_enable_edge(*old_config, *_total_config)) {
        LOG(debug, "GC has been enabled at reconfig edge; resetting last GC for all buckets to current time");
        _bucketDBUpdater.reset_all_last_gc_timestamps_to_current_time();
    }
}

void
DistributorStripe::propagate_config_snapshot_to_internal_components()
{
    _bucketDBMetricUpdater.setMinimumReplicaCountingMode(getConfig().getMinimumReplicaCountingMode());
    _ownershipSafeTimeCalc->setMaxClusterClockSkew(getConfig().getMaxClusterClockSkew());
    _pendingMessageTracker.setNodeBusyDuration(getConfig().getInhibitMergesOnBusyNodeDuration());
    _bucketDBUpdater.set_stale_reads_enabled(getConfig().allowStaleReadsDuringClusterStateTransitions());
    _externalOperationHandler.set_concurrent_gets_enabled(
            getConfig().allowStaleReadsDuringClusterStateTransitions());
    _externalOperationHandler.set_use_weak_internal_read_consistency_for_gets(
            getConfig().use_weak_internal_read_consistency_for_client_gets());
    _scheduler->set_implicitly_clear_priority_on_schedule(getConfig().implicitly_clear_priority_on_schedule());
}

void
DistributorStripe::fetchExternalMessages()
{
    assert(_fetchedMessages.empty());
    _fetchedMessages.swap(_messageQueue);
}

std::string
DistributorStripe::getActiveIdealStateOperations() const
{
    return _maintenanceOperationOwner.toString();
}

std::string
DistributorStripe::getActiveOperations() const
{
    return _operationOwner.toString();
}

StripeAccessGuard::PendingOperationStats
DistributorStripe::pending_operation_stats() const
{
    return {_operationOwner.size(), _maintenanceOperationOwner.size()};
}

void
DistributorStripe::set_pending_cluster_state_bundle(const lib::ClusterStateBundle& pending_state)
{
    getBucketSpaceRepo().set_pending_cluster_state_bundle(pending_state);
}

void
DistributorStripe::clear_pending_cluster_state_bundle()
{
    getBucketSpaceRepo().clear_pending_cluster_state_bundle();
}

void
DistributorStripe::enable_cluster_state_bundle(const lib::ClusterStateBundle& new_state,
                                               bool has_bucket_ownership_change)
{
    // TODO STRIPE replace legacy func
    enableClusterStateBundle(new_state);
    if (has_bucket_ownership_change) {
        vespalib::system_time now = _component.getClock().getSystemTime();
        _externalOperationHandler.rejectFeedBeforeTimeReached(_ownershipSafeTimeCalc->safeTimePoint(now));
    }
    _bucketDBUpdater.handle_activated_cluster_state_bundle(); // Triggers resending of queued requests
}

void
DistributorStripe::notify_distribution_change_enabled()
{
    // TODO STRIPE replace legacy func
    notifyDistributionChangeEnabled();
}

PotentialDataLossReport
DistributorStripe::remove_superfluous_buckets(document::BucketSpace bucket_space,
                                              const lib::ClusterState& new_state,
                                              bool is_distribution_change)
{
    return bucket_db_updater().remove_superfluous_buckets(bucket_space, new_state, is_distribution_change);
}

void
DistributorStripe::merge_entries_into_db(document::BucketSpace bucket_space,
                                         api::Timestamp gathered_at_timestamp,
                                         const lib::Distribution& distribution,
                                         const lib::ClusterState& new_state,
                                         const char* storage_up_states,
                                         const std::unordered_set<uint16_t>& outdated_nodes,
                                         const std::vector<dbtransition::Entry>& entries)
{
    bucket_db_updater().merge_entries_into_db(bucket_space, gathered_at_timestamp, distribution,
                                               new_state, storage_up_states, outdated_nodes, entries);
}

void
DistributorStripe::update_read_snapshot_before_db_pruning()
{
    bucket_db_updater().update_read_snapshot_before_db_pruning();
}

void
DistributorStripe::update_read_snapshot_after_db_pruning(const lib::ClusterStateBundle& new_state)
{
    bucket_db_updater().update_read_snapshot_after_db_pruning(new_state);
}

void
DistributorStripe::update_read_snapshot_after_activation(const lib::ClusterStateBundle& activated_state)
{
    bucket_db_updater().update_read_snapshot_after_activation(activated_state);
}

void
DistributorStripe::clear_read_only_bucket_repo_databases()
{
    bucket_db_updater().clearReadOnlyBucketRepoDatabases();
}

void
DistributorStripe::update_node_supported_features_repo(std::shared_ptr<const NodeSupportedFeaturesRepo> features_repo)
{
    _node_supported_features_repo = std::move(features_repo);
}

void
DistributorStripe::report_bucket_db_status(document::BucketSpace bucket_space, std::ostream& out) const
{
    ideal_state_manager().dump_bucket_space_db_status(bucket_space, out);
}

void
DistributorStripe::report_single_bucket_requests(vespalib::xml::XmlOutputStream& xos) const
{
    bucket_db_updater().report_single_bucket_requests(xos);
}

void
DistributorStripe::report_delayed_single_bucket_requests(vespalib::xml::XmlOutputStream& xos) const
{
    bucket_db_updater().report_delayed_single_bucket_requests(xos);
}

const NodeSupportedFeaturesRepo&
DistributorStripe::node_supported_features_repo() const noexcept
{
    return *_node_supported_features_repo;
}

}
