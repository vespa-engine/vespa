// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "mock_tickable_stripe.h"
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/persistence/spi/bucket_limits.h>
#include <vespa/storage/distributor/distributor_stripe_pool.h>
#include <vespa/storage/distributor/multi_threaded_stripe_access_guard.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace ::testing;
using RawIdVector = std::vector<uint64_t>;

constexpr uint8_t MUB = storage::spi::BucketLimits::MinUsedBits;

namespace storage::distributor {

struct AggregationTestingMockTickableStripe : MockTickableStripe {
    PotentialDataLossReport report;
    std::vector<dbtransition::Entry> entries;
    StripeAccessGuard::PendingOperationStats pending_stats{0, 0};

    PotentialDataLossReport remove_superfluous_buckets(document::BucketSpace, const lib::ClusterState&, bool) override {
        return report;
    }

    void merge_entries_into_db(document::BucketSpace, api::Timestamp, const lib::Distribution&,
                               const lib::ClusterState&, const char*, const OutdatedNodes &,
                               const std::vector<dbtransition::Entry>& entries_in) override {
        entries = entries_in;
    }

    RawIdVector entries_as_raw_ids() const {
        std::vector<uint64_t> result;
        for (const auto& entry : entries) {
            result.push_back(entry.bucket_id().withoutCountBits());
        }
        std::sort(result.begin(), result.end());
        return result;
    }

    StripeAccessGuard::PendingOperationStats pending_operation_stats() const override {
        return pending_stats;
    }

    bool tick() override {
        return false;
    }
};

struct MultiThreadedStripeAccessGuardTest : Test {
    DistributorStripePool                _pool;
    MultiThreadedStripeAccessor          _accessor;
    AggregationTestingMockTickableStripe _stripe0;
    AggregationTestingMockTickableStripe _stripe1;
    AggregationTestingMockTickableStripe _stripe2;
    AggregationTestingMockTickableStripe _stripe3;

    MultiThreadedStripeAccessGuardTest()
        : _pool(),
          _accessor(_pool)
    {}

    ~MultiThreadedStripeAccessGuardTest() {
        _pool.stop_and_join();
    }

    void start_pool_with_stripes() {
        _pool.start({{&_stripe0, &_stripe1, &_stripe2, &_stripe3}});
    }

    void start_pool_with_one_stripe() {
        _pool.start({&_stripe0});
    }

    void merge_entries_into_db(const RawIdVector& raw_ids) {
        std::vector<dbtransition::Entry> entries;
        for (auto raw_id : raw_ids) {
            entries.emplace_back(document::BucketId(MUB, raw_id), BucketCopy());
        }
        std::sort(entries.begin(), entries.end());
        auto guard = _accessor.rendezvous_and_hold_all();
        guard->merge_entries_into_db(document::FixedBucketSpaces::default_space(), api::Timestamp(),
                                     lib::Distribution(), lib::ClusterState(), "", {},
                                     entries);
    }

};

TEST_F(MultiThreadedStripeAccessGuardTest, remove_superfluous_buckets_aggregates_reports_across_stripes) {
    _stripe0.report = PotentialDataLossReport(20, 100);
    _stripe1.report = PotentialDataLossReport(5,  200);
    _stripe2.report = PotentialDataLossReport(7,  350);
    _stripe3.report = PotentialDataLossReport(3,  30);
    start_pool_with_stripes();

    auto guard = _accessor.rendezvous_and_hold_all();
    auto report = guard->remove_superfluous_buckets(document::FixedBucketSpaces::default_space(),
                                                    lib::ClusterState(), false);
    EXPECT_EQ(report.buckets, 35);
    EXPECT_EQ(report.documents, 680);
}

TEST_F(MultiThreadedStripeAccessGuardTest, pending_operation_stats_aggregates_stats_across_stripes) {
    using Stats = StripeAccessGuard::PendingOperationStats;
    _stripe0.pending_stats = Stats(20, 100);
    _stripe1.pending_stats = Stats(5,  200);
    _stripe2.pending_stats = Stats(7,  350);
    _stripe3.pending_stats = Stats(3,  30);
    start_pool_with_stripes();

    auto guard = _accessor.rendezvous_and_hold_all();
    auto pending_stats = guard->pending_operation_stats();

    EXPECT_EQ(pending_stats.external_load_operations, 35);
    EXPECT_EQ(pending_stats.maintenance_operations, 680);
}

TEST_F(MultiThreadedStripeAccessGuardTest, merge_entries_into_db_operates_across_all_stripes) {
    start_pool_with_stripes();
    // Note: The bucket key is calculated by reversing the bits of the raw bucket id.
    // We have 4 stripes and use 2 stripe bits. The 2 MSB of the bucket key is used to map to stripe.
    // This gives the following mapping from raw bucket id to bucket key to stripe:
    // raw id | key (8 MSB) | stripe
    // 0x..0  | 00000000    | 0
    // 0x..1  | 10000000    | 2
    // 0x..2  | 01000000    | 1
    // 0x..3  | 11000000    | 3
    merge_entries_into_db({0x10,0x20,0x30,0x40,0x11,0x21,0x31,0x12,0x22,0x13});
    EXPECT_EQ(RawIdVector({0x10,0x20,0x30,0x40}), _stripe0.entries_as_raw_ids());
    EXPECT_EQ(RawIdVector({0x12,0x22}), _stripe1.entries_as_raw_ids());
    EXPECT_EQ(RawIdVector({0x11,0x21,0x31}), _stripe2.entries_as_raw_ids());
    EXPECT_EQ(RawIdVector({0x13}), _stripe3.entries_as_raw_ids());
}

TEST_F(MultiThreadedStripeAccessGuardTest, merge_entries_into_db_operates_across_subset_of_stripes) {
    start_pool_with_stripes();
    merge_entries_into_db({0x12,0x22,0x13});
    EXPECT_EQ(RawIdVector(), _stripe0.entries_as_raw_ids());
    EXPECT_EQ(RawIdVector({0x12,0x22}), _stripe1.entries_as_raw_ids());
    EXPECT_EQ(RawIdVector(), _stripe2.entries_as_raw_ids());
    EXPECT_EQ(RawIdVector({0x13}), _stripe3.entries_as_raw_ids());
}

TEST_F(MultiThreadedStripeAccessGuardTest, merge_entries_into_db_operates_across_one_stripe) {
    start_pool_with_one_stripe();
    merge_entries_into_db({0x10,0x11});
    EXPECT_EQ(RawIdVector({0x10,0x11}), _stripe0.entries_as_raw_ids());
}

TEST_F(MultiThreadedStripeAccessGuardTest, merge_entries_into_db_handles_empty_entries_vector) {
    start_pool_with_one_stripe();
    merge_entries_into_db({});
    EXPECT_EQ(RawIdVector(), _stripe0.entries_as_raw_ids());
}

}
