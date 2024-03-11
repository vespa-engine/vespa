// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/messagebus/configagent.h>
#include <vespa/messagebus/iconfighandler.h>
#include <vespa/messagebus/routing/routingspec.h>
#include <vespa/messagebus/config-messagebus.h>
#include <vespa/config/print/fileconfigreader.hpp>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/testkit/test_path.h>

using namespace mbus;
using namespace messagebus;
using namespace config;

class ConfigAgentTest : public testing::Test, public IConfigHandler {
protected:
    RoutingSpec _spec;
    ConfigAgentTest();
    ~ConfigAgentTest() override;
    bool checkHalf();
    bool checkFull();
    void checkTables(uint32_t numTables, bool& success);
    bool setupRouting(RoutingSpec spec) override;
};

ConfigAgentTest::ConfigAgentTest()
    : testing::Test(),
      IConfigHandler(),
      _spec()
{
}

ConfigAgentTest::~ConfigAgentTest() = default;

bool
ConfigAgentTest::setupRouting(RoutingSpec spec)
{
    _spec = std::move(spec);
    return true;
}

void
ConfigAgentTest::checkTables(uint32_t numTables, bool& success)
{
    ASSERT_EQ(numTables, _spec.getNumTables());
    if (numTables > 0) {
        ASSERT_EQ("foo", _spec.getTable(0).getProtocol());
        ASSERT_EQ(2u, _spec.getTable(0).getNumHops());
        ASSERT_EQ("foo-h1", _spec.getTable(0).getHop(0).getName());
        ASSERT_EQ("foo-h1-sel", _spec.getTable(0).getHop(0).getSelector());
        ASSERT_EQ(2u, _spec.getTable(0).getHop(0).getNumRecipients());
        ASSERT_EQ("foo-h1-r1", _spec.getTable(0).getHop(0).getRecipient(0));
        ASSERT_EQ("foo-h1-r2", _spec.getTable(0).getHop(0).getRecipient(1));
        ASSERT_EQ(true, _spec.getTable(0).getHop(0).getIgnoreResult());
        ASSERT_EQ("foo-h2", _spec.getTable(0).getHop(1).getName());
        ASSERT_EQ("foo-h2-sel", _spec.getTable(0).getHop(1).getSelector());
        ASSERT_EQ(2u, _spec.getTable(0).getHop(1).getNumRecipients());
        ASSERT_EQ("foo-h2-r1", _spec.getTable(0).getHop(1).getRecipient(0));
        ASSERT_EQ("foo-h2-r2", _spec.getTable(0).getHop(1).getRecipient(1));
        ASSERT_EQ(2u, _spec.getTable(0).getNumRoutes());
        ASSERT_EQ("foo-r1", _spec.getTable(0).getRoute(0).getName());
        ASSERT_EQ(2u, _spec.getTable(0).getRoute(0).getNumHops());
        ASSERT_EQ("foo-h1", _spec.getTable(0).getRoute(0).getHop(0));
        ASSERT_EQ("foo-h2", _spec.getTable(0).getRoute(0).getHop(1));
        ASSERT_EQ("foo-r2", _spec.getTable(0).getRoute(1).getName());
        ASSERT_EQ(2u, _spec.getTable(0).getRoute(1).getNumHops());
        ASSERT_EQ("foo-h2", _spec.getTable(0).getRoute(1).getHop(0));
        ASSERT_EQ("foo-h1", _spec.getTable(0).getRoute(1).getHop(1));
    }
    if (numTables > 1) {
        ASSERT_EQ("bar", _spec.getTable(1).getProtocol());
        ASSERT_EQ(2u, _spec.getTable(1).getNumHops());
        ASSERT_EQ("bar-h1", _spec.getTable(1).getHop(0).getName());
        ASSERT_EQ("bar-h1-sel", _spec.getTable(1).getHop(0).getSelector());
        ASSERT_EQ(2u, _spec.getTable(1).getHop(0).getNumRecipients());
        ASSERT_EQ("bar-h1-r1", _spec.getTable(1).getHop(0).getRecipient(0));
        ASSERT_EQ("bar-h1-r2", _spec.getTable(1).getHop(0).getRecipient(1));
        ASSERT_EQ("bar-h2", _spec.getTable(1).getHop(1).getName());
        ASSERT_EQ("bar-h2-sel", _spec.getTable(1).getHop(1).getSelector());
        ASSERT_EQ(2u, _spec.getTable(1).getHop(1).getNumRecipients());
        ASSERT_EQ("bar-h2-r1", _spec.getTable(1).getHop(1).getRecipient(0));
        ASSERT_EQ("bar-h2-r2", _spec.getTable(1).getHop(1).getRecipient(1));
        ASSERT_EQ(2u, _spec.getTable(1).getNumRoutes());
        ASSERT_EQ("bar-r1", _spec.getTable(1).getRoute(0).getName());
        ASSERT_EQ(2u, _spec.getTable(1).getRoute(0).getNumHops());
        ASSERT_EQ("bar-h1", _spec.getTable(1).getRoute(0).getHop(0));
        ASSERT_EQ("bar-h2", _spec.getTable(1).getRoute(0).getHop(1));
        ASSERT_EQ("bar-r2", _spec.getTable(1).getRoute(1).getName());
        ASSERT_EQ(2u, _spec.getTable(1).getRoute(1).getNumHops());
        ASSERT_EQ("bar-h2", _spec.getTable(1).getRoute(1).getHop(0));
        ASSERT_EQ("bar-h1", _spec.getTable(1).getRoute(1).getHop(1));
    }
    success = true;
}

bool
ConfigAgentTest::checkHalf()
{
    bool success = false;
    return _spec.getNumTables() == 1 && (checkTables(1, success), success);
}

bool
ConfigAgentTest::checkFull()
{
    bool success = false;
    return _spec.getNumTables() == 2 && (checkTables(2, success), success);
}

TEST_F(ConfigAgentTest, test_config_agent)
{
    EXPECT_TRUE(!checkHalf());
    EXPECT_TRUE(!checkFull());
    ConfigAgent agent(*this);
    EXPECT_TRUE(!checkHalf());
    EXPECT_TRUE(!checkFull());
    agent.configure(FileConfigReader<MessagebusConfig>(TEST_PATH("full.cfg")).read());
    EXPECT_TRUE(!checkHalf());
    EXPECT_TRUE(checkFull());
    agent.configure(FileConfigReader<MessagebusConfig>(TEST_PATH("half.cfg")).read());
    EXPECT_TRUE(checkHalf());
    EXPECT_TRUE(!checkFull());
    agent.configure(FileConfigReader<MessagebusConfig>(TEST_PATH("full.cfg")).read());
    EXPECT_TRUE(checkFull());
    EXPECT_TRUE(!checkHalf());
}

GTEST_MAIN_RUN_ALL_TESTS()
