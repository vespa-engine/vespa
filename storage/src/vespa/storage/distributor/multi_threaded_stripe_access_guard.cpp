// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "multi_threaded_stripe_access_guard.h"
#include "distributor_stripe.h"
#include "distributor_stripe_pool.h"
#include "distributor_stripe_thread.h"

namespace storage::distributor {

MultiThreadedStripeAccessGuard::MultiThreadedStripeAccessGuard(
        MultiThreadedStripeAccessor& accessor,
        DistributorStripePool& stripe_pool)
    : _accessor(accessor),
      _stripe_pool(stripe_pool)
{
    assert(_stripe_pool.stripe_count() == 1); // TODO STRIPE many more yes yes
    _stripe_pool.park_all_threads();
}

MultiThreadedStripeAccessGuard::~MultiThreadedStripeAccessGuard() {
    _stripe_pool.unpark_all_threads();
    _accessor.mark_guard_released();
}

void MultiThreadedStripeAccessGuard::flush_and_close() {
    first_stripe().flush_and_close();
}

void MultiThreadedStripeAccessGuard::update_total_distributor_config(std::shared_ptr<const DistributorConfiguration> config) {
    // TODO STRIPE multiple stripes
    first_stripe().update_total_distributor_config(std::move(config));
}

void MultiThreadedStripeAccessGuard::update_distribution_config(const BucketSpaceDistributionConfigs& new_configs) {
    // TODO STRIPE multiple stripes
    first_stripe().update_distribution_config(new_configs);
}

void MultiThreadedStripeAccessGuard::set_pending_cluster_state_bundle(const lib::ClusterStateBundle& pending_state) {
    // TODO STRIPE multiple stripes
    first_stripe().set_pending_cluster_state_bundle(pending_state);
}

void MultiThreadedStripeAccessGuard::clear_pending_cluster_state_bundle() {
    // TODO STRIPE multiple stripes
    first_stripe().clear_pending_cluster_state_bundle();
}

void MultiThreadedStripeAccessGuard::enable_cluster_state_bundle(const lib::ClusterStateBundle& new_state) {
    // TODO STRIPE multiple stripes
    first_stripe().enable_cluster_state_bundle(new_state);
}

void MultiThreadedStripeAccessGuard::notify_distribution_change_enabled() {
    // TODO STRIPE multiple stripes
    first_stripe().notify_distribution_change_enabled();
}

PotentialDataLossReport
MultiThreadedStripeAccessGuard::remove_superfluous_buckets(document::BucketSpace bucket_space,
                                                           const lib::ClusterState& new_state,
                                                           bool is_distribution_change)
{
    // TODO STRIPE multiple stripes
    return first_stripe().remove_superfluous_buckets(bucket_space, new_state, is_distribution_change);
}

void
MultiThreadedStripeAccessGuard::merge_entries_into_db(document::BucketSpace bucket_space,
                                                      api::Timestamp gathered_at_timestamp,
                                                      const lib::Distribution& distribution,
                                                      const lib::ClusterState& new_state,
                                                      const char* storage_up_states,
                                                      const std::unordered_set<uint16_t>& outdated_nodes,
                                                      const std::vector<dbtransition::Entry>& entries)
{
    // TODO STRIPE multiple stripes
    first_stripe().merge_entries_into_db(bucket_space, gathered_at_timestamp, distribution,
                                         new_state, storage_up_states, outdated_nodes, entries);
}

void MultiThreadedStripeAccessGuard::update_read_snapshot_before_db_pruning() {
    // TODO STRIPE multiple stripes
    first_stripe().update_read_snapshot_before_db_pruning();
}

void MultiThreadedStripeAccessGuard::update_read_snapshot_after_db_pruning(const lib::ClusterStateBundle& new_state) {
    // TODO STRIPE multiple stripes
    first_stripe().update_read_snapshot_after_db_pruning(new_state);
}

void MultiThreadedStripeAccessGuard::update_read_snapshot_after_activation(const lib::ClusterStateBundle& activated_state) {
    // TODO STRIPE multiple stripes
    first_stripe().update_read_snapshot_after_activation(activated_state);
}

void MultiThreadedStripeAccessGuard::clear_read_only_bucket_repo_databases() {
    // TODO STRIPE multiple stripes
    first_stripe().clear_read_only_bucket_repo_databases();
}

void MultiThreadedStripeAccessGuard::report_bucket_db_status(document::BucketSpace bucket_space, std::ostream& out) const {
    // TODO STRIPE multiple stripes
    first_stripe().report_bucket_db_status(bucket_space, out);
}

StripeAccessGuard::PendingOperationStats
MultiThreadedStripeAccessGuard::pending_operation_stats() const {
    // TODO STRIPE multiple stripes
    return first_stripe().pending_operation_stats();
}

void MultiThreadedStripeAccessGuard::report_single_bucket_requests(vespalib::xml::XmlOutputStream& xos) const {
    // TODO STRIPE multiple stripes
    first_stripe().report_single_bucket_requests(xos);
}

void MultiThreadedStripeAccessGuard::report_delayed_single_bucket_requests(vespalib::xml::XmlOutputStream& xos) const {
    // TODO STRIPE multiple stripes
    first_stripe().report_delayed_single_bucket_requests(xos);
}

TickableStripe& MultiThreadedStripeAccessGuard::first_stripe() noexcept {
    return _stripe_pool.stripe_thread(0).stripe();
}

const TickableStripe& MultiThreadedStripeAccessGuard::first_stripe() const noexcept {
    return _stripe_pool.stripe_thread(0).stripe();
}

std::unique_ptr<StripeAccessGuard> MultiThreadedStripeAccessor::rendezvous_and_hold_all() {
    // For sanity checking of invariant of only one guard being allowed at any given time.
    assert(!_guard_held);
    _guard_held = true;
    return std::make_unique<MultiThreadedStripeAccessGuard>(*this, _stripe_pool);
}

void MultiThreadedStripeAccessor::mark_guard_released() {
    assert(_guard_held);
    _guard_held = false;
}

}
