// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "mock_tickable_stripe.h"
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/storage/distributor/distributor_stripe_pool.h>
#include <vespa/storage/distributor/multi_threaded_stripe_access_guard.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace ::testing;

namespace storage::distributor {

struct AggregationTestingMockTickableStripe : MockTickableStripe {
    PotentialDataLossReport report;

    PotentialDataLossReport remove_superfluous_buckets(document::BucketSpace, const lib::ClusterState&, bool) override {
        return report;
    }

    bool tick() override {
        return false;
    }
};

struct MultiThreadedStripeAccessGuardTest : Test {
    DistributorStripePool                _pool;
    MultiThreadedStripeAccessor          _accessor;
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
        _pool.start({{&_stripe1, &_stripe2, &_stripe3}});
    }
};

TEST_F(MultiThreadedStripeAccessGuardTest, remove_superfluous_buckets_aggregates_reports_across_stripes) {
    _stripe1.report = PotentialDataLossReport(20, 100);
    _stripe2.report = PotentialDataLossReport(5,  200);
    _stripe3.report = PotentialDataLossReport(7,  350);
    start_pool_with_stripes();

    auto guard = _accessor.rendezvous_and_hold_all();
    auto report = guard->remove_superfluous_buckets(document::FixedBucketSpaces::default_space(),
                                                    lib::ClusterState(), false);
    EXPECT_EQ(report.buckets, 32);
    EXPECT_EQ(report.documents, 650);
}

}
