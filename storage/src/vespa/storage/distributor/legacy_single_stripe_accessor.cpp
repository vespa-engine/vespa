// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "legacy_single_stripe_accessor.h"
#include "distributor_stripe.h"

namespace storage::distributor {

LegacySingleStripeAccessGuard::LegacySingleStripeAccessGuard(LegacySingleStripeAccessor& accessor,
                                                             DistributorStripe& stripe)
    : _accessor(accessor),
      _stripe(stripe)
{}

LegacySingleStripeAccessGuard::~LegacySingleStripeAccessGuard() {
    _accessor.mark_guard_released();
}

void LegacySingleStripeAccessGuard::update_distribution_config(const BucketSpaceDistributionConfigs& new_configs) {
    _stripe.update_distribution_config(new_configs);
}

void LegacySingleStripeAccessGuard::set_pending_cluster_state_bundle(const lib::ClusterStateBundle& pending_state) {
    _stripe.getBucketSpaceRepo().set_pending_cluster_state_bundle(pending_state);
    // TODO STRIPE also read only repo?
}

void LegacySingleStripeAccessGuard::clear_pending_cluster_state_bundle() {
    _stripe.getBucketSpaceRepo().clear_pending_cluster_state_bundle();
    // TODO STRIPE also read only repo?
}

void LegacySingleStripeAccessGuard::enable_cluster_state_bundle(const lib::ClusterStateBundle& new_state) {
    _stripe.enableClusterStateBundle(new_state);
}

void LegacySingleStripeAccessGuard::notify_distribution_change_enabled() {
    _stripe.notifyDistributionChangeEnabled();
}

PotentialDataLossReport
LegacySingleStripeAccessGuard::remove_superfluous_buckets(document::BucketSpace bucket_space,
                                                          const lib::ClusterState& new_state,
                                                          bool is_distribution_change)
{
    return _stripe.bucket_db_updater().remove_superfluous_buckets(bucket_space, new_state, is_distribution_change);
}

void
LegacySingleStripeAccessGuard::merge_entries_into_db(document::BucketSpace bucket_space,
                                                     api::Timestamp gathered_at_timestamp,
                                                     const lib::Distribution& distribution,
                                                     const lib::ClusterState& new_state,
                                                     const char* storage_up_states,
                                                     const std::unordered_set<uint16_t>& outdated_nodes,
                                                     const std::vector<dbtransition::Entry>& entries)
{
    _stripe.bucket_db_updater().merge_entries_into_db(bucket_space, gathered_at_timestamp, distribution,
                                                      new_state, storage_up_states, outdated_nodes, entries);
}

void LegacySingleStripeAccessGuard::update_read_snapshot_before_db_pruning() {
    _stripe.bucket_db_updater().update_read_snapshot_before_db_pruning();
}

void LegacySingleStripeAccessGuard::update_read_snapshot_after_db_pruning(const lib::ClusterStateBundle& new_state) {
    _stripe.bucket_db_updater().update_read_snapshot_after_db_pruning(new_state);
}

void LegacySingleStripeAccessGuard::update_read_snapshot_after_activation(const lib::ClusterStateBundle& activated_state) {
    _stripe.bucket_db_updater().update_read_snapshot_after_activation(activated_state);
}

void LegacySingleStripeAccessGuard::clear_read_only_bucket_repo_databases() {
    _stripe.bucket_db_updater().clearReadOnlyBucketRepoDatabases();
}

std::unique_ptr<StripeAccessGuard> LegacySingleStripeAccessor::rendezvous_and_hold_all() {
    // For sanity checking during development.
    assert(!_guard_held);
    _guard_held = true;
    return std::make_unique<LegacySingleStripeAccessGuard>(*this, _stripe);
}

void LegacySingleStripeAccessor::mark_guard_released() {
    assert(_guard_held);
    _guard_held = false;
}

}
