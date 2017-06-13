// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdslib/state/nodestate.h>
#include <cppunit/extensions/HelperMacros.h>

namespace storage {
namespace lib {

class NodeStateTest : public CppUnit::TestFixture {
    CPPUNIT_TEST_SUITE(NodeStateTest);
    CPPUNIT_TEST(testParsing);
    CPPUNIT_TEST(testExponential);
    CPPUNIT_TEST(stateInstancesProvideDescriptiveNames);
    CPPUNIT_TEST_SUITE_END();

public:
protected:
    void testParsing();
    void testExponential(); // Test exponential notation.
    void stateInstancesProvideDescriptiveNames();
};

CPPUNIT_TEST_SUITE_REGISTRATION( NodeStateTest );

void
NodeStateTest::testParsing()
{
    {
        NodeState ns = NodeState("s:u");
        CPPUNIT_ASSERT_EQUAL(std::string("s:u"), ns.toString());
        CPPUNIT_ASSERT_EQUAL(vespalib::Double(1.0), ns.getCapacity());
        CPPUNIT_ASSERT_EQUAL(uint16_t(1), ns.getReliability());
    }
    {
        NodeState ns = NodeState("s:m");
        CPPUNIT_ASSERT_EQUAL(std::string("s:m"), ns.toString());
        CPPUNIT_ASSERT_EQUAL(vespalib::Double(1.0), ns.getCapacity());
        CPPUNIT_ASSERT_EQUAL(uint16_t(1), ns.getReliability());
    }
    {
        NodeState ns = NodeState("t:4");
        CPPUNIT_ASSERT_EQUAL(std::string("s:u t:4"), ns.toString());
        CPPUNIT_ASSERT_EQUAL(uint64_t(4), ns.getStartTimestamp());
    }
    {
        NodeState ns = NodeState("s:u c:2.4 r:3 b:12");
        CPPUNIT_ASSERT_EQUAL(std::string("s:u c:2.4 r:3 b:12"), ns.toString());
        CPPUNIT_ASSERT_EQUAL(vespalib::Double(2.4), ns.getCapacity());
        CPPUNIT_ASSERT_EQUAL(uint16_t(3), ns.getReliability());
        CPPUNIT_ASSERT_EQUAL(12, (int)ns.getMinUsedBits());

        CPPUNIT_ASSERT(!(NodeState("s:u b:12") == NodeState("s:u b:13")));
    }
    {
        NodeState ns = NodeState("c:2.4\ns:u\nr:5");
        CPPUNIT_ASSERT_EQUAL(std::string("s:u c:2.4 r:5"), ns.toString());
        CPPUNIT_ASSERT_EQUAL(vespalib::Double(2.4), ns.getCapacity());
        CPPUNIT_ASSERT_EQUAL(uint16_t(5), ns.getReliability());
    }
    {
        NodeState ns = NodeState("c:2.4 r:1");
        CPPUNIT_ASSERT_EQUAL(std::string("s:u c:2.4"), ns.toString());
        CPPUNIT_ASSERT_EQUAL(vespalib::Double(2.4), ns.getCapacity());
        CPPUNIT_ASSERT_EQUAL(uint16_t(1), ns.getReliability());
    }
    {
        NodeState ns = NodeState("c:2.4 k:2.6");
        CPPUNIT_ASSERT_EQUAL(std::string("s:u c:2.4"), ns.toString());
        CPPUNIT_ASSERT_EQUAL(vespalib::Double(2.4), ns.getCapacity());
        CPPUNIT_ASSERT_EQUAL(uint16_t(1), ns.getReliability());
    }
}

void
NodeStateTest::testExponential()
{
    {
        NodeState ns = NodeState("c:3E-8");
        CPPUNIT_ASSERT_EQUAL( std::string("s:u c:3e-08"), ns.toString() );
        CPPUNIT_ASSERT_EQUAL(vespalib::Double(3E-8), ns.getCapacity());
    }
    {
        NodeState ns = NodeState("c:3e-08");
        CPPUNIT_ASSERT_EQUAL( std::string("s:u c:3e-08"), ns.toString() );
        CPPUNIT_ASSERT_EQUAL(vespalib::Double(3e-08), ns.getCapacity());
    }
}

void
NodeStateTest::stateInstancesProvideDescriptiveNames()
{
    CPPUNIT_ASSERT_EQUAL(vespalib::string("Unknown"),
                         State::UNKNOWN.getName());
    CPPUNIT_ASSERT_EQUAL(vespalib::string("Maintenance"),
                         State::MAINTENANCE.getName());
    CPPUNIT_ASSERT_EQUAL(vespalib::string("Down"),
                         State::DOWN.getName());
    CPPUNIT_ASSERT_EQUAL(vespalib::string("Stopping"),
                         State::STOPPING.getName());
    CPPUNIT_ASSERT_EQUAL(vespalib::string("Initializing"),
                         State::INITIALIZING.getName());
    CPPUNIT_ASSERT_EQUAL(vespalib::string("Retired"),
                         State::RETIRED.getName());
    CPPUNIT_ASSERT_EQUAL(vespalib::string("Up"),
                         State::UP.getName());
}

} // lib
} // storage
