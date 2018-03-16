// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/test/make_bucket_space.h>
#include <vespa/storage/distributor/maintenance/node_maintenance_stats_tracker.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>

namespace storage::distributor {

using document::test::makeBucketSpace;
using document::BucketSpace;

class NodeMaintenanceStatsTrackerTest : public CppUnit::TestFixture
{
    CPPUNIT_TEST_SUITE(NodeMaintenanceStatsTrackerTest);
    CPPUNIT_TEST(emptyStatsInstancesAreEqual);
    CPPUNIT_TEST(statsFieldsAffectEqualityComparison);
    CPPUNIT_TEST(requestingNonExistingNodeGivesEmptyStats);
    CPPUNIT_TEST(statsAreTrackedPerNode);
    CPPUNIT_TEST(statsAreTrackedPerBucketSpace);
    CPPUNIT_TEST_SUITE_END();

    void emptyStatsInstancesAreEqual();
    void statsFieldsAffectEqualityComparison();
    void requestingNonExistingNodeGivesEmptyStats();
    void statsAreTrackedPerNode();
    void statsAreTrackedPerBucketSpace();
    void assertEmptyBucketStats(BucketSpace bucketSpace, const NodeMaintenanceStatsTracker& tracker);
    void assertBucketStats(uint64_t expMovingOut, uint64_t expSyncing, uint64_t expCopyingIn, uint64_t expCopyingOut, uint64_t expTotal,
                           BucketSpace bucketSpace, const NodeMaintenanceStatsTracker& tracker);
};

CPPUNIT_TEST_SUITE_REGISTRATION(NodeMaintenanceStatsTrackerTest);

void
NodeMaintenanceStatsTrackerTest::emptyStatsInstancesAreEqual()
{
    NodeMaintenanceStats a;
    NodeMaintenanceStats b;
    CPPUNIT_ASSERT_EQUAL(a, b);
}

void
NodeMaintenanceStatsTrackerTest::statsFieldsAffectEqualityComparison()
{
    NodeMaintenanceStats a;
    NodeMaintenanceStats b;

    a.movingOut = 1;
    CPPUNIT_ASSERT(!(a == b));
    b.movingOut = 1;
    CPPUNIT_ASSERT(a == b);

    a.syncing = 1;
    CPPUNIT_ASSERT(!(a == b));
    b.syncing = 1;
    CPPUNIT_ASSERT(a == b);

    a.copyingIn = 1;
    CPPUNIT_ASSERT(!(a == b));
    b.copyingIn = 1;
    CPPUNIT_ASSERT(a == b);

    a.copyingOut = 1;
    CPPUNIT_ASSERT(!(a == b));
    b.copyingOut = 1;
    CPPUNIT_ASSERT(a == b);
}

void
NodeMaintenanceStatsTrackerTest::requestingNonExistingNodeGivesEmptyStats()
{
    NodeMaintenanceStatsTracker tracker;
    NodeMaintenanceStats wanted;
    CPPUNIT_ASSERT_EQUAL(wanted, tracker.forNode(0, makeBucketSpace()));
}

void
NodeMaintenanceStatsTrackerTest::statsAreTrackedPerNode()
{
    NodeMaintenanceStatsTracker tracker;
    NodeMaintenanceStats wanted;
    BucketSpace space(1);

    tracker.incMovingOut(0, space);
    wanted.movingOut = 1;
    CPPUNIT_ASSERT_EQUAL(wanted, tracker.forNode(0, space));
    wanted.movingOut = 0;
    CPPUNIT_ASSERT_EQUAL(wanted, tracker.forNode(1, space));

    tracker.incMovingOut(0, space);
    wanted.movingOut = 2;
    CPPUNIT_ASSERT_EQUAL(wanted, tracker.forNode(0, space));

    tracker.incMovingOut(1, space);
    wanted.movingOut = 1;
    CPPUNIT_ASSERT_EQUAL(wanted, tracker.forNode(1, space));

    tracker.incSyncing(1, space);
    tracker.incCopyingIn(1, space);
    tracker.incCopyingOut(1, space);
    wanted.syncing = 1;
    wanted.copyingIn = 1;
    wanted.copyingOut = 1;
    CPPUNIT_ASSERT_EQUAL(wanted, tracker.forNode(1, space));
}

void
NodeMaintenanceStatsTrackerTest::statsAreTrackedPerBucketSpace()
{
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
    CPPUNIT_ASSERT_EQUAL(expStats, tracker.forNode(0, bucketSpace));
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
    CPPUNIT_ASSERT_EQUAL(expStats, tracker.forNode(0, bucketSpace));
}

}

