// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/persistence/conformancetest/conformancetest.h>
#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/config-stor-distribution.h>

namespace storage {
namespace spi {

struct ClusterStateTest : public CppUnit::TestFixture {
    ClusterStateTest() {}

    CPPUNIT_TEST_SUITE(ClusterStateTest);
    CPPUNIT_TEST(testClusterUp);
    CPPUNIT_TEST(testNodeUp);
    CPPUNIT_TEST(testNodeInitializing);
    CPPUNIT_TEST(testReady);
    CPPUNIT_TEST(can_infer_own_node_retired_state);
    CPPUNIT_TEST_SUITE_END();

    void testClusterUp();
    void testNodeUp();
    void testNodeInitializing();
    void testReady();
    void can_infer_own_node_retired_state();
};

CPPUNIT_TEST_SUITE_REGISTRATION(ClusterStateTest);

void
ClusterStateTest::testClusterUp()
{
    lib::Distribution d(lib::Distribution::getDefaultDistributionConfig(3, 3));

    {
        lib::ClusterState s("version:1 storage:3 distributor:3");
        ClusterState state(s, 0, d);
        CPPUNIT_ASSERT_EQUAL(true, state.clusterUp());
    }

    {
        lib::ClusterState s("version:1 storage:3 .0.s:d distributor:3");
        ClusterState state(s, 0, d);
        CPPUNIT_ASSERT_EQUAL(true, state.clusterUp());
    }

    {
        lib::ClusterState s("version:1 cluster:d storage:3 .0.s:d distributor:3");
        ClusterState state(s, 0, d);
        CPPUNIT_ASSERT_EQUAL(false, state.clusterUp());
    }

    {
        lib::ClusterState s("version:1 cluster:d storage:3 distributor:3");
        ClusterState state(s, 0, d);
        CPPUNIT_ASSERT_EQUAL(false, state.clusterUp());
    }
}

void
ClusterStateTest::testNodeUp()
{
    lib::Distribution d(lib::Distribution::getDefaultDistributionConfig(3, 3));

    {
        lib::ClusterState s("version:1 storage:3 distributor:3");
        ClusterState state(s, 0, d);
        CPPUNIT_ASSERT_EQUAL(true, state.nodeUp());
    }

    {
        lib::ClusterState s("version:1 storage:3 .0.s:d distributor:3");
        ClusterState state(s, 0, d);
        CPPUNIT_ASSERT_EQUAL(false, state.nodeUp());
    }

    {
        lib::ClusterState s("version:1 storage:3 .0.s:d distributor:3");
        ClusterState state(s, 1, d);
        CPPUNIT_ASSERT_EQUAL(true, state.nodeUp());
    }

    {
        lib::ClusterState s("version:1 cluster:d storage:3 distributor:3");
        ClusterState state(s, 0, d);
        CPPUNIT_ASSERT_EQUAL(true, state.nodeUp());
    }

    {
        lib::ClusterState s("version:1 cluster:d storage:3 distributor:3 .0.s:d");
        ClusterState state(s, 0, d);
        CPPUNIT_ASSERT_EQUAL(true, state.nodeUp());
    }

    {
        lib::ClusterState s("version:1 cluster:d storage:3 .0.s:d distributor:3");
        ClusterState state(s, 0, d);
        CPPUNIT_ASSERT_EQUAL(false, state.nodeUp());
    }

    {
        lib::ClusterState s("version:1 cluster:d storage:3 .0.s:r distributor:3");
        ClusterState state(s, 0, d);
        CPPUNIT_ASSERT_EQUAL(true, state.nodeUp());
    }

    {
        lib::ClusterState s("version:1 cluster:d storage:3 .0.s:i distributor:3");
        ClusterState state(s, 0, d);
        CPPUNIT_ASSERT_EQUAL(true, state.nodeUp());
    }
}

namespace {

bool
nodeMarkedAsInitializingInState(const std::string& stateStr,
                                const lib::Distribution& d,
                                uint16_t node)
{
    lib::ClusterState s(stateStr);
    ClusterState state(s, node, d);
    return state.nodeInitializing();
}

} // anon ns

void
ClusterStateTest::testNodeInitializing()
{
    lib::Distribution d(lib::Distribution::getDefaultDistributionConfig(3, 3));

    CPPUNIT_ASSERT(!nodeMarkedAsInitializingInState(
            "version:1 storage:3 distributor:3", d, 0));
    CPPUNIT_ASSERT(nodeMarkedAsInitializingInState(
            "version:1 storage:3 .0.s:i distributor:3", d, 0));
    CPPUNIT_ASSERT(!nodeMarkedAsInitializingInState(
            "version:1 storage:3 .0.s:i distributor:3", d, 1));
    // To mirror nodeUp functionality, we ignore cluster state.
    CPPUNIT_ASSERT(nodeMarkedAsInitializingInState(
            "version:1 cluster:d storage:3 .0.s:i distributor:3", d, 0));
    // Distributors don't technically have init state, but just go with it.
    CPPUNIT_ASSERT(!nodeMarkedAsInitializingInState(
            "version:1 storage:3 distributor:3 .0.s:i", d, 0));
    CPPUNIT_ASSERT(!nodeMarkedAsInitializingInState(
            "version:1 storage:3 .0.s:d distributor:3", d, 0));
    CPPUNIT_ASSERT(!nodeMarkedAsInitializingInState(
            "version:1 storage:3 .0.s:r distributor:3", d, 0));
    CPPUNIT_ASSERT(!nodeMarkedAsInitializingInState(
            "version:1 storage:3 .0.s:m distributor:3", d, 0));
}

namespace {

lib::Distribution::DistributionConfig getCfg(uint16_t redundancy, uint16_t readyCopies)
{
    lib::Distribution::DistributionConfigBuilder config(lib::Distribution::getDefaultDistributionConfig(redundancy, 100).get());
    config.readyCopies = readyCopies;
    return config;
}

}

void
ClusterStateTest::testReady()
{
    lib::ClusterState s("version:1 storage:3 distributor:3");

    Bucket b(document::BucketId(16, 1), PartitionId(0));

    // With 3 copies, this bucket has ideal state 0, 2, 1

    // Nothing ready with 0 ready copies.
    {
        lib::Distribution d(getCfg(3, 0));
        ClusterState state(s, 0, d);
        CPPUNIT_ASSERT_EQUAL(false, state.shouldBeReady(b));
    }

    // Only node 0 with 1 ready copy.
    for (uint32_t i = 0; i < 3; ++i) {
        lib::Distribution d(getCfg(3, 1));
        ClusterState state(s, i, d);
        CPPUNIT_ASSERT_EQUAL(i == 0, state.shouldBeReady(b));
    }

    // All of them with 3 ready copies
    for (uint32_t i = 0; i < 3; ++i) {
        lib::Distribution d(getCfg(3, 3));
        ClusterState state(s, i, d);
        CPPUNIT_ASSERT_EQUAL(true, state.shouldBeReady(b));
    }

    // Node 0 and node 1 with 2 ready copies.
    for (uint32_t i = 0; i < 3; ++i) {
        lib::Distribution d(getCfg(3, 2));
        ClusterState state(s, i, d);
        CPPUNIT_ASSERT_EQUAL(i == 0 || i == 2, state.shouldBeReady(b));
    }

    // All of them with 3 ready copies
    for (uint32_t i = 0; i < 3; ++i) {
        lib::Distribution d(getCfg(3, 3));
        ClusterState state(s, i, d);
        CPPUNIT_ASSERT_EQUAL(true, state.shouldBeReady(b));
    }

    lib::ClusterState s2("version:1 storage:3 .0.s:d distributor:3");

    // The two others should be ready now
    for (uint32_t i = 0; i < 3; ++i) {
        lib::Distribution d(getCfg(3, 2));
        ClusterState state(s2, i, d);
        CPPUNIT_ASSERT_EQUAL(i == 1 || i == 2, state.shouldBeReady(b));
    }

    for (uint32_t i = 0; i < 3; ++i) {
        lib::Distribution d(getCfg(3, 1));
        ClusterState state(s2, i, d);
        CPPUNIT_ASSERT_EQUAL(i == 2, state.shouldBeReady(b));
    }
}

namespace {

bool
node_marked_as_retired_in_state(const std::string& stateStr,
                                const lib::Distribution& d,
                                uint16_t node)
{
    lib::ClusterState s(stateStr);
    ClusterState state(s, node, d);
    return state.nodeRetired();
}

}

void ClusterStateTest::can_infer_own_node_retired_state() {
    lib::Distribution d(lib::Distribution::getDefaultDistributionConfig(3, 3));

    CPPUNIT_ASSERT(!node_marked_as_retired_in_state("distributor:3 storage:3", d, 0));
    CPPUNIT_ASSERT(!node_marked_as_retired_in_state("distributor:3 storage:3 .0.s:i", d, 0));
    CPPUNIT_ASSERT(!node_marked_as_retired_in_state("distributor:3 storage:3 .0.s:d", d, 0));
    CPPUNIT_ASSERT(!node_marked_as_retired_in_state("distributor:3 storage:3 .0.s:m", d, 0));
    CPPUNIT_ASSERT(node_marked_as_retired_in_state("distributor:3 storage:3 .0.s:r", d, 0));
    CPPUNIT_ASSERT(!node_marked_as_retired_in_state("distributor:3 storage:3 .0.s:r", d, 1));
    CPPUNIT_ASSERT(!node_marked_as_retired_in_state("distributor:3 storage:3 .1.s:r", d, 0));
}

} // spi
} // storage
