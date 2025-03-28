// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "replica_counting_mode.h"
#include <vespa/vespalib/util/time.h>

namespace vespa::config::content::core::internal {
    class InternalStorDistributormanagerType;
    class InternalStorVisitordispatcherType;
}

namespace storage {

class StorageComponent;

class DistributorConfiguration {
public:
    DistributorConfiguration(const DistributorConfiguration& other) = delete;
    DistributorConfiguration& operator=(const DistributorConfiguration& other) = delete;
    explicit DistributorConfiguration(StorageComponent& component);
    ~DistributorConfiguration();

    struct MaintenancePriorities {
        uint8_t mergeMoveToIdealNode {165};
        uint8_t mergeOutOfSyncCopies {120};
        uint8_t mergeTooFewCopies {120};
        uint8_t mergeGlobalBuckets {115};
        uint8_t activateNoExistingActive {100};
        uint8_t activateWithExistingActive {100};
        uint8_t deleteBucketCopy {120};
        uint8_t joinBuckets {120};
        uint8_t splitDistributionBits {200};
        uint8_t splitLargeBucket {120};
        uint8_t splitInconsistentBucket {110};
        uint8_t garbageCollection {200};
    };
    using DistributorManagerConfig = vespa::config::content::core::internal::InternalStorDistributormanagerType;
    using VisitorDispatcherConfig = vespa::config::content::core::internal::InternalStorVisitordispatcherType;

    void configure(const DistributorManagerConfig& config);
    void configure(const VisitorDispatcherConfig& config);

    const std::string& getGarbageCollectionSelection() const {
        return _garbageCollectionSelection;
    }

    vespalib::duration getGarbageCollectionInterval() const {
        return _garbageCollectionInterval;
    }

    void setGarbageCollection(const std::string& selection, vespalib::duration interval) {
        _garbageCollectionSelection = selection;
        _garbageCollectionInterval = interval;
    }

    void setLastGarbageCollectionChangeTime(vespalib::steady_time lastChangeTime) {
        _lastGarbageCollectionChange = lastChangeTime;
    }

    void setDoInlineSplit(bool value) {
        _doInlineSplit = value;
    }
    
    bool doInlineSplit() const {
        return _doInlineSplit;
    }

    /**
       Sets the number of documents needed for a bucket to be split.

       @param count The minimum number of documents a bucket needs to have to be split.
    */
    void setSplitCount(uint32_t count) { _docCountSplitLimit = count; }

    /**
       Sets the number of bytes needed for a bucket to be split.

       @param sz The minimum size (in bytes) a bucket needs to have in order to be split.
    */
    void setSplitSize(uint32_t sz) { _byteCountSplitLimit = sz; }

    /**
       Sets the maximum number of documents two buckets can have in order to be joined. The sum
       of the documents in the two buckets need to be below this limit for join to occur.

       @param count The maximum number of documents two buckets need to have in order to be joined.
    */
    void setJoinCount(uint32_t count) { _docCountJoinLimit = count; }

    /**
       Sets the maximum number of stored bytes two buckets can have in order to be joined. The sum
       of the sizes of the two buckets need to be below this limit for join to occur.

       @param count The maximum size the two buckets need to have in order to be joined.
    */
    void setJoinSize(uint32_t sz) { _byteCountJoinLimit = sz; }

    /**
       Sets the minimal bucket split level we want buckets to have. Buckets that have fewer used bits
       than this are automatically split.

       @param splitBits The minimal bucket split level.
    */
    void setMinimalBucketSplit(int splitBits) { _minimalBucketSplit = splitBits; };

    void setMaintenancePriorities(const MaintenancePriorities& mp) {
        _maintenancePriorities = mp;
    }

    const MaintenancePriorities& getMaintenancePriorities() const {
        return _maintenancePriorities;
    }

    uint8_t default_external_feed_priority() const noexcept { return 120; }
    
    /**
       @see setSplitCount
    */
    uint32_t getSplitCount() const { return _docCountSplitLimit; }

    /**
       @see setSplitSize
    */
    uint32_t getSplitSize() const { return _byteCountSplitLimit; }

    /**
       @see setJoinCount
    */
    uint32_t getJoinCount() const { return _docCountJoinLimit; }

    /**
       @see setJoinSize
    */
    uint32_t getJoinSize() const { return _byteCountJoinLimit; }

    /**
       @see setMinimalBucketSplit
    */
    uint32_t getMinimalBucketSplit() const { return _minimalBucketSplit; };

    uint32_t getMinPendingMaintenanceOps() const {
        return _minPendingMaintenanceOps;
    }
    void setMinPendingMaintenanceOps(uint32_t minPendingMaintenanceOps) {
        _minPendingMaintenanceOps = minPendingMaintenanceOps;
    }
    uint32_t getMaxPendingMaintenanceOps() const {
        return _maxPendingMaintenanceOps;
    }
    void setMaxPendingMaintenanceOps(uint32_t maxPendingMaintenanceOps) {
        _maxPendingMaintenanceOps = maxPendingMaintenanceOps;
    }

    uint32_t getMaxVisitorsPerNodePerClientVisitor() const {
        return _maxVisitorsPerNodePerClientVisitor;
    }
    uint32_t getMinBucketsPerVisitor() const {
        return _minBucketsPerVisitor;
    }

    uint32_t getMaxNodesPerMerge() const {
        return _maxNodesPerMerge;
    }
    bool getEnableJoinForSiblingLessBuckets() const {
        return _enableJoinForSiblingLessBuckets;
    }
    bool getEnableInconsistentJoin() const noexcept {
        return _enableInconsistentJoin;
    }

