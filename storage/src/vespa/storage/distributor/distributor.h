// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/config/config.h>
#include <vespa/storage/common/distributorcomponent.h>
#include <vespa/storage/common/doneinitializehandler.h>
#include <vespa/storage/common/messagesender.h>
#include <vespa/storage/distributor/idealstatemanager.h>
#include <vespa/storage/distributor/bucketdbupdater.h>
#include <vespa/storage/distributor/pendingmessagetracker.h>
#include <vespa/storage/distributor/externaloperationhandler.h>
#include <vespa/storage/distributor/bucketdb/bucketdbmetricupdater.h>
#include <vespa/storage/distributor/bucketdbupdater.h>
#include <vespa/storage/distributor/maintenancebucket.h>
#include <vespa/storage/distributor/min_replica_provider.h>
#include <vespa/storage/distributor/distributorinterface.h>
#include <vespa/storage/distributor/maintenance/maintenancescheduler.h>
#include <vespa/storage/distributor/statusreporterdelegate.h>
#include <vespa/storage/distributor/distributor_host_info_reporter.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/storageframework/generic/thread/tickingthread.h>
#include <vespa/vespalib/util/sync.h>

#include <unordered_map>

namespace storage {

class DoneInitializeHandler;
class HostInfo;

namespace distributor {

class ManagedBucketSpaceRepo;
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
                    public MinReplicaProvider
{
public:
    Distributor(DistributorComponentRegister&,
                framework::TickingThreadPool&,
                DoneInitializeHandler&,
                bool manageActiveBucketCopies,
                HostInfo& hostInfoReporterRegistrar,
                ChainedMessageSender* = nullptr);

    ~Distributor();

    void onOpen();

    void onClose();

    bool onDown(const std::shared_ptr<api::StorageMessage>&);

    void sendUp(const std::shared_ptr<api::StorageMessage>&);

    void sendDown(const std::shared_ptr<api::StorageMessage>&);

    virtual ChainedMessageSender& getMessageSender() {
        return (_messageSender == 0 ? *this : *_messageSender);
    }

    DistributorMetricSet& getMetrics() { return *_metrics; }
    
    PendingMessageTracker& getPendingMessageTracker() {
        return _pendingMessageTracker;
    }

    BucketOwnership checkOwnershipInPendingState(const document::BucketId&) const override;

    /**
     * Enables a new cluster state. Called after the bucket db updater has
     * retrieved all bucket info related to the change.
     */
    void enableClusterState(const lib::ClusterState& clusterState);

    /**
     * Invoked when a pending cluster state for a distribution (config)
     * change has been enabled. An invocation of storageDistributionChanged
     * will eventually cause this method to be called, assuming the pending
     * cluster state completed successfully.
     */
    void notifyDistributionChangeEnabled();

    void storageDistributionChanged();

    void recheckBucketInfo(uint16_t nodeIdx, const document::BucketId& bid);

    bool handleReply(const std::shared_ptr<api::StorageReply>& reply);

    // StatusReporter implementation
    vespalib::string getReportContentType(
            const framework::HttpUrlPath&) const;
    bool reportStatus(std::ostream&, const framework::HttpUrlPath&) const;

    bool handleStatusRequest(const DelegatedStatusRequest& request) const;

    uint32_t pendingMaintenanceCount() const;

    std::string getActiveIdealStateOperations() const;
    std::string getActiveOperations() const;

    virtual framework::ThreadWaitInfo doCriticalTick(framework::ThreadIndex);
    virtual framework::ThreadWaitInfo doNonCriticalTick(framework::ThreadIndex);

    /**
     * Checks whether a bucket needs to be split, and sends a split
     * if so.
     */
    void checkBucketForSplit(const BucketDatabase::Entry& e,
                             uint8_t priority);

    const lib::Distribution& getDistribution() const;

    const lib::ClusterState& getClusterState() const {
        return _clusterState;
    }

    /**
     * @return Returns the states in which the distributors consider
     * storage nodes to be up.
     */
    const char* getStorageNodeUpStates() const
    { return _initializingIsUp ? "uri" : "ur"; }

    /**
     * Called by bucket db updater after a merge has finished, and all the
     * request bucket info operations have been performed as well. Passes the
     * merge back to the operation that created it.
     */
    void handleCompletedMerge(const std::shared_ptr<api::MergeBucketReply>& reply);


    bool initializing() const {
        return !_doneInitializing;
    }

    BucketDatabase& getBucketDatabase();
    const BucketDatabase& getBucketDatabase() const;
    
    const DistributorConfiguration& getConfig() const {
        return _component.getTotalDistributorConfig();
    }

    bool isInRecoveryMode() const {
        return _schedulingMode == MaintenanceScheduler::RECOVERY_SCHEDULING_MODE;
    }

    int getDistributorIndex() const;

    const std::string& getClusterName() const;

    const PendingMessageTracker& getPendingMessageTracker() const;

    virtual void sendCommand(const std::shared_ptr<api::StorageCommand>&);
    virtual void sendReply(const std::shared_ptr<api::StorageReply>&);

    const BucketGcTimeCalculator::BucketIdHasher&
    getBucketIdHasher() const override {
        return *_bucketIdHasher;
    }

    ManagedBucketSpace& getDefaultBucketSpace() noexcept;
    const ManagedBucketSpace& getDefaultBucketSpace() const noexcept;

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
    void startExternalOperations();

    /**
     * Return a copy of the latest min replica data, see MinReplicaProvider.
     */
    std::unordered_map<uint16_t, uint32_t> getMinReplica() const override;

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
    void propagateDefaultDistribution(std::shared_ptr<lib::Distribution>);

    lib::ClusterState _clusterState;

    DistributorComponentRegister& _compReg;
    storage::DistributorComponent _component;
    std::unique_ptr<ManagedBucketSpaceRepo> _bucketSpaceRepo;
    std::shared_ptr<DistributorMetricSet> _metrics;

    OperationOwner _operationOwner;
    OperationOwner _maintenanceOperationOwner;

    PendingMessageTracker _pendingMessageTracker;
    BucketDBUpdater _bucketDBUpdater;
    StatusReporterDelegate _distributorStatusDelegate;
    StatusReporterDelegate _bucketDBStatusDelegate;
    IdealStateManager _idealStateManager;
    ExternalOperationHandler _externalOperationHandler;

    mutable std::shared_ptr<lib::Distribution> _distribution;
    std::shared_ptr<lib::Distribution> _nextDistribution;

    typedef std::vector<std::shared_ptr<api::StorageMessage> > MessageQueue;
    MessageQueue _messageQueue;
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
    BucketDBMetricUpdater::Stats _bucketDbStats;
    DistributorHostInfoReporter _hostInfoReporter;
    std::unique_ptr<OwnershipTransferSafeTimePointCalculator> _ownershipSafeTimeCalc;
};

} // distributor
} // storage
