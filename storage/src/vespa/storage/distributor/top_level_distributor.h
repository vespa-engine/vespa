// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bucket_spaces_stats_provider.h"
#include "cluster_state_bundle_activation_listener.h"
#include "top_level_bucket_db_updater.h"
#include "distributor_component.h"
#include "distributor_host_info_reporter.h"
#include "distributor_interface.h"
#include "distributor_stripe_interface.h"
#include "externaloperationhandler.h"
#include "ideal_state_total_metrics.h"
#include "idealstatemanager.h"
#include "min_replica_provider.h"
#include "pendingmessagetracker.h"
#include "statusreporterdelegate.h"
#include "stripe_bucket_db_updater.h" // TODO this is temporary
#include "stripe_host_info_notifier.h"
#include <vespa/storage/common/distributorcomponent.h>
#include <vespa/storage/common/doneinitializehandler.h>
#include <vespa/storage/common/messagesender.h>
#include <vespa/storage/common/node_identity.h>
#include <vespa/storage/common/storagelink.h>
#include <vespa/storage/distributor/bucketdb/bucketdbmetricupdater.h>
#include <vespa/storage/distributor/maintenance/maintenancescheduler.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/storageframework/generic/metric/metricupdatehook.h>
#include <vespa/storageframework/generic/thread/tickingthread.h>
#include <vespa/vdslib/state/random.h>
#include <chrono>
#include <queue>
#include <unordered_map>

namespace storage {
    struct DoneInitializeHandler;
    class HostInfo;
}

