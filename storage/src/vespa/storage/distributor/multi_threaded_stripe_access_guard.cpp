// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    assert(_stripe_pool.stripe_count() > 0);
    _stripe_pool.park_all_threads();
}

MultiThreadedStripeAccessGuard::~MultiThreadedStripeAccessGuard() {
    _stripe_pool.unpark_all_threads();
    _accessor.mark_guard_released();
}

void MultiThreadedStripeAccessGuard::flush_and_close() {
    for_each_stripe([](TickableStripe& stripe) {
        stripe.flush_and_close();
    });
}

void MultiThreadedStripeAccessGuard::update_total_distributor_config(std::shared_ptr<const DistributorConfiguration> config) {
    for_each_stripe([&](TickableStripe& stripe) {
        stripe.update_total_distributor_config(config);
    });
}

void MultiThreadedStripeAccessGuard::update_distribution_config(const BucketSpaceDistributionConfigs& new_configs) {
    for_each_stripe([&](TickableStripe& stripe) {
        stripe.update_distribution_config(new_configs);
    });
}

void MultiThreadedStripeAccessGuard::set_pending_cluster_state_bundle(const lib::ClusterStateBundle& pending_state) {
    for_each_stripe([&](TickableStripe& stripe) {
        stripe.set_pending_cluster_state_bundle(pending_state);
    });
}

void MultiThreadedStripeAccessGuard::clear_pending_cluster_state_bundle() {
    for_each_stripe([](TickableStripe& stripe) {
        stripe.clear_pending_cluster_state_bundle();
    });
}

void MultiThreadedStripeAccessGuard::enable_cluster_state_bundle(const lib::ClusterStateBundle& new_state,
                                                                 bool has_bucket_ownership_change)
{
    for_each_stripe([&](TickableStripe& stripe) {
        stripe.enable_cluster_state_bundle(new_state, has_bucket_ownership_change);
    });
}

void MultiThreadedStripeAccessGuard::notify_distribution_change_enabled() {
    for_each_stripe([](TickableStripe& stripe) {
        stripe.notify_distribution_change_enabled();
    });
}

PotentialDataLossReport
MultiThreadedStripeAccessGuard::remove_superfluous_buckets(document::BucketSpace bucket_space,
                                                           const lib::ClusterState& new_state,
                                                           bool is_distribution_change)
{
    PotentialDataLossReport report;
    for_each_stripe([&](TickableStripe& stripe) {
        report.merge(stripe.remove_superfluous_buckets(bucket_space, new_state, is_distribution_change));
    });
    return report;
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
    if (entries.empty()) {
        return;
    }
    std::vector<dbtransition::Entry> stripe_entries;
    stripe_entries.reserve(entries.size() / _stripe_pool.stripe_count());
    auto* curr_stripe = &_stripe_pool.stripe_of_key(entries[0].bucket_key);
    stripe_entries.push_back(entries[0]);
    for (size_t i = 1; i < entries.size(); ++i) {
        const auto& entry = entries[i];
        auto* next_stripe = &_stripe_pool.stripe_of_key(entry.bucket_key);
        if (curr_stripe != next_stripe) {
            curr_stripe->merge_entries_into_db(bucket_space, gathered_at_timestamp, distribution,
                                               new_state, storage_up_states, outdated_nodes, stripe_entries);
            stripe_entries.clear();
        }
        curr_stripe = next_stripe;
        stripe_entries.push_back(entry);
    }
    curr_stripe->merge_entries_into_db(bucket_space, gathered_at_timestamp, distribution,
                                       new_state, storage_up_states, outdated_nodes, stripe_entries);
}

void MultiThreadedStripeAccessGuard::update_read_snapshot_before_db_pruning() {
    for_each_stripe([](TickableStripe& stripe) {
        stripe.update_read_snapshot_before_db_pruning();
    });
}

void MultiThreadedStripeAccessGuard::update_read_snapshot_after_db_pruning(const lib::ClusterStateBundle& new_state) {
    for_each_stripe([&](TickableStripe& stripe) {
        stripe.update_read_snapshot_after_db_pruning(new_state);
    });
}

void MultiThreadedStripeAccessGuard::update_read_snapshot_after_activation(const lib::ClusterStateBundle& activated_state) {
    for_each_stripe([&](TickableStripe& stripe) {
        stripe.update_read_snapshot_after_activation(activated_state);
    });
}

void MultiThreadedStripeAccessGuard::clear_read_only_bucket_repo_databases() {
    for_each_stripe([](TickableStripe& stripe) {
        stripe.clear_read_only_bucket_repo_databases();
    });
}

void MultiThreadedStripeAccessGuard::update_node_supported_features_repo(
        std::shared_ptr<const NodeSupportedFeaturesRepo> features_repo)
{
    for_each_stripe([&](TickableStripe& stripe) {
        stripe.update_node_supported_features_repo(features_repo);
    });
}

void MultiThreadedStripeAccessGuard::report_bucket_db_status(document::BucketSpace bucket_space, std::ostream& out) const {
    for_each_stripe([&](TickableStripe& stripe) {
        stripe.report_bucket_db_status(bucket_space, out);
    });
}

StripeAccessGuard::PendingOperationStats
MultiThreadedStripeAccessGuard::pending_operation_stats() const {
    StripeAccessGuard::PendingOperationStats stats(0, 0);
    for_each_stripe([&](const TickableStripe& stripe) {
        stats.merge(stripe.pending_operation_stats());
    });
    return stats;
}

void MultiThreadedStripeAccessGuard::report_single_bucket_requests(vespalib::xml::XmlOutputStream& xos) const {
    for_each_stripe([&](TickableStripe& stripe) {
        stripe.report_single_bucket_requests(xos);
    });
}

void MultiThreadedStripeAccessGuard::report_delayed_single_bucket_requests(vespalib::xml::XmlOutputStream& xos) const {
    for_each_stripe([&](TickableStripe& stripe) {
        stripe.report_delayed_single_bucket_requests(xos);
    });
}

template <typename Func>
void MultiThreadedStripeAccessGuard::for_each_stripe(Func&& f) {
    for (auto& stripe_thread : _stripe_pool) {
        f(stripe_thread->stripe());
    }
}

template <typename Func>
void MultiThreadedStripeAccessGuard::for_each_stripe(Func&& f) const {
    for (const auto& stripe_thread : _stripe_pool) {
        f(stripe_thread->stripe());
    }
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
