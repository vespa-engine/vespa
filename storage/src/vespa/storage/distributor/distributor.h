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
#include <vespa/storage/common/distributorcomponent.h>
#include <vespa/storage/common/doneinitializehandler.h>
#include <vespa/storage/common/messagesender.h>
#include <vespa/storage/distributor/bucketdb/bucketdbmetricupdater.h>
#include <vespa/storage/distributor/maintenance/maintenancescheduler.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/storageframework/generic/metric/metricupdatehook.h>
#include <vespa/storageframework/generic/thread/tickingthread.h>
#include <vespa/vespalib/util/sync.h>
#include <queue>
#include <unordered_map>

namespace storage {

class DoneInitializeHandler;
class HostInfo;

namespace distributor {

class DistributorBucketSpaceRepo;
class SimpleMaintenanceScanner;
class BlockingOperationStarter;
class ThrottlingOperationStarter;
class BucketPriorityDatabase;
class OwnershipTransferSafeTimePointCalculator;

class Distributor : public StorageLink,
                    public DistributorInterface,
                    public StatusDelegator,
                    public framework::StatusReporter,
                    public framework::TickingThread,
                    public MinReplicaProvider,
                    public BucketSpacesStatsProvider
{
public:
    Distributor(DistributorComponentRegister&,
                framework::TickingThreadPool&,
                DoneInitializeHandler&,
                bool manageActiveBucketCopies,
                HostInfo& hostInfoReporterRegistrar,
                ChainedMessageSender* = nullptr);

    ~Distributor();

    void onOpen() override;
    void onClose() override;
    bool onDown(const std::shared_ptr<api::StorageMessage>&) override;
    void sendUp(const std::shared_ptr<api::StorageMessage>&) override;
    void sendDown(const std::shared_ptr<api::StorageMessage>&) override;

    ChainedMessageSender& getMessageSender() override {
        return (_messageSender == 0 ? *this : *_messageSender);
    }

    DistributorMetricSet& getMetrics() override { return *_metrics; }
    
    PendingMessageTracker& getPendingMessageTracker() override {
        return _pendingMessageTracker;
    }

    BucketOwnership checkOwnershipInPendingState(const document::Bucket &bucket) const override;

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

    uint32_t pendingMaintenanceCount() const;

    std::string getActiveIdealStateOperations() const;
    std::string getActiveOperations() const;

    virtual framework::ThreadWaitInfo doCriticalTick(framework::ThreadIndex) override;
    virtual framework::ThreadWaitInfo doNonCriticalTick(framework::ThreadIndex) override;

    /**
     * Checks whether a bucket needs to be split, and sends a split
     * if so.
     */
    void checkBucketForSplit(document::BucketSpace bucketSpace, const BucketDatabase::Entry& e, uint8_t priority) override;

    const lib::ClusterStateBundle& getClusterStateBundle() const override;

    /**
     * @return Returns the states in which the distributors consider
     * storage nodes to be up.
     */
    const char* getStorageNodeUpStates() const override {
        return _initializingIsUp ? "uri" : "ur";
    }

    /**
     * Called by bucket db updater after a merge has finished, and all the
     * request bucket info operations have been performed as well. Passes the
     * merge back to the operation that created it.
     */
    void handleCompletedMerge(const std::shared_ptr<api::MergeBucketReply>& reply) override;


    bool initializing() const override {
        return !_doneInitializing;
    }
    
    const DistributorConfiguration& getConfig() const override {
        return _component.getTotalDistributorConfig();
    }

    bool isInRecoveryMode() const {
        return _schedulingMode == MaintenanceScheduler::RECOVERY_SCHEDULING_MODE;
    }

    int getDistributorIndex() const override;
    const std::string& getClusterName() const override;
    const PendingMessageTracker& getPendingMessageTracker() const override;
    void sendCommand(const std::shared_ptr<api::StorageCommand>&) override;
    void sendReply(const std::shared_ptr<api::StorageReply>&) override;

    const BucketGcTimeCalculator::BucketIdHasher&
    getBucketIdHasher() const override {
        return *_bucketIdHasher;
    }

    DistributorBucketSpaceRepo &getBucketSpaceRepo() noexcept { return *_bucketSpaceRepo; }
    const DistributorBucketSpaceRepo &getBucketSpaceRepo() const noexcept { return *_bucketSpaceRepo; }

private:
    friend class Distributor_Test;
    friend class BucketDBUpdaterTest;
    friend class DistributorTestUtil;
    friend class ExternalOperationHandler_Test;
    friend class Operation_Test;
    friend class MetricUpdateHook;

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

    void setNodeStateUp();
    bool handleMessage(const std::shared_ptr<api::StorageMessage>& msg);
    bool isMaintenanceReply(const api::StorageReply& reply) const;

    void handleStatusRequests();
    void send_shutdown_abort_reply(const std::shared_ptr<api::StorageMessage>&);
    void handle_or_propagate_message(const std::shared_ptr<api::StorageMessage>& msg);
    void startExternalOperations();

    /**
     * Return a copy of the latest min replica data, see MinReplicaProvider.
     */
    std::unordered_map<uint16_t, uint32_t> getMinReplica() const override;

    PerNodeBucketSpacesStats getBucketSpacesStats() const override;

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
    void scanAllBuckets();
    MaintenanceScanner::ScanResult scanNextBucket();
    void enableNextConfig();
    void fetchStatusRequests();
    void fetchExternalMessages();
    void startNextMaintenanceOperation();
    void signalWorkWasDone();
    bool workWasDone();

    void enterRecoveryMode();
    void leaveRecoveryMode();

    // Tries to generate an operation from the given message. Returns true
    // if we either returned an operation, or the message was otherwise handled
    // (for instance, wrong distribution).
    bool generateOperation(const std::shared_ptr<api::StorageMessage>& msg,
                           Operation::SP& operation);

    void enableNextDistribution();
    void propagateDefaultDistribution(std::shared_ptr<const lib::Distribution>);
    void propagateClusterStates();

    lib::ClusterStateBundle _clusterStateBundle;

    DistributorComponentRegister& _compReg;
    storage::DistributorComponent _component;
    std::unique_ptr<DistributorBucketSpaceRepo> _bucketSpaceRepo;
    std::shared_ptr<DistributorMetricSet> _metrics;

    OperationOwner _operationOwner;
    OperationOwner _maintenanceOperationOwner;

    PendingMessageTracker _pendingMessageTracker;
    BucketDBUpdater _bucketDBUpdater;
    StatusReporterDelegate _distributorStatusDelegate;
    StatusReporterDelegate _bucketDBStatusDelegate;
    IdealStateManager _idealStateManager;
    ExternalOperationHandler _externalOperationHandler;

    std::shared_ptr<lib::Distribution> _distribution;
    std::shared_ptr<lib::Distribution> _nextDistribution;

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
    MessageQueue _messageQueue;
    ClientRequestPriorityQueue _client_request_priority_queue;
    MessageQueue _fetchedMessages;
    framework::TickingThreadPool& _threadPool;
    vespalib::Monitor _statusMonitor;

    class Status;
    mutable std::vector<std::shared_ptr<Status>> _statusToDo;
    mutable std::vector<std::shared_ptr<Status>> _fetchedStatusRequests;

    bool _initializingIsUp;

    DoneInitializeHandler& _doneInitializeHandler;
    bool _doneInitializing;

    ChainedMessageSender* _messageSender;

    std::unique_ptr<BucketPriorityDatabase> _bucketPriorityDb;
    std::unique_ptr<SimpleMaintenanceScanner> _scanner;
    std::unique_ptr<ThrottlingOperationStarter> _throttlingStarter;
    std::unique_ptr<BlockingOperationStarter> _blockingStarter;
    std::unique_ptr<MaintenanceScheduler> _scheduler;
    MaintenanceScheduler::SchedulingMode _schedulingMode;
    framework::MilliSecTimer _recoveryTimeStarted;
    framework::ThreadWaitInfo _tickResult;
    const std::string _clusterName;
    BucketDBMetricUpdater _bucketDBMetricUpdater;
    std::unique_ptr<BucketGcTimeCalculator::BucketIdHasher> _bucketIdHasher;
    MetricUpdateHook _metricUpdateHook;
    vespalib::Lock _metricLock;
    /**
     * Maintenance stats for last completed database scan iteration.
     * Access must be protected by _metricLock as it is read by metric
     * manager thread but written by distributor thread.
     */
    SimpleMaintenanceScanner::PendingMaintenanceStats _maintenanceStats;
    BucketSpacesStatsProvider::PerNodeBucketSpacesStats _bucketSpacesStats;
    BucketDBMetricUpdater::Stats _bucketDbStats;
    DistributorHostInfoReporter _hostInfoReporter;
    std::unique_ptr<OwnershipTransferSafeTimePointCalculator> _ownershipSafeTimeCalc;
};

} // distributor
} // storage
