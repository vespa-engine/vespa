// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vespalib/util/exception.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/document/bucket/bucketidfactory.h>
#include <cmath>
#include <vespa/vdslib/state/random.h>
#include <vespa/vdstestlib/cppunit/macros.h>

using vespalib::string;

namespace storage {
namespace lib {

struct ClusterStateTest : public CppUnit::TestFixture {

    void testBasicFunctionality();
    void testErrorBehaviour();
    void testBackwardsCompability();
    void testDetailed();
    void testParseFailure();
    void testParseFailureGroups();

    void testDiff();

    CPPUNIT_TEST_SUITE(ClusterStateTest);
    CPPUNIT_TEST(testBasicFunctionality);
    CPPUNIT_TEST(testErrorBehaviour);
    CPPUNIT_TEST(testBackwardsCompability);
    CPPUNIT_TEST(testDetailed);

        // Ideal state tests.
    CPPUNIT_TEST(testParseFailure);
    CPPUNIT_TEST(testParseFailureGroups);
    CPPUNIT_TEST(testDiff);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(ClusterStateTest);

void
ClusterStateTest::testDiff() {
    ClusterState state1("distributor:9 storage:4");
    ClusterState state2("distributor:7 storage:6");
    ClusterState state3("distributor:9 storage:2");
    CPPUNIT_ASSERT_EQUAL(
            std::string("storage [4: d to u, 5: d to u] "
                        "distributor [7: u to d, 8: u to d]"),
            state1.getTextualDifference(state2));
    CPPUNIT_ASSERT_EQUAL(
            std::string("storage [2: u to d, 3: u to d, 4: u to d, 5: u to d] "
                        "distributor [7: d to u, 8: d to u]"),
            state2.getTextualDifference(state3));
}


#define VERIFY3(source, result, type, typestr) { \
    vespalib::asciistream ost; \
    try{ \
        state->serialize(ost, type); \
    } catch (std::exception& e) { \
        CPPUNIT_FAIL("Failed to serialize system state " \
                + state->toString(true) + " in " + std::string(typestr) \
                + " format: " + std::string(e.what())); \
    } \
    CPPUNIT_ASSERT_EQUAL_MSG(vespalib::string(state->toString(true)), \
            vespalib::string(typestr) + " \"" + vespalib::string(result) + "\"", \
            vespalib::string(typestr) + " \"" + ost.str() + "\""); \
}

#define VERIFY2(serialized, result, testOld, testNew) { \
    std::unique_ptr<ClusterState> state; \
    try{ \
        state.reset(new ClusterState(serialized)); \
    } catch (std::exception& e) { \
        CPPUNIT_FAIL("Failed to parse '" + std::string(serialized) \
                     + "': " + e.what()); \
    } \
    if (testOld) VERIFY3(serialized, result, true, "Old") \
    if (testNew) VERIFY3(serialized, result, false, "New") \
}

#define VERIFYSAMEOLD(serialized) VERIFY2(serialized, serialized, true, false)
#define VERIFYOLD(serialized, result) VERIFY2(serialized, result, true, false)
#define VERIFYSAMENEW(serialized) VERIFY2(serialized, serialized, false, true)
#define VERIFYNEW(serialized, result) VERIFY2(serialized, result, false, true)
#define VERIFYSAME(serialized) VERIFY2(serialized, serialized, true, true)
#define VERIFY(serialized, result) VERIFY2(serialized, result, true, true)

#define VERIFY_FAIL(serialized, error) { \
    try{ \
        ClusterState state(serialized); \
        CPPUNIT_FAIL("Parsing the state '" + std::string(serialized) \
                     + "' is supposed to fail."); \
    } catch (vespalib::Exception& e) { \
        CPPUNIT_ASSERT_MATCH_REGEX(error, e.getMessage()); \
    } \
}

