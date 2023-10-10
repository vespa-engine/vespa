// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/test/make_bucket_space.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/storage/distributor/maintenance/node_maintenance_stats_tracker.h>
#include <vespa/vespalib/gtest/gtest.h>

namespace storage::distributor {

using document::test::makeBucketSpace;
using document::BucketSpace;
using namespace ::testing;

struct NodeMaintenanceStatsTrackerTest : Test {
    void assertEmptyBucketStats(BucketSpace bucketSpace, const NodeMaintenanceStatsTracker& tracker);
    void assertBucketStats(uint64_t expMovingOut, uint64_t expSyncing, uint64_t expCopyingIn, uint64_t expCopyingOut, uint64_t expTotal,
                           BucketSpace bucketSpace, const NodeMaintenanceStatsTracker& tracker);
};

TEST_F(NodeMaintenanceStatsTrackerTest, empty_stats_instances_are_equal) {
    NodeMaintenanceStats a;
    NodeMaintenanceStats b;
    EXPECT_EQ(a, b);
}

TEST_F(NodeMaintenanceStatsTrackerTest, stats_fields_affect_equality_comparison) {
    NodeMaintenanceStats a;
    NodeMaintenanceStats b;

    a.movingOut = 1;
    EXPECT_NE(a, b);
    b.movingOut = 1;
    EXPECT_EQ(a, b);

    a.syncing = 1;
    EXPECT_NE(a, b);
    b.syncing = 1;
    EXPECT_EQ(a, b);

    a.copyingIn = 1;
    EXPECT_NE(a, b);
    b.copyingIn = 1;
    EXPECT_EQ(a, b);

    a.copyingOut = 1;
    EXPECT_NE(a, b);
    b.copyingOut = 1;
    EXPECT_EQ(a, b);
}

TEST_F(NodeMaintenanceStatsTrackerTest, requesting_non_existing_node_gives_empty_stats) {
    NodeMaintenanceStatsTracker tracker;
    NodeMaintenanceStats wanted;
    EXPECT_EQ(wanted, tracker.forNode(0, makeBucketSpace()));
}

TEST_F(NodeMaintenanceStatsTrackerTest, stats_are_tracked_per_node){
    NodeMaintenanceStatsTracker tracker;
    NodeMaintenanceStats wanted;
    BucketSpace space(1);

    tracker.incMovingOut(0, space);
    wanted.movingOut = 1;
    EXPECT_EQ(wanted, tracker.forNode(0, space));
    wanted.movingOut = 0;
    EXPECT_EQ(wanted, tracker.forNode(1, space));

    tracker.incMovingOut(0, space);
    wanted.movingOut = 2;
    EXPECT_EQ(wanted, tracker.forNode(0, space));

    tracker.incMovingOut(1, space);
    wanted.movingOut = 1;
    EXPECT_EQ(wanted, tracker.forNode(1, space));

    tracker.incSyncing(1, space);
    tracker.incCopyingIn(1, space);
    tracker.incCopyingOut(1, space);
    wanted.syncing = 1;
    wanted.copyingIn = 1;
    wanted.copyingOut = 1;
    EXPECT_EQ(wanted, tracker.forNode(1, space));
}

TEST_F(NodeMaintenanceStatsTrackerTest, statsAreTrackedPerBucketSpace) {
    NodeMaintenanceStatsTracker tracker;
    BucketSpace fooSpace(3);
    BucketSpace barSpace(5);

    tracker.incTotal(0, fooSpace);
    tracker.incMovingOut(0, fooSpace);
    assertBucketStats(1, 0, 0, 0, 1, fooSpace, tracker);
    assertEmptyBucketStats(barSpace, tracker);

    tracker.incTotal(0, barSpace);
    tracker.incMovingOut(0, barSpace);
    assertBucketStats(1, 0, 0, 0, 1, fooSpace, tracker);
    assertBucketStats(1, 0, 0, 0, 1, barSpace, tracker);

    tracker.incTotal(0, fooSpace);
    tracker.incSyncing(0, fooSpace);
    assertBucketStats(1, 1, 0, 0, 2, fooSpace, tracker);
    assertBucketStats(1, 0, 0, 0, 1, barSpace, tracker);

    tracker.incTotal(0, fooSpace);
    tracker.incCopyingIn(0, fooSpace);
    assertBucketStats(1, 1, 1, 0, 3, fooSpace, tracker);
    assertBucketStats(1, 0, 0, 0, 1, barSpace, tracker);

    tracker.incTotal(0, fooSpace);
    tracker.incCopyingOut(0, fooSpace);
    assertBucketStats(1, 1, 1, 1, 4, fooSpace, tracker);
    assertBucketStats(1, 0, 0, 0, 1, barSpace, tracker);
}

void
NodeMaintenanceStatsTrackerTest::assertEmptyBucketStats(BucketSpace bucketSpace,
                                                        const NodeMaintenanceStatsTracker& tracker)
{
    NodeMaintenanceStats expStats;
    EXPECT_EQ(expStats, tracker.forNode(0, bucketSpace));
}

void
NodeMaintenanceStatsTrackerTest::assertBucketStats(uint64_t expMovingOut,
                                                   uint64_t expSyncing,
                                                   uint64_t expCopyingIn,
                                                   uint64_t expCopyingOut,
                                                   uint64_t expTotal,
                                                   BucketSpace bucketSpace,
                                                   const NodeMaintenanceStatsTracker& tracker)
{
    NodeMaintenanceStats expStats(expMovingOut, expSyncing, expCopyingIn, expCopyingOut, expTotal);
    EXPECT_EQ(expStats, tracker.forNode(0, bucketSpace));
}

}