    ReplicaCountingMode getMinimumReplicaCountingMode() const noexcept {
        return _minimumReplicaCountingMode;
    }
    bool isBucketActivationDisabled() const noexcept {
        return _disableBucketActivation;
    }
    std::chrono::seconds getMaxClusterClockSkew() const noexcept {
        return _maxClusterClockSkew;
    }
    std::chrono::seconds getInhibitMergesOnBusyNodeDuration() const noexcept {
        return _inhibitMergeSendingOnBusyNodeDuration;
    }

    std::chrono::milliseconds simulated_db_pruning_latency() const noexcept {
        return _simulated_db_pruning_latency;
    }
    std::chrono::milliseconds simulated_db_merging_latency() const noexcept {
        return _simulated_db_merging_latency;
    }

    bool allowStaleReadsDuringClusterStateTransitions() const noexcept {
        return _allowStaleReadsDuringClusterStateTransitions;
    }
    void setAllowStaleReadsDuringClusterStateTransitions(bool allow) noexcept {
        _allowStaleReadsDuringClusterStateTransitions = allow;
    }

    bool update_fast_path_restart_enabled() const noexcept {
        return _update_fast_path_restart_enabled;
    }
    void set_update_fast_path_restart_enabled(bool enabled) noexcept {
        _update_fast_path_restart_enabled = enabled;
    }

    bool merge_operations_disabled() const noexcept {
        return _merge_operations_disabled;
    }

    void set_use_weak_internal_read_consistency_for_client_gets(bool use_weak) noexcept {
        _use_weak_internal_read_consistency_for_client_gets = use_weak;
    }
    bool use_weak_internal_read_consistency_for_client_gets() const noexcept {
        return _use_weak_internal_read_consistency_for_client_gets;
    }

    void set_enable_metadata_only_fetch_phase_for_inconsistent_updates(bool enable) noexcept {
        _enable_metadata_only_fetch_phase_for_inconsistent_updates = enable;
    }
    bool enable_metadata_only_fetch_phase_for_inconsistent_updates() const noexcept {
        return _enable_metadata_only_fetch_phase_for_inconsistent_updates;
    }

    uint32_t max_consecutively_inhibited_maintenance_ticks() const noexcept {
        return _max_consecutively_inhibited_maintenance_ticks;
    }

    void set_max_activation_inhibited_out_of_sync_groups(uint32_t max_groups) noexcept {
        _max_activation_inhibited_out_of_sync_groups = max_groups;
    }
    uint32_t max_activation_inhibited_out_of_sync_groups() const noexcept {
        return _max_activation_inhibited_out_of_sync_groups;
    }

    [[nodiscard]] uint32_t max_document_operation_message_size_bytes() const noexcept {
        return _max_document_operation_message_size_bytes;
    }
    void set_max_document_operation_message_size_bytes(uint32_t max_size_bytes) noexcept {
        // We use uint32_t internally but cap to INT32_MAX due to wire format restrictions
        _max_document_operation_message_size_bytes = std::min(max_size_bytes, static_cast<uint32_t>(INT32_MAX));
    }

    [[nodiscard]] bool enable_operation_cancellation() const noexcept {
        return _enable_operation_cancellation;
    }
    [[nodiscard]] bool symmetric_put_and_activate_replica_selection() const noexcept {
        return _symmetric_put_and_activate_replica_selection;
    }

    [[nodiscard]] bool containsTimeStatement(const std::string& documentSelection) const;

private:
    StorageComponent& _component;
    
    uint32_t _byteCountSplitLimit;
    uint32_t _docCountSplitLimit;
    uint32_t _byteCountJoinLimit;
    uint32_t _docCountJoinLimit;
    uint32_t _minimalBucketSplit;
    uint32_t _maxNodesPerMerge;
    uint32_t _max_consecutively_inhibited_maintenance_ticks;
    uint32_t _max_activation_inhibited_out_of_sync_groups;
    uint32_t _max_document_operation_message_size_bytes;

    std::string _garbageCollectionSelection;

    vespalib::steady_time _lastGarbageCollectionChange;
    vespalib::duration    _garbageCollectionInterval;

    uint32_t _minPendingMaintenanceOps;
    uint32_t _maxPendingMaintenanceOps;

    uint32_t _maxVisitorsPerNodePerClientVisitor;
    uint32_t _minBucketsPerVisitor;

    MaintenancePriorities _maintenancePriorities;
    std::chrono::seconds _maxClusterClockSkew;
    std::chrono::seconds _inhibitMergeSendingOnBusyNodeDuration;
    std::chrono::milliseconds _simulated_db_pruning_latency;
    std::chrono::milliseconds _simulated_db_merging_latency;

    bool _doInlineSplit;
    bool _enableJoinForSiblingLessBuckets;
    bool _enableInconsistentJoin;
    bool _disableBucketActivation;
    bool _allowStaleReadsDuringClusterStateTransitions;
    bool _update_fast_path_restart_enabled; //TODO Rewrite tests and GC
    bool _merge_operations_disabled;
    bool _use_weak_internal_read_consistency_for_client_gets;
    bool _enable_metadata_only_fetch_phase_for_inconsistent_updates; //TODO Rewrite tests and GC
    bool _enable_operation_cancellation;
    bool _symmetric_put_and_activate_replica_selection;

    ReplicaCountingMode _minimumReplicaCountingMode;
};

}


