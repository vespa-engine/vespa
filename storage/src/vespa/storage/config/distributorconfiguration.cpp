// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "distributorconfiguration.h"
#include <vespa/storage/common/storagecomponent.h>
#include <vespa/storage/config/config-stor-distributormanager.h>
#include <vespa/storage/config/config-stor-visitordispatcher.h>
#include <vespa/document/select/parser.h>
#include <vespa/document/select/traversingvisitor.h>
#include <vespa/persistence/spi/bucket_limits.h>
#include <vespa/vespalib/util/exceptions.h>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".distributorconfiguration");

using namespace std::chrono;

namespace storage {

DistributorConfiguration::DistributorConfiguration(StorageComponent& component)
    : _component(component),
      _byteCountSplitLimit(0xffffffff),
      _docCountSplitLimit(0xffffffff),
      _byteCountJoinLimit(0),
      _docCountJoinLimit(0),
      _minimalBucketSplit(16),
      _maxNodesPerMerge(16),
      _max_consecutively_inhibited_maintenance_ticks(20),
      _max_activation_inhibited_out_of_sync_groups(0),
      _lastGarbageCollectionChange(vespalib::duration::zero()),
      _garbageCollectionInterval(0),
      _minPendingMaintenanceOps(100),
      _maxPendingMaintenanceOps(1000),
      _maxVisitorsPerNodePerClientVisitor(4),
      _minBucketsPerVisitor(5),
      _maxClusterClockSkew(0),
      _inhibitMergeSendingOnBusyNodeDuration(60s),
      _simulated_db_pruning_latency(0),
      _simulated_db_merging_latency(0),
      _doInlineSplit(true),
      _enableJoinForSiblingLessBuckets(false),
      _enableInconsistentJoin(false),
      _disableBucketActivation(false),
      _allowStaleReadsDuringClusterStateTransitions(false),
      _update_fast_path_restart_enabled(true),
      _merge_operations_disabled(false),
      _use_weak_internal_read_consistency_for_client_gets(false),
      _enable_metadata_only_fetch_phase_for_inconsistent_updates(true),
      _enable_operation_cancellation(false),
      _symmetric_put_and_activate_replica_selection(false),
      _minimumReplicaCountingMode(ReplicaCountingMode::TRUSTED)
{
}

DistributorConfiguration::~DistributorConfiguration() = default;

namespace {

class TimeVisitor : public document::select::TraversingVisitor {
public:
    bool hasCurrentTime;

    TimeVisitor() : hasCurrentTime(false) {}

