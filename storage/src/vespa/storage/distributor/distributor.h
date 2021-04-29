// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bucket_spaces_stats_provider.h"
#include "bucketdbupdater.h"
#include "distributor_component.h"
#include "distributor_host_info_reporter.h"
#include "distributor_interface.h"
#include "distributor_stripe_interface.h"
#include "externaloperationhandler.h"
#include "idealstatemanager.h"
#include "min_replica_provider.h"
#include "pendingmessagetracker.h"
#include "statusreporterdelegate.h"
#include "stripe_bucket_db_updater.h" // TODO this is temporary
#include <vespa/config/config.h>
#include <vespa/storage/common/distributorcomponent.h>
#include <vespa/storage/common/doneinitializehandler.h>
#include <vespa/storage/common/messagesender.h>
#include <vespa/storage/distributor/bucketdb/bucketdbmetricupdater.h>
#include <vespa/storage/distributor/maintenance/maintenancescheduler.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/storageframework/generic/metric/metricupdatehook.h>
#include <vespa/storageframework/generic/thread/tickingthread.h>
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
class BucketDBUpdater;
class DistributorBucketSpaceRepo;
class DistributorStatus;
class DistributorStripe;
class OperationSequencer;
class LegacySingleStripeAccessor;
class OwnershipTransferSafeTimePointCalculator;
class SimpleMaintenanceScanner;
class ThrottlingOperationStarter;

class Distributor final
    : public StorageLink,
      public DistributorInterface,
      public StatusDelegator,
      public framework::StatusReporter,
      public framework::TickingThread,
      public MinReplicaProvider,
      public BucketSpacesStatsProvider
{
public:
    Distributor(DistributorComponentRegister&,
                const NodeIdentity& node_identity,
                framework::TickingThreadPool&,
                DoneInitializeHandler&,
                uint32_t num_distributor_stripes,
                HostInfo& hostInfoReporterRegistrar,
                ChainedMessageSender* = nullptr);

    ~Distributor() override;

    const ClusterContext& cluster_context() const {
        return _component.cluster_context();
    }
    void onOpen() override;
    void onClose() override;
    bool onDown(const std::shared_ptr<api::StorageMessage>&) override;
    void sendUp(const std::shared_ptr<api::StorageMessage>&) override;
    void sendDown(const std::shared_ptr<api::StorageMessage>&) override;

    DistributorMetricSet& getMetrics() { return *_metrics; }

    // Implements DistributorInterface,
    DistributorMetricSet& metrics() override { return getMetrics(); }
    const DistributorConfiguration& config() const override;

    /**
     * Enables a new cluster state. Called after the bucket db updater has
     * retrieved all bucket info related to the change.
     */
    void enableClusterStateBundle(const lib::ClusterStateBundle& clusterStateBundle);

    void storageDistributionChanged() override;

    bool handleReply(const std::shared_ptr<api::StorageReply>& reply);

    // StatusReporter implementation
    vespalib::string getReportContentType(const framework::HttpUrlPath&) const override;
    bool reportStatus(std::ostream&, const framework::HttpUrlPath&) const override;

    bool handleStatusRequest(const DelegatedStatusRequest& request) const override;

    std::string getActiveIdealStateOperations() const;

    virtual framework::ThreadWaitInfo doCriticalTick(framework::ThreadIndex) override;
    virtual framework::ThreadWaitInfo doNonCriticalTick(framework::ThreadIndex) override;

    const lib::ClusterStateBundle& getClusterStateBundle() const;
    const DistributorConfiguration& getConfig() const;

    bool isInRecoveryMode() const noexcept;

    PendingMessageTracker& getPendingMessageTracker();
    const PendingMessageTracker& getPendingMessageTracker() const;

    DistributorBucketSpaceRepo& getBucketSpaceRepo() noexcept;
    const DistributorBucketSpaceRepo& getBucketSpaceRepo() const noexcept;
    DistributorBucketSpaceRepo& getReadOnlyBucketSpaceRepo() noexcept;
    const DistributorBucketSpaceRepo& getReadyOnlyBucketSpaceRepo() const noexcept;

    storage::distributor::DistributorStripeComponent& distributor_component() noexcept;

    class MetricUpdateHook : public framework::MetricUpdateHook
    {
    public:
        MetricUpdateHook(Distributor& self)
            : _self(self)
        {
        }

        void updateMetrics(const MetricLockGuard &) override {
            _self.propagateInternalScanMetricsToExternal();
        }

    private:
        Distributor& _self;
    };

    std::chrono::steady_clock::duration db_memory_sample_interval() const noexcept;

private:
    friend struct DistributorTest;
    friend class BucketDBUpdaterTest;
    friend class DistributorTestUtil;
    friend class MetricUpdateHook;

    void setNodeStateUp();
    bool handleMessage(const std::shared_ptr<api::StorageMessage>& msg);

    // Accessors used by tests
    StripeBucketDBUpdater& bucket_db_updater();
    const StripeBucketDBUpdater& bucket_db_updater() const;
    IdealStateManager& ideal_state_manager();
    const IdealStateManager& ideal_state_manager() const;
    ExternalOperationHandler& external_operation_handler();
    const ExternalOperationHandler& external_operation_handler() const;

    BucketDBMetricUpdater& bucket_db_metric_updater() const noexcept;

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
    void scanAllBuckets();
    void enableNextConfig();
    void enableNextDistribution();
    void propagateDefaultDistribution(std::shared_ptr<const lib::Distribution>);

    DistributorComponentRegister&         _comp_reg;
    std::shared_ptr<DistributorMetricSet> _metrics;
    ChainedMessageSender*                 _messageSender;
    // TODO STRIPE multiple stripes...! This is for proof of concept of wiring.
    std::unique_ptr<DistributorStripe>   _stripe;
    std::unique_ptr<LegacySingleStripeAccessor> _stripe_accessor;
    distributor::DistributorComponent    _component;
    std::shared_ptr<const DistributorConfiguration> _total_config;
    std::unique_ptr<BucketDBUpdater>     _bucket_db_updater;
    StatusReporterDelegate               _distributorStatusDelegate;
    framework::TickingThreadPool&        _threadPool;
    framework::ThreadWaitInfo            _tickResult;
    MetricUpdateHook                     _metricUpdateHook;
    DistributorHostInfoReporter          _hostInfoReporter;

    std::shared_ptr<lib::Distribution>   _distribution;
    std::shared_ptr<lib::Distribution>   _next_distribution;

    uint64_t                             _current_internal_config_generation;
};

}
