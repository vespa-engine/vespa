// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/distributor/tickable_stripe.h>
#include <cstdlib>

namespace storage::distributor {

struct MockTickableStripe : TickableStripe {
    bool tick() override { abort(); }
    void flush_and_close() override { abort(); }
    void update_total_distributor_config(std::shared_ptr<const DistributorConfiguration>) override { abort(); }
    void update_distribution_config(const BucketSpaceDistributionConfigs&) override { abort(); }
    void set_pending_cluster_state_bundle(const lib::ClusterStateBundle&) override { abort(); }
    void clear_pending_cluster_state_bundle() override { abort(); }
    void enable_cluster_state_bundle(const lib::ClusterStateBundle&, bool) override { abort(); }
    void notify_distribution_change_enabled() override { abort(); }
    PotentialDataLossReport remove_superfluous_buckets(document::BucketSpace, const lib::ClusterState&, bool) override {
        abort();
    }
    void merge_entries_into_db(document::BucketSpace,
                               api::Timestamp,
                               const lib::Distribution&,
                               const lib::ClusterState&,
                               const char*,
                               const std::unordered_set<uint16_t>&,
                               const std::vector<dbtransition::Entry>&) override
    {
        abort();
    }
    void update_read_snapshot_before_db_pruning() override { abort(); }
    void update_read_snapshot_after_db_pruning(const lib::ClusterStateBundle&) override { abort(); }
    void update_read_snapshot_after_activation(const lib::ClusterStateBundle&) override { abort(); }
    void clear_read_only_bucket_repo_databases() override { abort(); }

    void update_node_supported_features_repo(std::shared_ptr<const NodeSupportedFeaturesRepo>) override {
        abort();
    }

    void report_bucket_db_status(document::BucketSpace, std::ostream&) const override { abort(); }
    StripeAccessGuard::PendingOperationStats pending_operation_stats() const override { abort(); }
    void report_single_bucket_requests(vespalib::xml::XmlOutputStream&) const override { abort(); }
    void report_delayed_single_bucket_requests(vespalib::xml::XmlOutputStream&) const override { abort(); }
};

}