namespace storage::distributor {

class BlockingOperationStarter;
class BucketPriorityDatabase;
class TopLevelBucketDBUpdater;
class DistributorBucketSpaceRepo;
class DistributorStatus;
class DistributorStripe;
class DistributorStripePool;
class DistributorTotalMetrics;
class StripeAccessor;
class OperationSequencer;
class OwnershipTransferSafeTimePointCalculator;
class SimpleMaintenanceScanner;
class ThrottlingOperationStarter;

class TopLevelDistributor final
    : public StorageLink,
      public DistributorInterface,
      public StatusDelegator,
      public framework::StatusReporter,
      public framework::TickingThread,
      public MinReplicaProvider,
      public BucketSpacesStatsProvider,
      public StripeHostInfoNotifier,
      public ClusterStateBundleActivationListener
{
public:
    TopLevelDistributor(DistributorComponentRegister&,
                        const NodeIdentity& node_identity,
                        framework::TickingThreadPool&,
                        DistributorStripePool& stripe_pool,
                        DoneInitializeHandler&,
                        uint32_t num_distributor_stripes,
                        HostInfo& hostInfoReporterRegistrar,
                        ChainedMessageSender* = nullptr);

    ~TopLevelDistributor() override;

    void onOpen() override;
    void onClose() override;
    bool onDown(const std::shared_ptr<api::StorageMessage>&) override;
    void sendUp(const std::shared_ptr<api::StorageMessage>&) override;
    void sendDown(const std::shared_ptr<api::StorageMessage>&) override;

    void start_stripe_pool();

    DistributorMetricSet& getMetrics();

    const NodeIdentity& node_identity() const noexcept { return _node_identity; }

    [[nodiscard]] bool done_initializing() const noexcept { return _done_initializing; }

    // Implements DistributorInterface and DistributorMessageSender.
    DistributorMetricSet& metrics() override { return getMetrics(); }
    const DistributorConfiguration& config() const override;

    void sendCommand(const std::shared_ptr<api::StorageCommand>& cmd) override;
    void sendReply(const std::shared_ptr<api::StorageReply>& reply) override;
    int getDistributorIndex() const override { return _component.node_index(); }
    const ClusterContext& cluster_context() const override { return _component.cluster_context(); }

    void storageDistributionChanged() override;

    // StatusReporter implementation
    vespalib::string getReportContentType(const framework::HttpUrlPath&) const override;
    bool reportStatus(std::ostream&, const framework::HttpUrlPath&) const override;

    bool handleStatusRequest(const DelegatedStatusRequest& request) const override;

    framework::ThreadWaitInfo doCriticalTick(framework::ThreadIndex) override;
    framework::ThreadWaitInfo doNonCriticalTick(framework::ThreadIndex) override;

    // Called by DistributorStripe threads when they want to notify the cluster controller of changed stats.
    // Thread safe.
    void notify_stripe_wants_to_send_host_info(uint16_t stripe_index) override;

    class MetricUpdateHook : public framework::MetricUpdateHook
    {
    public:
        MetricUpdateHook(TopLevelDistributor& self)
            : _self(self)
        {
        }

        void updateMetrics(const MetricLockGuard &) override {
            _self.propagateInternalScanMetricsToExternal();
        }

    private:
        TopLevelDistributor& _self;
    };

private:
    friend class DistributorStripeTestUtil;
    friend class TopLevelDistributorTestUtil;
    friend class MetricUpdateHook;
    friend struct DistributorStripeTest;
    friend struct TopLevelDistributorTest;

    void setNodeStateUp();

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
    void enable_next_config_if_changed();
    void fetch_status_requests();
    void handle_status_requests();
    void signal_work_was_done();
    [[nodiscard]] bool work_was_done() const noexcept;
    void enable_next_distribution_if_changed();
    void propagate_default_distribution_thread_unsafe(std::shared_ptr<const lib::Distribution> distribution);
    void un_inhibit_maintenance_if_safe_time_passed();

    void dispatch_to_main_distributor_thread_queue(const std::shared_ptr<api::StorageMessage>& msg);
    void fetch_external_messages();
    void process_fetched_external_messages();
    void send_host_info_if_appropriate();
    // Precondition: _stripe_scan_notify_mutex is held
    [[nodiscard]] bool may_send_host_info_on_behalf_of_stripes(std::lock_guard<std::mutex>& held_lock) noexcept;

    uint32_t random_stripe_idx();
    uint32_t stripe_of_bucket_id(const document::BucketId& bucket_id, const api::StorageMessage& msg);

    // ClusterStateBundleActivationListener impl:
    void on_cluster_state_bundle_activated(const lib::ClusterStateBundle& new_bundle,
                                           bool has_bucket_ownership_transfer) override;

    struct StripeScanStats {
        bool wants_to_send_host_info = false;
        bool has_reported_in_at_least_once = false;
    };

    using MessageQueue = std::vector<std::shared_ptr<api::StorageMessage>>;

    const NodeIdentity                    _node_identity;
    DistributorComponentRegister&         _comp_reg;
    DoneInitializeHandler&                _done_init_handler;
    bool                                  _done_initializing;
    std::shared_ptr<DistributorTotalMetrics> _total_metrics;
    std::shared_ptr<IdealStateTotalMetrics> _ideal_state_total_metrics;
    ChainedMessageSender*                 _messageSender;
    uint8_t                               _n_stripe_bits;
    DistributorStripePool&                _stripe_pool;
    std::vector<std::unique_ptr<DistributorStripe>> _stripes;
    std::unique_ptr<StripeAccessor>      _stripe_accessor;
    storage::lib::RandomGen              _random_stripe_gen;
    std::mutex                           _random_stripe_gen_mutex;
    MessageQueue                         _message_queue; // Queue for top-level ops
    MessageQueue                         _fetched_messages;
    distributor::DistributorComponent    _component;
    storage::DistributorComponent        _ideal_state_component;
    std::shared_ptr<const DistributorConfiguration> _total_config;
    std::unique_ptr<TopLevelBucketDBUpdater>     _bucket_db_updater;
    StatusReporterDelegate               _distributorStatusDelegate;
    std::unique_ptr<StatusReporterDelegate> _bucket_db_status_delegate;
    framework::TickingThreadPool&        _threadPool;
    mutable std::vector<std::shared_ptr<DistributorStatus>> _status_to_do;
    mutable std::vector<std::shared_ptr<DistributorStatus>> _fetched_status_requests;
    mutable std::mutex                   _stripe_scan_notify_mutex;
    std::vector<StripeScanStats>         _stripe_scan_stats; // Indices are 1-1 with _stripes entries
    vespalib::steady_time                _last_host_info_send_time;
    vespalib::duration                   _host_info_send_delay;
    // Ideally this would use steady_clock, but for now let's use the same semantics as
    // feed blocking during safe time periods.
    vespalib::system_time                _maintenance_safe_time_point;
    std::chrono::seconds                 _maintenance_safe_time_delay;
    framework::ThreadWaitInfo            _tickResult;
    MetricUpdateHook                     _metricUpdateHook;
    DistributorHostInfoReporter          _hostInfoReporter;

    mutable std::mutex                   _distribution_mutex;
    std::shared_ptr<lib::Distribution>   _distribution;
    std::shared_ptr<lib::Distribution>   _next_distribution;

    uint64_t                             _current_internal_config_generation;
};

}