    void visitCurrentTimeValueNode(const document::select::CurrentTimeValueNode&) override {
        hasCurrentTime = true;
    }
};

}

bool
DistributorConfiguration::containsTimeStatement(const std::string& documentSelection) const
{
    TimeVisitor visitor;
    try {
        document::select::Parser parser(*_component.getTypeRepo()->documentTypeRepo, _component.getBucketIdFactory());

        std::unique_ptr<document::select::Node> node = parser.parse(documentSelection);
        node->visit(visitor);
    } catch (std::exception& e) {
        LOG(error,
            "Caught exception during config-time processing of GC "
            "selection '%s', terminating process to force full "
            "reconfiguration: %s",
            documentSelection.c_str(),
            e.what());
        std::terminate();
    }
    return visitor.hasCurrentTime;
}

namespace {

ReplicaCountingMode
deriveReplicaCountingMode(DistributorConfiguration::DistributorManagerConfig::MinimumReplicaCountingMode mode) {
    switch (mode) {
        case vespa::config::content::core::internal::InternalStorDistributormanagerType::MinimumReplicaCountingMode::TRUSTED:
            return ReplicaCountingMode::TRUSTED;
        default:
            return ReplicaCountingMode::ANY;
    }
}

}
void 
DistributorConfiguration::configure(const DistributorManagerConfig & config)
{
    if ((config.splitsize != 0 && config.joinsize > config.splitsize)
        || (config.splitcount != 0 && config.joincount > config.splitcount))
    {
        std::ostringstream ost;
        ost << "Split limits must be higher than join limits (both count and "
            << "size). Values gotten are size(join(" << config.joinsize
            << ")/split(" << config.splitsize << ")) count(join("
            << config.joincount << ")/split(" << config.splitcount << "))";
        throw vespalib::IllegalArgumentException(ost.str(), VESPA_STRLOC);
    }
    
    _byteCountSplitLimit = config.splitsize;
    _docCountSplitLimit = config.splitcount;
    _byteCountJoinLimit = config.joinsize;
    _docCountJoinLimit = config.joincount;
    _minimalBucketSplit = std::max(config.minsplitcount, static_cast<int>(spi::BucketLimits::MinUsedBits));
    _maxNodesPerMerge = config.maximumNodesPerMerge;
    _max_consecutively_inhibited_maintenance_ticks = config.maxConsecutivelyInhibitedMaintenanceTicks;

    _garbageCollectionInterval = std::chrono::seconds(config.garbagecollection.interval);

    if (containsTimeStatement(config.garbagecollection.selectiontoremove)) {
        // Always changes.
        _lastGarbageCollectionChange = vespalib::steady_time::min();
    } else if (_garbageCollectionSelection != config.garbagecollection.selectiontoremove) {
        _lastGarbageCollectionChange = vespalib::steady_clock::now();
    }

    _garbageCollectionSelection = config.garbagecollection.selectiontoremove;

    // Don't garbage collect with empty selection.
    if (_garbageCollectionSelection.empty()) {
      _garbageCollectionInterval = vespalib::duration::zero();
    }

    _doInlineSplit = config.inlinebucketsplitting;
    _enableJoinForSiblingLessBuckets = config.enableJoinForSiblingLessBuckets;
    _enableInconsistentJoin = config.enableInconsistentJoin;

    _disableBucketActivation = config.disableBucketActivation;
    _allowStaleReadsDuringClusterStateTransitions = config.allowStaleReadsDuringClusterStateTransitions;
    _merge_operations_disabled = config.mergeOperationsDisabled;
    _use_weak_internal_read_consistency_for_client_gets = config.useWeakInternalReadConsistencyForClientGets;
    _max_activation_inhibited_out_of_sync_groups = config.maxActivationInhibitedOutOfSyncGroups;
    _enable_operation_cancellation = config.enableOperationCancellation;
    _minimumReplicaCountingMode = deriveReplicaCountingMode(config.minimumReplicaCountingMode);
    _symmetric_put_and_activate_replica_selection = config.symmetricPutAndActivateReplicaSelection;

    if (config.maxClusterClockSkewSec >= 0) {
        _maxClusterClockSkew = std::chrono::seconds(config.maxClusterClockSkewSec);
    }
    if (config.inhibitMergeSendingOnBusyNodeDurationSec >= 0) {
        _inhibitMergeSendingOnBusyNodeDuration = std::chrono::seconds(config.inhibitMergeSendingOnBusyNodeDurationSec);
    }
    _simulated_db_pruning_latency = std::chrono::milliseconds(std::max(0, config.simulatedDbPruningLatencyMsec));
    _simulated_db_merging_latency = std::chrono::milliseconds(std::max(0, config.simulatedDbMergingLatencyMsec));

    // TODO GC
    _update_fast_path_restart_enabled = true;
    _enable_metadata_only_fetch_phase_for_inconsistent_updates = true;

    LOG(debug,
        "Distributor now using new configuration parameters. Split limits: %d docs/%d bytes. "
        "Join limits: %d docs/%d bytes. Minimal bucket split %d. "
        "Documents to garbage collect: %s (check every %d seconds). ",
        (int)_docCountSplitLimit,
        (int)_byteCountSplitLimit,
        (int)_docCountJoinLimit,
        (int)_byteCountJoinLimit,
        (int)_minimalBucketSplit,
        _garbageCollectionSelection.c_str(),
        (int)vespalib::to_s(_garbageCollectionInterval));
}

void 
DistributorConfiguration::configure(const VisitorDispatcherConfig & config)
{
    _minBucketsPerVisitor = config.minbucketspervisitor;
    _maxVisitorsPerNodePerClientVisitor = config.maxvisitorspernodeperclientvisitor;
}

} // storage

