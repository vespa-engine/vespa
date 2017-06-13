// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdstestlib/cppunit/macros.h>

#include <vespa/storage/distributor/maintenance/node_maintenance_stats_tracker.h>

namespace storage {
namespace distributor {

class NodeMaintenanceStatsTrackerTest : public CppUnit::TestFixture
{
    CPPUNIT_TEST_SUITE(NodeMaintenanceStatsTrackerTest);
    CPPUNIT_TEST(emptyStatsInstancesAreEqual);
    CPPUNIT_TEST(statsFieldsAffectEqualityComparison);
    CPPUNIT_TEST(requestingNonExistingNodeGivesEmptyStats);
    CPPUNIT_TEST(statsAreTrackedPerNode);
    CPPUNIT_TEST_SUITE_END();

    void emptyStatsInstancesAreEqual();
    void statsFieldsAffectEqualityComparison();
    void requestingNonExistingNodeGivesEmptyStats();
    void statsAreTrackedPerNode();
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
    CPPUNIT_ASSERT_EQUAL(wanted, tracker.forNode(0));
}

void
NodeMaintenanceStatsTrackerTest::statsAreTrackedPerNode()
{
    NodeMaintenanceStatsTracker tracker;
    NodeMaintenanceStats wanted;

    tracker.incMovingOut(0);
    wanted.movingOut = 1;
    CPPUNIT_ASSERT_EQUAL(wanted, tracker.forNode(0));
    wanted.movingOut = 0;
    CPPUNIT_ASSERT_EQUAL(wanted, tracker.forNode(1));

    tracker.incMovingOut(0);
    wanted.movingOut = 2;
    CPPUNIT_ASSERT_EQUAL(wanted, tracker.forNode(0));

    tracker.incMovingOut(1);
    wanted.movingOut = 1;
    CPPUNIT_ASSERT_EQUAL(wanted, tracker.forNode(1));

    tracker.incSyncing(1);
    tracker.incCopyingIn(1);
    tracker.incCopyingOut(1);
    wanted.syncing = 1;
    wanted.copyingIn = 1;
    wanted.copyingOut = 1;
    CPPUNIT_ASSERT_EQUAL(wanted, tracker.forNode(1));
}

} // distributor
} // storage

