// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdslib/state/nodestate.h>
#include <vespa/vespalib/gtest/gtest.h>

namespace storage::lib {

TEST(NodeStateTest, test_parsing)
{
    {
        NodeState ns = NodeState("s:u");
        EXPECT_EQ(std::string("s:u"), ns.toString());
        EXPECT_EQ(vespalib::Double(1.0), ns.getCapacity());
    }
    {
        NodeState ns = NodeState("s:m");
        EXPECT_EQ(std::string("s:m"), ns.toString());
        EXPECT_EQ(vespalib::Double(1.0), ns.getCapacity());
    }
    {
        NodeState ns = NodeState("t:4");
        EXPECT_EQ(std::string("s:u t:4"), ns.toString());
        EXPECT_EQ(4u, ns.getStartTimestamp());
    }
    {
        NodeState ns = NodeState("s:u c:2.4 b:12");
        EXPECT_EQ(std::string("s:u c:2.4 b:12"), ns.toString());
        EXPECT_EQ(vespalib::Double(2.4), ns.getCapacity());
        EXPECT_EQ(12, (int)ns.getMinUsedBits());

        EXPECT_NE(NodeState("s:u b:12"), NodeState("s:u b:13"));
    }
    {
        NodeState ns = NodeState("c:2.4\ns:u");
        EXPECT_EQ(std::string("s:u c:2.4"), ns.toString());
        EXPECT_EQ(vespalib::Double(2.4), ns.getCapacity());
    }
    {
        NodeState ns = NodeState("c:2.4");
        EXPECT_EQ(std::string("s:u c:2.4"), ns.toString());
        EXPECT_EQ(vespalib::Double(2.4), ns.getCapacity());
    }
    {
        NodeState ns = NodeState("c:2.4 k:2.6");
        EXPECT_EQ(std::string("s:u c:2.4"), ns.toString());
        EXPECT_EQ(vespalib::Double(2.4), ns.getCapacity());
    }
}

TEST(NodeStateTest, test_exponential)
{
    {
        NodeState ns = NodeState("c:3E-8");
        EXPECT_EQ(std::string("s:u c:3e-08"), ns.toString() );
        EXPECT_EQ(vespalib::Double(3E-8), ns.getCapacity());
    }
    {
        NodeState ns = NodeState("c:3e-08");
        EXPECT_EQ(std::string("s:u c:3e-08"), ns.toString() );
        EXPECT_EQ(vespalib::Double(3e-08), ns.getCapacity());
    }
}

TEST(NodeStateTest, state_instances_provide_descriptive_names)
{
    EXPECT_EQ(vespalib::string("Unknown"),
              State::UNKNOWN.getName());
    EXPECT_EQ(vespalib::string("Maintenance"),
              State::MAINTENANCE.getName());
    EXPECT_EQ(vespalib::string("Down"),
              State::DOWN.getName());
    EXPECT_EQ(vespalib::string("Stopping"),
              State::STOPPING.getName());
    EXPECT_EQ(vespalib::string("Initializing"),
              State::INITIALIZING.getName());
    EXPECT_EQ(vespalib::string("Retired"),
              State::RETIRED.getName());
    EXPECT_EQ(vespalib::string("Up"),
              State::UP.getName());
}

}
