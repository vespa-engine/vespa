// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "stripe_access_guard.h"

namespace storage::distributor {

class DistributorStripe;
class LegacySingleStripeAccessor;

/**
 * Very simple stripe access guard which expects the caller and its single stripe to run in the
 * same thread. This means there's no actual striping of operations or any thread synchronization
 * performed. Only intended as a stop-gap while we have legacy stripe behavior.
 */
class LegacySingleStripeAccessGuard : public StripeAccessGuard {
    LegacySingleStripeAccessor& _accessor;
    DistributorStripe&          _stripe;
public:
    LegacySingleStripeAccessGuard(LegacySingleStripeAccessor& accessor,
                                  DistributorStripe& stripe);
    ~LegacySingleStripeAccessGuard() override;

    void update_total_distributor_config(std::shared_ptr<const DistributorConfiguration> config) override;

    void update_distribution_config(const BucketSpaceDistributionConfigs& new_configs) override;
    void set_pending_cluster_state_bundle(const lib::ClusterStateBundle& pending_state) override;
    void clear_pending_cluster_state_bundle() override;
    void enable_cluster_state_bundle(const lib::ClusterStateBundle& new_state) override;
    void notify_distribution_change_enabled() override;

    PotentialDataLossReport remove_superfluous_buckets(document::BucketSpace bucket_space,
                                                       const lib::ClusterState& new_state,
                                                       bool is_distribution_change) override;
    void merge_entries_into_db(document::BucketSpace bucket_space,
                               api::Timestamp gathered_at_timestamp,
                               const lib::Distribution& distribution,
                               const lib::ClusterState& new_state,
                               const char* storage_up_states,
                               const std::unordered_set<uint16_t>& outdated_nodes,
                               const std::vector<dbtransition::Entry>& entries) override;

    void update_read_snapshot_before_db_pruning() override;
    void update_read_snapshot_after_db_pruning(const lib::ClusterStateBundle& new_state) override;
    void update_read_snapshot_after_activation(const lib::ClusterStateBundle& activated_state) override;
    void clear_read_only_bucket_repo_databases() override;
};

/**
 * Impl of StripeAccessor which creates LegacySingleStripeAccessGuards bound to a single stripe.
 */
class LegacySingleStripeAccessor : public StripeAccessor {
    DistributorStripe& _stripe;
    bool               _guard_held;

    friend class LegacySingleStripeAccessGuard;
public:
    explicit LegacySingleStripeAccessor(DistributorStripe& stripe)
        : _stripe(stripe),
          _guard_held(false)
    {}
    ~LegacySingleStripeAccessor() override = default;

    std::unique_ptr<StripeAccessGuard> rendezvous_and_hold_all() override;
private:
    void mark_guard_released();
};

}
