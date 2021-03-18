// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bucket_spaces_stats_provider.h"
#include "bucketdbupdater.h"
#include "distributor_host_info_reporter.h"
#include "distributorinterface.h"
#include "externaloperationhandler.h"
#include "idealstatemanager.h"
#include "min_replica_provider.h"
#include "pendingmessagetracker.h"
#include "statusreporterdelegate.h"
#include <vespa/config/config.h>
#include <vespa/storage/common/doneinitializehandler.h>
#include <vespa/storage/common/messagesender.h>
#include <vespa/storage/distributor/bucketdb/bucketdbmetricupdater.h>
#include <vespa/storage/distributor/distributorcomponent.h>
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
class DistributorBucketSpaceRepo;
class DistributorStatus;
class DistributorStripe;
class OperationSequencer;
class OwnershipTransferSafeTimePointCalculator;
class SimpleMaintenanceScanner;
class ThrottlingOperationStarter;

class Distributor : public StorageLink,
                    public DistributorInterface,
                    public StatusDelegator,
                    public framework::StatusReporter,
                    public framework::TickingThread,
                    public MinReplicaProvider,
                    public BucketSpacesStatsProvider,
                    public NonTrackingMessageSender
{
public:
    Distributor(DistributorComponentRegister&,
                const NodeIdentity& node_identity,
                framework::TickingThreadPool&,
                DoneInitializeHandler&,
                bool manageActiveBucketCopies,
                HostInfo& hostInfoReporterRegistrar,
                ChainedMessageSender* = nullptr);

    ~Distributor() override;

    const ClusterContext& cluster_context() const override {
        return _component.cluster_context();
    }
    void onOpen() override;
    void onClose() override;
    bool onDown(const std::shared_ptr<api::StorageMessage>&) override;
    void sendUp(const std::shared_ptr<api::StorageMessage>&) override;
    void sendDown(const std::shared_ptr<api::StorageMessage>&) override;
    // Bypasses message tracker component. Thread safe.
    void send_up_without_tracking(const std::shared_ptr<api::StorageMessage>&) override;

    ChainedMessageSender& getMessageSender() override {
        abort(); // TODO STRIPE
    }

    DistributorMetricSet& getMetrics() override { return *_metrics; }

    const OperationSequencer& operation_sequencer() const noexcept override {
        abort(); // TODO STRIPE
    }

    const lib::ClusterState* pendingClusterStateOrNull(const document::BucketSpace&) const override;

    /**
     * Enables a new cluster state. Called after the bucket db updater has
     * retrieved all bucket info related to the change.
     */
    void enableClusterStateBundle(const lib::ClusterStateBundle& clusterStateBundle) override;

    /**
     * Invoked when a pending cluster state for a distribution (config)
     * change has been enabled. An invocation of storageDistributionChanged
     * will eventually cause this method to be called, assuming the pending
     * cluster state completed successfully.
     */
    void notifyDistributionChangeEnabled() override;

    void storageDistributionChanged() override;

    void recheckBucketInfo(uint16_t nodeIdx, const document::Bucket &bucket) override;

    bool handleReply(const std::shared_ptr<api::StorageReply>& reply) override;

    // StatusReporter implementation
    vespalib::string getReportContentType(const framework::HttpUrlPath&) const override;
    bool reportStatus(std::ostream&, const framework::HttpUrlPath&) const override;

    bool handleStatusRequest(const DelegatedStatusRequest& request) const override;

    std::string getActiveIdealStateOperations() const;

    virtual framework::ThreadWaitInfo doCriticalTick(framework::ThreadIndex) override;
    virtual framework::ThreadWaitInfo doNonCriticalTick(framework::ThreadIndex) override;

    /**
     * Checks whether a bucket needs to be split, and sends a split
     * if so.
     */
    void checkBucketForSplit(document::BucketSpace bucketSpace,
                             const BucketDatabase::Entry& e,
                             uint8_t priority) override;

    const lib::ClusterStateBundle& getClusterStateBundle() const override;

    /**
     * @return Returns the states in which the distributors consider
     * storage nodes to be up.
     */
    const char* getStorageNodeUpStates() const override {
        return "uri";
    }

    /**
     * Called by bucket db updater after a merge has finished, and all the
     * request bucket info operations have been performed as well. Passes the
     * merge back to the operation that created it.
     */
    void handleCompletedMerge(const std::shared_ptr<api::MergeBucketReply>& reply) override;

    bool initializing() const override;
    
    const DistributorConfiguration& getConfig() const override;

    bool isInRecoveryMode() const noexcept;

    int getDistributorIndex() const override;
    PendingMessageTracker& getPendingMessageTracker() override;
    const PendingMessageTracker& getPendingMessageTracker() const override;

    DistributorBucketSpaceRepo& getBucketSpaceRepo() noexcept;
    const DistributorBucketSpaceRepo& getBucketSpaceRepo() const noexcept;
    DistributorBucketSpaceRepo& getReadOnlyBucketSpaceRepo() noexcept;
    const DistributorBucketSpaceRepo& getReadyOnlyBucketSpaceRepo() const noexcept;

    storage::distributor::DistributorComponent& distributor_component() noexcept;

    void sendCommand(const std::shared_ptr<api::StorageCommand>&) override;
    void sendReply(const std::shared_ptr<api::StorageReply>&) override;

    const BucketGcTimeCalculator::BucketIdHasher&
    getBucketIdHasher() const override {
        abort(); // TODO STRIPE
    }

    OperationRoutingSnapshot read_snapshot_for_bucket(const document::Bucket&) const override;

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
    bool isMaintenanceReply(const api::StorageReply& reply) const;

    void handleStatusRequests();
    void send_shutdown_abort_reply(const std::shared_ptr<api::StorageMessage>&);
    void handle_or_propagate_message(const std::shared_ptr<api::StorageMessage>& msg);
    void startExternalOperations();

    // Accessors used by tests
    BucketDBUpdater& bucket_db_updater();
    const BucketDBUpdater& bucket_db_updater() const;
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
    void maybe_update_bucket_db_memory_usage_stats();
    void scanAllBuckets();
    void enableNextConfig();
    void signalWorkWasDone();
    bool workWasDone() const noexcept;

    void enableNextDistribution();
    void propagateDefaultDistribution(std::shared_ptr<const lib::Distribution>);
    void propagateClusterStates();

    std::shared_ptr<DistributorMetricSet> _metrics;
    ChainedMessageSender* _messageSender;
    // TODO STRIPE multiple stripes...! This is for proof of concept of wiring.
    std::unique_ptr<DistributorStripe> _stripe;

    std::unique_ptr<DistributorBucketSpaceRepo> _bucketSpaceRepo;
    // Read-only bucket space repo with DBs that only contain buckets transiently
    // during cluster state transitions. Bucket set does not overlap that of _bucketSpaceRepo
    // and the DBs are empty during non-transition phases.
    std::unique_ptr<DistributorBucketSpaceRepo> _readOnlyBucketSpaceRepo;
    storage::distributor::DistributorComponent _component;

    StatusReporterDelegate _distributorStatusDelegate;

    framework::TickingThreadPool& _threadPool;

    mutable std::vector<std::shared_ptr<DistributorStatus>> _statusToDo;
    mutable std::vector<std::shared_ptr<DistributorStatus>> _fetchedStatusRequests;

    framework::ThreadWaitInfo _tickResult;
    MetricUpdateHook _metricUpdateHook;
    mutable std::mutex _metricLock;
    DistributorHostInfoReporter _hostInfoReporter;
};

}