void
ClusterStateTest::testBasicFunctionality()
{
    // Version is default and should not be written
    VERIFYNEW("version:0", "");
    VERIFYNEW("version:1", "version:1");

    // Cluster state up is default and should not be written
    VERIFYNEW("cluster:u", "");
    VERIFYSAMENEW("cluster:d");
    VERIFYSAMENEW("cluster:i");
    VERIFYSAMENEW("cluster:s");

    // No need to write node counts if no nodes exist.
    VERIFYNEW("cluster:d distributor:0 storage:0", "cluster:d");

    // Test legal distributor states
    VERIFYNEW("distributor:10 .1.s:i .2.s:u .3.s:s .4.s:d",
              "distributor:10 .1.s:i .1.i:0 .3.s:s .4.s:d");

    // Test legal storage states
    VERIFYNEW("storage:10 .1.s:i .2.s:u .3.s:d .4.s:m .5.s:r",
              "storage:10 .1.s:i .1.i:0 .3.s:d .4.s:m .5.s:r");

    // Test legal disk states
    VERIFYNEW("storage:10 .1.d:4 .1.d.0.s:u .1.d.1.s:d",
              "storage:10 .1.d:4 .1.d.1.s:d");

    // Test other disk properties
    VERIFYSAMENEW("storage:10 .1.d:4 .1.d.0.c:1.4");

    // Test other distributor node propertise
    // (Messages is excluded from system states to not make them too long as
    // most nodes have no use for them)
    VERIFYNEW("distributor:9 .7.m:foo\\x20bar", "distributor:9");
    VERIFYSAMENEW("distributor:4 .2.s:m");

    // Test other storage node propertise
    // (Messages is excluded from system states to not make them too long as
    // most nodes have no use for them)
    VERIFYNEW("storage:9 .3.c:2.3 .4.r:8 .7.m:foo\\x20bar",
              "storage:9 .3.c:2.3 .4.r:8");

    // Test that messages are kept in verbose mode, even if last index
    {
        ClusterState state("storage:5 .4.s:d .4.m:Foo\\x20bar");
        const NodeState& ns(state.getNodeState(Node(NodeType::STORAGE, 4)));
        CPPUNIT_ASSERT_EQUAL(string("Foo bar"), ns.getDescription());
    }

    ClusterState state;
    state.setClusterState(State::UP);
    state.setNodeState(Node(NodeType::DISTRIBUTOR, 3),
                       NodeState(NodeType::DISTRIBUTOR, State::UP));
    CPPUNIT_ASSERT_EQUAL(std::string("distributor:4 .0.s:d .1.s:d .2.s:d"),
                         state.toString(false));
    state.setNodeState(Node(NodeType::DISTRIBUTOR, 1),
                       NodeState(NodeType::DISTRIBUTOR, State::UP));
    CPPUNIT_ASSERT_EQUAL(std::string("distributor:4 .0.s:d .2.s:d"),
                         state.toString(false));
    state.setNodeState(Node(NodeType::DISTRIBUTOR, 3),
                       NodeState(NodeType::DISTRIBUTOR, State::DOWN));
    CPPUNIT_ASSERT_EQUAL(std::string("distributor:2 .0.s:d"),
                         state.toString(false));
    state.setNodeState(Node(NodeType::DISTRIBUTOR, 4),
                       NodeState(NodeType::DISTRIBUTOR, State::UP));
    CPPUNIT_ASSERT_EQUAL(std::string("distributor:5 .0.s:d .2.s:d .3.s:d"),
                         state.toString(false));
}

