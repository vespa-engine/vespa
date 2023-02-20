// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bucket_spaces_stats_provider.h"
#include "distributor_host_info_reporter.h"
#include "distributor_stripe_interface.h"
#include "externaloperationhandler.h"
#include "idealstatemanager.h"
#include "min_replica_provider.h"
#include "pendingmessagetracker.h"
#include "statusreporterdelegate.h"
#include "stripe_access_guard.h"
#include "stripe_bucket_db_updater.h"
#include "tickable_stripe.h"
#include <vespa/storage/common/doneinitializehandler.h>
#include <vespa/storage/common/messagesender.h>
#include <vespa/storage/distributor/bucketdb/bucketdbmetricupdater.h>
#include <vespa/storage/distributor/distributor_stripe_component.h>
#include <vespa/storage/distributor/maintenance/maintenancescheduler.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/storageframework/generic/metric/metricupdatehook.h>
#include <vespa/storageframework/generic/thread/tickingthread.h>
#include <vespa/storageframework/generic/clock/timer.h>

#include <atomic>
#include <mutex>
#include <queue>
#include <unordered_map>

namespace storage {
    struct DoneInitializeHandler;
    class HostInfo;
    class NodeIdentity;
}

namespace storage::distributor {

class BlockingOperationStarter;
class BucketPriorityDatabase;
class DistributorStatus;
class DistributorBucketSpaceRepo;
class OperationSequencer;
class OwnershipTransferSafeTimePointCalculator;
class SimpleMaintenanceScanner;
class StripeHostInfoNotifier;
class ThrottlingOperationStarter;

/**
 * A DistributorStripe encapsulates client operation handling and maintenance of a subset of the
 * bucket space that the full distributor has responsibility for.
 *
 * Each distributor stripe is responsible for a completely disjoint subset of the bucket space of all
 * other distributor stripes in the process (and transitively, in the entire cluster).
 */
class DistributorStripe final
    : public DistributorStripeInterface,
      public MinReplicaProvider,
      public BucketSpacesStatsProvider,
      public NonTrackingMessageSender,
      public TickableStripe
{
public:
    DistributorStripe(DistributorComponentRegister&,
                      DistributorMetricSet& metrics,
                      IdealStateMetricSet& ideal_state_metrics,
                      const NodeIdentity& node_identity,
                      ChainedMessageSender& messageSender,
                      StripeHostInfoNotifier& stripe_host_info_notifier,
                      const bool& done_initializing_ref,
                      uint32_t stripe_index = 0);

    ~DistributorStripe() override;

    const ClusterContext& cluster_context() const override {
        return _component.cluster_context();
    }
    void flush_and_close() override;
    bool handle_or_enqueue_message(const std::shared_ptr<api::StorageMessage>&);
    void send_up_with_tracking(const std::shared_ptr<api::StorageMessage>&);
    // Bypasses message tracker component. Thread safe.
    void send_up_without_tracking(const std::shared_ptr<api::StorageMessage>&) override;

    ChainedMessageSender& getMessageSender() override {
        return _messageSender;
    }

    DistributorMetricSet& getMetrics() override { return _metrics; }

    PendingMessageTracker& getPendingMessageTracker() override {
        return _pendingMessageTracker;
    }

    const OperationSequencer& operation_sequencer() const noexcept override {
        return *_operation_sequencer;
    }

    OperationSequencer& operation_sequencer() noexcept override {
        return *_operation_sequencer;
    }

    const lib::ClusterState* pendingClusterStateOrNull(const document::BucketSpace&) const override;

    /**
     * Enables a new cluster state. Called after the bucket db updater has
     * retrieved all bucket info related to the change.
     */
    void enableClusterStateBundle(const lib::ClusterStateBundle& clusterStateBundle) override;

    /**
     * Invoked when a pending cluster state for a distribution (config)
     * change has been enabled. An invocation of storage_distribution_changed
     * will eventually cause this method to be called, assuming the pending
     * cluster state completed successfully.
     */
    void notifyDistributionChangeEnabled() override;

    void recheckBucketInfo(uint16_t nodeIdx, const document::Bucket &bucket) override;

    bool handleReply(const std::shared_ptr<api::StorageReply>& reply) override;

    StripeAccessGuard::PendingOperationStats pending_operation_stats() const override;

    std::string getActiveIdealStateOperations() const;
    std::string getActiveOperations() const;

    framework::ThreadWaitInfo doNonCriticalTick(framework::ThreadIndex);

    /**
     * Checks whether a bucket needs to be split, and sends a split
     * if so.
     */
    void checkBucketForSplit(document::BucketSpace bucketSpace,
                             const BucketDatabase::Entry& e,
                             uint8_t priority) override;

    const lib::ClusterStateBundle& getClusterStateBundle() const override;

    /**
     * Called by bucket db updater after a merge has finished, and all the
     * request bucket info operations have been performed as well. Passes the
     * merge back to the operation that created it.
     */
    void handleCompletedMerge(const std::shared_ptr<api::MergeBucketReply>& reply) override;

    bool initializing() const override {
        return !_done_initializing_ref;
    }

    const DistributorConfiguration& getConfig() const override {
        return *_total_config;
    }

    bool isInRecoveryMode() const noexcept {
        return _schedulingMode == MaintenanceScheduler::RECOVERY_SCHEDULING_MODE;
    }

    int getDistributorIndex() const override;
    const PendingMessageTracker& getPendingMessageTracker() const override;
    void sendCommand(const std::shared_ptr<api::StorageCommand>&) override;
    void sendReply(const std::shared_ptr<api::StorageReply>&) override;

    const BucketGcTimeCalculator::BucketIdHasher&
    getBucketIdHasher() const override {
        return *_bucketIdHasher;
    }

    const NodeSupportedFeaturesRepo& node_supported_features_repo() const noexcept override;

    StripeBucketDBUpdater& bucket_db_updater() { return _bucketDBUpdater; }
    const StripeBucketDBUpdater& bucket_db_updater() const { return _bucketDBUpdater; }
    IdealStateManager& ideal_state_manager() { return _idealStateManager; }
    const IdealStateManager& ideal_state_manager() const { return _idealStateManager; }
    ExternalOperationHandler& external_operation_handler() { return _externalOperationHandler; }
    const ExternalOperationHandler& external_operation_handler() const { return _externalOperationHandler; }

    DistributorBucketSpaceRepo &getBucketSpaceRepo() noexcept { return *_bucketSpaceRepo; }
    const DistributorBucketSpaceRepo &getBucketSpaceRepo() const noexcept { return *_bucketSpaceRepo; }

    DistributorBucketSpaceRepo& getReadOnlyBucketSpaceRepo() noexcept {
        return *_readOnlyBucketSpaceRepo;
    }
    const DistributorBucketSpaceRepo& getReadyOnlyBucketSpaceRepo() const noexcept {
        return *_readOnlyBucketSpaceRepo;
    }

    OperationRoutingSnapshot read_snapshot_for_bucket(const document::Bucket&) const override;

    std::chrono::steady_clock::duration db_memory_sample_interval() const noexcept {
        return _db_memory_sample_interval;
    }

    void inhibit_non_activation_maintenance_operations(bool inhibit) noexcept {
        _non_activation_maintenance_is_inhibited.store(inhibit, std::memory_order_relaxed);
    }

    bool non_activation_maintenance_is_inhibited() const noexcept {
        return _non_activation_maintenance_is_inhibited.load(std::memory_order_relaxed);
    }

    bool tick() override;

private:
    friend class TopLevelDistributor;
    friend class DistributorStripeTestUtil;
    friend class MetricUpdateHook;
    friend class MultiThreadedStripeAccessGuard;
    friend struct DistributorStripeTest;
    friend struct TopLevelDistributorTest;
    friend class TopLevelDistributorTestUtil;

    bool handleMessage(const std::shared_ptr<api::StorageMessage>& msg);
    static bool isMaintenanceReply(const api::StorageReply& reply);

    void send_shutdown_abort_reply(const std::shared_ptr<api::StorageMessage>&);
    void handle_or_propagate_message(const std::shared_ptr<api::StorageMessage>& msg);
    void startExternalOperations();

    /**
     * Return a copy of the latest min replica data, see MinReplicaProvider.
     */
    std::unordered_map<uint16_t, uint32_t> getMinReplica() const override;

    PerNodeBucketSpacesStats getBucketSpacesStats() const override;

    SimpleMaintenanceScanner::PendingMaintenanceStats pending_maintenance_stats() const;

    /**
     * Atomically publish internal metrics to external ideal state metrics.
     * Takes metric lock.
     */
    void propagateInternalScanMetricsToExternal();
    /**
     * Atomically updates internal metrics (not externally visible metrics;
     * these are not changed until a snapshot triggers
     * propagateIdealStateMetrics()).
     *
     * Takes metric lock.
     */
    void updateInternalMetricsForCompletedScan();
    void maybe_update_bucket_db_memory_usage_stats();
    void scanAllBuckets();
    MaintenanceScanner::ScanResult scanNextBucket();
    bool should_inhibit_current_maintenance_scan_tick() const noexcept;
    void mark_current_maintenance_tick_as_inhibited() noexcept;
    void mark_maintenance_tick_as_no_longer_inhibited() noexcept;
    void fetchExternalMessages();
    void startNextMaintenanceOperation();
    void signalWorkWasDone();
    bool workWasDone() const noexcept;

    void enterRecoveryMode();
    void leaveRecoveryMode();

    // Tries to generate an operation from the given message. Returns true
    // if we either returned an operation, or the message was otherwise handled
    // (for instance, wrong distribution).
    bool generateOperation(const std::shared_ptr<api::StorageMessage>& msg,
                           Operation::SP& operation);

    void propagateDefaultDistribution(std::shared_ptr<const lib::Distribution>); // TODO STRIPE remove once legacy is gone
    void propagateClusterStates();

    BucketSpacesStatsProvider::BucketSpacesStats make_invalid_stats_per_configured_space() const;
    template <typename NodeFunctor>
    void for_each_available_content_node_in(const lib::ClusterState&, NodeFunctor&&);
    void invalidate_internal_db_dependent_stats();
    void invalidate_bucket_spaces_stats(std::lock_guard<std::mutex>& held_metric_lock);
    void invalidate_min_replica_stats(std::lock_guard<std::mutex>& held_metric_lock);
    void send_updated_host_info_if_required();
    void propagate_config_snapshot_to_internal_components();

    // Additional implementations of TickableStripe:
    void update_distribution_config(const BucketSpaceDistributionConfigs& new_configs) override;
    void update_total_distributor_config(std::shared_ptr<const DistributorConfiguration> config) override;
    void set_pending_cluster_state_bundle(const lib::ClusterStateBundle& pending_state) override;
    void clear_pending_cluster_state_bundle() override;
    void enable_cluster_state_bundle(const lib::ClusterStateBundle& new_state,
                                     bool has_bucket_ownership_change) override;
    void notify_distribution_change_enabled() override;
    PotentialDataLossReport remove_superfluous_buckets(document::BucketSpace bucket_space,
                                                       const lib::ClusterState& new_state,
                                                       bool is_distribution_change) override;
    void merge_entries_into_db(document::BucketSpace bucket_space,
                               api::Timestamp gathered_at_timestamp,
                               const lib::Distribution& distribution,
                               const lib::ClusterState& new_state,
                               const char* storage_up_states,
                               const std::unordered_set<uint16_t>& outdated_nodes,
                               const std::vector<dbtransition::Entry>& entries) override;
    void update_read_snapshot_before_db_pruning() override;
    void update_read_snapshot_after_db_pruning(const lib::ClusterStateBundle& new_state) override;
    void update_read_snapshot_after_activation(const lib::ClusterStateBundle& activated_state) override;
    void clear_read_only_bucket_repo_databases() override;
    void update_node_supported_features_repo(std::shared_ptr<const NodeSupportedFeaturesRepo> features_repo) override;
    void report_bucket_db_status(document::BucketSpace bucket_space, std::ostream& out) const override;
    void report_single_bucket_requests(vespalib::xml::XmlOutputStream& xos) const override;
    void report_delayed_single_bucket_requests(vespalib::xml::XmlOutputStream& xos) const override;

    lib::ClusterStateBundle _clusterStateBundle;
    std::unique_ptr<DistributorBucketSpaceRepo> _bucketSpaceRepo;
    // Read-only bucket space repo with DBs that only contain buckets transiently
    // during cluster state transitions. Bucket set does not overlap that of _bucketSpaceRepo
    // and the DBs are empty during non-transition phases.
    std::unique_ptr<DistributorBucketSpaceRepo> _readOnlyBucketSpaceRepo;
    storage::distributor::DistributorStripeComponent _component;
    std::shared_ptr<const DistributorConfiguration> _total_config;
    DistributorMetricSet& _metrics;

    OperationOwner _operationOwner;
    OperationOwner _maintenanceOperationOwner;

    std::unique_ptr<OperationSequencer> _operation_sequencer;
    PendingMessageTracker _pendingMessageTracker;
    StripeBucketDBUpdater _bucketDBUpdater;
    IdealStateManager _idealStateManager;
    ChainedMessageSender& _messageSender;
    StripeHostInfoNotifier& _stripe_host_info_notifier;
    ExternalOperationHandler _externalOperationHandler;

    std::shared_ptr<lib::Distribution> _distribution;

    using MessageQueue = std::vector<std::shared_ptr<api::StorageMessage>>;
    struct IndirectHigherPriority {
        template <typename Lhs, typename Rhs>
        bool operator()(const Lhs& lhs, const Rhs& rhs) const noexcept {
            return lhs->getPriority() > rhs->getPriority();
        }
    };
    using ClientRequestPriorityQueue = std::priority_queue<
            std::shared_ptr<api::StorageMessage>,
            std::vector<std::shared_ptr<api::StorageMessage>>,
            IndirectHigherPriority
    >;
    mutable std::mutex _external_message_mutex;
    MessageQueue _messageQueue;
    ClientRequestPriorityQueue _client_request_priority_queue;
    MessageQueue _fetchedMessages;
    const bool& _done_initializing_ref;

    std::unique_ptr<BucketPriorityDatabase> _bucketPriorityDb;
    std::unique_ptr<SimpleMaintenanceScanner> _scanner;
    std::unique_ptr<ThrottlingOperationStarter> _throttlingStarter;
    std::unique_ptr<BlockingOperationStarter> _blockingStarter;
    std::unique_ptr<MaintenanceScheduler> _scheduler;
    MaintenanceScheduler::SchedulingMode _schedulingMode;
    framework::MilliSecTimer _recoveryTimeStarted;
    framework::ThreadWaitInfo _tickResult;
    BucketDBMetricUpdater _bucketDBMetricUpdater;
    std::unique_ptr<BucketGcTimeCalculator::BucketIdHasher> _bucketIdHasher;
    std::shared_ptr<const NodeSupportedFeaturesRepo> _node_supported_features_repo;
    mutable std::mutex _metricLock;
    /**
     * Maintenance stats for last completed database scan iteration.
     * Access must be protected by _metricLock as it is read by metric
     * manager thread but written by distributor thread.
     */
    SimpleMaintenanceScanner::PendingMaintenanceStats _maintenanceStats;
    BucketSpacesStatsProvider::PerNodeBucketSpacesStats _bucketSpacesStats;
    BucketDBMetricUpdater::Stats _bucketDbStats;
    std::unique_ptr<OwnershipTransferSafeTimePointCalculator> _ownershipSafeTimeCalc;
    std::chrono::steady_clock::duration _db_memory_sample_interval;
    std::chrono::steady_clock::time_point _last_db_memory_sample_time_point;
    size_t _inhibited_maintenance_tick_count;
    uint32_t _stripe_index;
    std::atomic<bool> _non_activation_maintenance_is_inhibited;
    bool _must_send_updated_host_info;
};

}
