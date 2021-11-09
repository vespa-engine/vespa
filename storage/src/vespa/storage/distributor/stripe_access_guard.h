// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "bucket_space_distribution_configs.h"
#include "pending_bucket_space_db_transition_entry.h"
#include "potential_data_loss_report.h"
#include <vespa/document/bucket/bucketspace.h>
#include <vespa/storageapi/defs.h>
#include <unordered_set> // TODO use hash_set instead

namespace storage::lib {
class ClusterState;
class ClusterStateBundle;
class Distribution;
}

namespace storage { class DistributorConfiguration; }

namespace vespalib::xml { class XmlOutputStream; }

namespace storage::distributor {

class NodeSupportedFeaturesRepo;

/**
 * A stripe access guard guarantees that the holder of a guard can access underlying
 * stripes via it in a thread safe manner. In particular, while any access guard is
 * held, all stripe threads must be in a safe rendezvous location with no race conditions
 * possible. When a guard goes out of scope, the stripe threads may resume operation.
 */
class StripeAccessGuard {
public:
    virtual ~StripeAccessGuard() = default;

    virtual void flush_and_close() = 0;

    virtual void update_total_distributor_config(std::shared_ptr<const DistributorConfiguration> config) = 0;

    virtual void update_distribution_config(const BucketSpaceDistributionConfigs& new_configs) = 0;
    virtual void set_pending_cluster_state_bundle(const lib::ClusterStateBundle& pending_state) = 0;
    virtual void clear_pending_cluster_state_bundle() = 0;
    virtual void enable_cluster_state_bundle(const lib::ClusterStateBundle& new_state,
                                             bool has_bucket_ownership_change) = 0;
    virtual void notify_distribution_change_enabled() = 0;

    virtual PotentialDataLossReport remove_superfluous_buckets(document::BucketSpace bucket_space,
                                                               const lib::ClusterState& new_state,
                                                               bool is_distribution_change) = 0;
    virtual void merge_entries_into_db(document::BucketSpace bucket_space,
                                       api::Timestamp gathered_at_timestamp,
                                       const lib::Distribution& distribution,
                                       const lib::ClusterState& new_state,
                                       const char* storage_up_states,
                                       const std::unordered_set<uint16_t>& outdated_nodes,
                                       const std::vector<dbtransition::Entry>& entries) = 0;

    virtual void update_read_snapshot_before_db_pruning() = 0;
    virtual void update_read_snapshot_after_db_pruning(const lib::ClusterStateBundle& new_state) = 0;
    virtual void update_read_snapshot_after_activation(const lib::ClusterStateBundle& activated_state) = 0;
    virtual void clear_read_only_bucket_repo_databases() = 0;

    virtual void update_node_supported_features_repo(std::shared_ptr<const NodeSupportedFeaturesRepo> features_repo) = 0;

    struct PendingOperationStats {
        size_t external_load_operations;
        size_t maintenance_operations;
        PendingOperationStats(size_t external_load_operations_in,
                              size_t maintenance_operations_in) noexcept
            : external_load_operations(external_load_operations_in),
              maintenance_operations(maintenance_operations_in) {}

        void merge(const PendingOperationStats& rhs) noexcept {
            external_load_operations += rhs.external_load_operations;
            maintenance_operations   += rhs.maintenance_operations;
        }
    };

    // Functions used for state reporting
    virtual void report_bucket_db_status(document::BucketSpace bucket_space, std::ostream& out) const = 0;
    virtual PendingOperationStats pending_operation_stats() const = 0;
    virtual void report_single_bucket_requests(vespalib::xml::XmlOutputStream& xos) const = 0;
    virtual void report_delayed_single_bucket_requests(vespalib::xml::XmlOutputStream& xos) const = 0;

};

/**
 * Provides a factory for guards that protect access to underlying stripes.
 *
 * Important: at most one StripeAccessorGuard may exist at any given time. Creating
 * concurrent guards is undefined behavior.
 */
class StripeAccessor {
public:
    virtual ~StripeAccessor() = default;

    virtual std::unique_ptr<StripeAccessGuard> rendezvous_and_hold_all() = 0;
    // TODO also accessor for a single particular stripe?
};

}