void
ClusterStateTest::testErrorBehaviour()
{
    // Keys with invalid values

    // Index out of range
    VERIFY_FAIL("storage:5 distributor:4 .4.s:s",
                "Cannot index distributor node 4 of 4");
    VERIFY_FAIL("distributor:5 storage:4 .4.s:s",
                "Cannot index storage node 4 of 4");

    // Test illegal cluster states
    VERIFY_FAIL("cluster:m", "Maintenance is not a legal cluster state");
    VERIFY_FAIL("cluster:r", "Retired is not a legal cluster state");

    // Test illegal distributor states
// Currently set to legal
//    VERIFY_FAIL("distributor:4 .2.s:r",
//                "Retired is not a legal distributor state");

    // Test illegal storage states
    VERIFY_FAIL("storage:4 .2.d:2 .2.d.5.s:d", "Cannot index disk 5 of 2");

    // Test blatantly illegal values for known attributes:
    VERIFY_FAIL("distributor:4 .2.s:z", "Unknown state z given.*");
    VERIFY_FAIL("distributor:4 .2.i:foobar",
                ".*Init progress must be a floating point number from .*");
    VERIFY_FAIL("storage:4 .2.d:foobar", "Invalid disk count 'foobar'. Need.*");
    VERIFY_FAIL("storage:4 .2.d:2 .2.d.1.s:foobar",
                "Unknown state foobar given.*");
    VERIFY_FAIL("storage:4 .2.d:2 .2.d.1.c:foobar",
                "Illegal disk capacity 'foobar'. Capacity must be a .*");
    VERIFY_FAIL("storage:4 .2.d:2 .2.d.a.s:d",
                "Invalid disk index 'a'. Need a positive integer .*");

    // Lacking absolute path first
    VERIFY_FAIL(".2.s:d distributor:4", "The first path in system state.*");

    // Unknown tokens
    VERIFYNEW("distributor:4 .2.d:2", "distributor:4");
    VERIFYNEW("distributor:4 .2.d:2 .2.d:2", "distributor:4");
    VERIFYNEW("distributor:4 .2.c:1.2 .3.r:2.0", "distributor:4");
    VERIFYNEW("distributor:4 .2:foo storage:5 .4:d", "distributor:4 storage:5");
    VERIFYNEW("ballalaika:true distributor:4 .2.urk:oj .2.z:foo .2.s:s "
              ".2.j:foo storage:10 .3.d:4 .3.d.2.a:boo .3.s:s",
              "distributor:4 .2.s:s storage:10 .3.s:s .3.d:4");
}

void
ClusterStateTest::testBackwardsCompability()
{
    // 4.1 and older nodes do not support some features, and the java parser
    // do not allow unknown elements as it was supposed to do, thus we should
    // avoid using new features when talking to 4.1 nodes.

    //  - 4.1 nodes should not see new cluster, version, initializing and
    //    description tags.
    VERIFYOLD("version:4 cluster:i storage:2 .0.s:i .0.i:0.5 .1.m:foobar",
              "distributor:0 storage:2 .0.s:i");

    //  - 4.1 nodes have only one disk property being state, so in 4.1, a
    //    disk state is typically set as .4.d.2:d while in new format it
    //    specifies that this is the state .4.d.2.s:d
    VERIFYSAMEOLD("distributor:0 storage:3 .2.d:10 .2.d.4:d");
    VERIFYOLD("distributor:0 storage:3 .2.d:10 .2.d.4.s:d",
              "distributor:0 storage:3 .2.d:10 .2.d.4:d");

    //  - 4.1 nodes should always have distributor and storage tags with counts.
    VERIFYOLD("storage:4", "distributor:0 storage:4");
    VERIFYOLD("distributor:4", "distributor:4 storage:0");

    //  - 4.1 nodes should not see the state stopping
    VERIFYOLD("storage:4 .2.s:s", "distributor:0 storage:4 .2.s:d");

}

