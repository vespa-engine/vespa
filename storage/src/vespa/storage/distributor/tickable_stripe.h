// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "stripe_access_guard.h"

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
 * A tickable stripe is the minimal binding glue between the stripe's worker thread and
 * the actual implementation. Primarily allows for easier testing without having to
 * fake an entire actual DistributorStripe.
 */
class TickableStripe {
public:
    virtual ~TickableStripe() = default;

    // Perform a single operation tick of the stripe logic.
    // If function returns true, the caller should not perform any waiting before calling
    // tick() again. This generally means that the stripe is processing client operations
    // and wants to continue doing so as quickly as possible.
    // Only used for multi-threaded striped setups.
    // TODO return an enum indicating type of last processed event? E.g. external, maintenance, none, ...
    virtual bool tick() = 0;

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

    // Functions used for state reporting
    virtual void report_bucket_db_status(document::BucketSpace bucket_space, std::ostream& out) const = 0;
    virtual StripeAccessGuard::PendingOperationStats pending_operation_stats() const = 0;
    virtual void report_single_bucket_requests(vespalib::xml::XmlOutputStream& xos) const = 0;
    virtual void report_delayed_single_bucket_requests(vespalib::xml::XmlOutputStream& xos) const = 0;

};

}
