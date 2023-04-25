// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/config/config-stor-distributormanager.h>
#include <vespa/storage/config/config-stor-visitordispatcher.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/storage/common/storagecomponent.h>
#include <vespa/vespalib/util/time.h>

namespace storage {

namespace distributor { struct LegacyDistributorTest; }

class DistributorConfiguration {
public: 
    explicit DistributorConfiguration(StorageComponent& component);
    ~DistributorConfiguration();

    struct MaintenancePriorities
    {
        // Defaults for these are chosen as those used as the current (non-
        // configurable) values at the time of implementation.
        uint8_t mergeMoveToIdealNode {120};
        uint8_t mergeOutOfSyncCopies {120};
        uint8_t mergeTooFewCopies {120};
        uint8_t mergeGlobalBuckets {115};
        uint8_t activateNoExistingActive {100};
        uint8_t activateWithExistingActive {100};
        uint8_t deleteBucketCopy {100};
        uint8_t joinBuckets {155};
        uint8_t splitDistributionBits {200};
        uint8_t splitLargeBucket {175};
        uint8_t splitInconsistentBucket {110};
        uint8_t garbageCollection {200};
    };

    using DistrConfig = vespa::config::content::core::StorDistributormanagerConfig;
    
    void configure(const DistrConfig& config);

    void configure(const vespa::config::content::core::StorVisitordispatcherConfig& config);


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

    bool stateCheckerIsActive(vespalib::stringref stateCheckerName) const {
        return _blockedStateCheckers.find(stateCheckerName) == _blockedStateCheckers.end();
    }

    void disableStateChecker(vespalib::stringref stateCheckerName) {
        _blockedStateCheckers.insert(stateCheckerName);
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

    bool getEnableHostInfoReporting() const noexcept {
        return _enableHostInfoReporting;
    }

    using ReplicaCountingMode = DistrConfig::MinimumReplicaCountingMode;

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

    bool getSequenceMutatingOperations() const noexcept {
        return _sequenceMutatingOperations;
    }
    void setSequenceMutatingOperations(bool sequenceMutations) noexcept {
        _sequenceMutatingOperations = sequenceMutations;
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
    void set_merge_operations_disabled(bool disabled) noexcept {
        _merge_operations_disabled = disabled;
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

    void set_prioritize_global_bucket_merges(bool prioritize) noexcept {
        _prioritize_global_bucket_merges = prioritize;
    }
    bool prioritize_global_bucket_merges() const noexcept {
        return _prioritize_global_bucket_merges;
    }

    void set_max_activation_inhibited_out_of_sync_groups(uint32_t max_groups) noexcept {
        _max_activation_inhibited_out_of_sync_groups = max_groups;
    }
    uint32_t max_activation_inhibited_out_of_sync_groups() const noexcept {
        return _max_activation_inhibited_out_of_sync_groups;
    }

    bool enable_revert() const noexcept {
        return _enable_revert;
    }
    [[nodiscard]] bool implicitly_clear_priority_on_schedule() const noexcept {
        return _implicitly_clear_priority_on_schedule;
    }
    void set_use_unordered_merge_chaining(bool unordered) noexcept {
        _use_unordered_merge_chaining = unordered;
    }
    [[nodiscard]] bool use_unordered_merge_chaining() const noexcept {
        return _use_unordered_merge_chaining;
    }
    void set_inhibit_default_merges_when_global_merges_pending(bool inhibit) noexcept {
        _inhibit_default_merges_when_global_merges_pending = inhibit;
    }
    [[nodiscard]] bool inhibit_default_merges_when_global_merges_pending() const noexcept {
        return _inhibit_default_merges_when_global_merges_pending;
    }
    void set_enable_two_phase_garbage_collection(bool enable) noexcept {
        _enable_two_phase_garbage_collection = enable;
    }
    [[nodiscard]] bool enable_two_phase_garbage_collection() const noexcept {
        return _enable_two_phase_garbage_collection;
    }
    void set_enable_condition_probing(bool enable) noexcept {
        _enable_condition_probing = enable;
    }
    [[nodiscard]] bool enable_condition_probing() const noexcept {
        return _enable_condition_probing;
    }

    uint32_t num_distributor_stripes() const noexcept { return _num_distributor_stripes; }

    bool containsTimeStatement(const std::string& documentSelection) const;
    
private:
    DistributorConfiguration(const DistributorConfiguration& other);
    DistributorConfiguration& operator=(const DistributorConfiguration& other);
    
    StorageComponent& _component;
    
    uint32_t _byteCountSplitLimit;
    uint32_t _docCountSplitLimit;
    uint32_t _byteCountJoinLimit;
    uint32_t _docCountJoinLimit;
    uint32_t _minimalBucketSplit;
    uint32_t _maxIdealStateOperations;
    uint32_t _idealStateChunkSize;
    uint32_t _maxNodesPerMerge;
    uint32_t _max_consecutively_inhibited_maintenance_ticks;
    uint32_t _max_activation_inhibited_out_of_sync_groups;

    std::string _garbageCollectionSelection;

    vespalib::steady_time _lastGarbageCollectionChange;
    vespalib::duration    _garbageCollectionInterval;

    uint32_t _minPendingMaintenanceOps;
    uint32_t _maxPendingMaintenanceOps;

    vespalib::hash_set<vespalib::string> _blockedStateCheckers;

    uint32_t _maxVisitorsPerNodePerClientVisitor;
    uint32_t _minBucketsPerVisitor;

    uint32_t _num_distributor_stripes;

    MaintenancePriorities _maintenancePriorities;
    std::chrono::seconds _maxClusterClockSkew;
    std::chrono::seconds _inhibitMergeSendingOnBusyNodeDuration;
    std::chrono::milliseconds _simulated_db_pruning_latency;
    std::chrono::milliseconds _simulated_db_merging_latency;

    bool _doInlineSplit;
    bool _enableJoinForSiblingLessBuckets;
    bool _enableInconsistentJoin;
    bool _enableHostInfoReporting;
    bool _disableBucketActivation;
    bool _sequenceMutatingOperations;
    bool _allowStaleReadsDuringClusterStateTransitions;
    bool _update_fast_path_restart_enabled;
    bool _merge_operations_disabled;
    bool _use_weak_internal_read_consistency_for_client_gets;
    bool _enable_metadata_only_fetch_phase_for_inconsistent_updates;
    bool _prioritize_global_bucket_merges;
    bool _enable_revert;
    bool _implicitly_clear_priority_on_schedule;
    bool _use_unordered_merge_chaining;
    bool _inhibit_default_merges_when_global_merges_pending;
    bool _enable_two_phase_garbage_collection;
    bool _enable_condition_probing;

    DistrConfig::MinimumReplicaCountingMode _minimumReplicaCountingMode;

    friend struct distributor::LegacyDistributorTest;
    void configureMaintenancePriorities(
            const vespa::config::content::core::StorDistributormanagerConfig&);
};

}