void
ClusterStateTest::testDetailed()
{
    ClusterState state(
            "version:314 cluster:i "
            "distributor:8 .1.s:i .3.s:i .3.i:0.5 .5.s:d .7.m:foo\\x20bar "
            "storage:10 .2.d:16 .2.d.3:d .4.s:d .5.c:1.3 .5.r:4"
            " .6.m:bar\\tfoo .7.s:m .8.d:10 .8.d.4.c:0.6 .8.d.4.m:small"
    );
    CPPUNIT_ASSERT_EQUAL(314u, state.getVersion());
    CPPUNIT_ASSERT_EQUAL(State::INITIALIZING, state.getClusterState());
    CPPUNIT_ASSERT_EQUAL(uint16_t(8),state.getNodeCount(NodeType::DISTRIBUTOR));
    CPPUNIT_ASSERT_EQUAL(uint16_t(10),state.getNodeCount(NodeType::STORAGE));

    // Testing distributor node states
    for (uint16_t i = 0; i <= 20; ++i) {
        const NodeState& ns(state.getNodeState(Node(NodeType::DISTRIBUTOR, i)));
            // Test node states
        if (i == 1 || i == 3) {
            CPPUNIT_ASSERT_EQUAL(State::INITIALIZING, ns.getState());
        } else if (i == 5 || i >= 8) {
            CPPUNIT_ASSERT_EQUAL(State::DOWN, ns.getState());
        } else {
            CPPUNIT_ASSERT_EQUAL(State::UP, ns.getState());
        }
            // Test initialize progress
        if (i == 1) {
            CPPUNIT_ASSERT_EQUAL(vespalib::Double(0.0), ns.getInitProgress());
        } else if (i == 3) {
            CPPUNIT_ASSERT_EQUAL(vespalib::Double(0.5), ns.getInitProgress());
        } else {
            CPPUNIT_ASSERT_EQUAL(vespalib::Double(0.0), ns.getInitProgress());
        }
            // Test message
        if (i == 7) {
            CPPUNIT_ASSERT_EQUAL(string("foo bar"), ns.getDescription());
        } else {
            CPPUNIT_ASSERT_EQUAL(string(""), ns.getDescription());
        }
    }

    // Testing storage node states
    for (uint16_t i = 0; i <= 20; ++i) {
        const NodeState& ns(state.getNodeState(Node(NodeType::STORAGE, i)));
            // Test node states
        if (i == 4 || i >= 10) {
            CPPUNIT_ASSERT_EQUAL(State::DOWN, ns.getState());
        } else if (i == 7) {
            CPPUNIT_ASSERT_EQUAL(State::MAINTENANCE, ns.getState());
        } else {
            CPPUNIT_ASSERT_EQUAL(State::UP, ns.getState());
        }
            // Test disk states
        if (i == 2) {
            CPPUNIT_ASSERT_EQUAL(uint16_t(16), ns.getDiskCount());
        } else if (i == 8) {
            CPPUNIT_ASSERT_EQUAL(uint16_t(10), ns.getDiskCount());
        } else {
            CPPUNIT_ASSERT_EQUAL(uint16_t(0), ns.getDiskCount());
        }
        if (i == 2) {
            for (uint16_t j = 0; j < 16; ++j) {
                if (j == 3) {
                    CPPUNIT_ASSERT_EQUAL(State::DOWN,
                                         ns.getDiskState(j).getState());
                } else {
                    CPPUNIT_ASSERT_EQUAL(State::UP,
                                         ns.getDiskState(j).getState());
                }
            }
        } else if (i == 8) {
            for (uint16_t j = 0; j < 10; ++j) {
                if (j == 4) {
                    CPPUNIT_ASSERT_DOUBLES_EQUAL(
                            0.6, ns.getDiskState(j).getCapacity().getValue(), 0.0001);
                    CPPUNIT_ASSERT_EQUAL(
                            string("small"),
                            ns.getDiskState(j).getDescription());
                } else {
                    CPPUNIT_ASSERT_DOUBLES_EQUAL(
                            1.0, ns.getDiskState(j).getCapacity().getValue(), 0.0001);
                    CPPUNIT_ASSERT_EQUAL(
                            string(""),
                            ns.getDiskState(j).getDescription());
                }
            }
        }
            // Test message
        if (i == 6) {
            CPPUNIT_ASSERT_EQUAL(string("bar\tfoo"), ns.getDescription());
        } else {
            CPPUNIT_ASSERT_EQUAL(string(""), ns.getDescription());
        }
            // Test reliability
        if (i == 5) {
            CPPUNIT_ASSERT_EQUAL(uint16_t(4), ns.getReliability());
        } else {
            CPPUNIT_ASSERT_EQUAL(uint16_t(1), ns.getReliability());
        }
            // Test capacity
        if (i == 5) {
            CPPUNIT_ASSERT_EQUAL(vespalib::Double(1.3), ns.getCapacity());
        } else {
            CPPUNIT_ASSERT_EQUAL(vespalib::Double(1.0), ns.getCapacity());
        }
    }

}

void
ClusterStateTest::testParseFailure()
{
    try {
        ClusterState state("storage");
        CPPUNIT_ASSERT(false);
    } catch (vespalib::Exception& e) {
    }

    try {
        ClusterState state("");
    } catch (vespalib::Exception& e) {
        CPPUNIT_ASSERT(false);
    }

    try {
        ClusterState state(".her:tull");
        CPPUNIT_ASSERT(false);
    } catch (vespalib::Exception& e) {
    }
}

void
ClusterStateTest::testParseFailureGroups()
{
    try {
        ClusterState state(")");
        CPPUNIT_ASSERT(false);
    } catch (vespalib::Exception& e) {
    }
}

} // lib
} // storage
