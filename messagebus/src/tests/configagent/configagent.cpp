// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/messagebus/configagent.h>
#include <vespa/messagebus/iconfighandler.h>
#include <vespa/messagebus/routing/routingspec.h>
#include <vespa/messagebus/config-messagebus.h>
#include <vespa/config/print/fileconfigreader.hpp>

using namespace mbus;
using namespace messagebus;
using namespace config;

class Test : public vespalib::TestApp, public IConfigHandler {
private:
    RoutingSpec _spec;
    bool checkHalf();
    bool checkFull();
    bool checkTables(uint32_t numTables);

public:
    ~Test() {}
    int Main() override;
    bool setupRouting(const RoutingSpec &spec) override;
};

TEST_APPHOOK(Test);

bool
Test::setupRouting(const RoutingSpec &spec)
{
    _spec = spec;
    return true;
}

bool
Test::checkTables(uint32_t numTables)
{
    if (!EXPECT_EQUAL(numTables, _spec.getNumTables())) return false;
    if (numTables > 0) {
        if (!EXPECT_EQUAL("foo", _spec.getTable(0).getProtocol())) return false;
        if (!EXPECT_EQUAL(2u, _spec.getTable(0).getNumHops())) return false;
        if (!EXPECT_EQUAL("foo-h1", _spec.getTable(0).getHop(0).getName())) return false;
        if (!EXPECT_EQUAL("foo-h1-sel", _spec.getTable(0).getHop(0).getSelector())) return false;
        if (!EXPECT_EQUAL(2u, _spec.getTable(0).getHop(0).getNumRecipients())) return false;
        if (!EXPECT_EQUAL("foo-h1-r1", _spec.getTable(0).getHop(0).getRecipient(0))) return false;
        if (!EXPECT_EQUAL("foo-h1-r2", _spec.getTable(0).getHop(0).getRecipient(1))) return false;
        if (!EXPECT_EQUAL(true, _spec.getTable(0).getHop(0).getIgnoreResult())) return false;
        if (!EXPECT_EQUAL("foo-h2", _spec.getTable(0).getHop(1).getName())) return false;
        if (!EXPECT_EQUAL("foo-h2-sel", _spec.getTable(0).getHop(1).getSelector())) return false;
        if (!EXPECT_EQUAL(2u, _spec.getTable(0).getHop(1).getNumRecipients())) return false;
        if (!EXPECT_EQUAL("foo-h2-r1", _spec.getTable(0).getHop(1).getRecipient(0))) return false;
        if (!EXPECT_EQUAL("foo-h2-r2", _spec.getTable(0).getHop(1).getRecipient(1))) return false;
        if (!EXPECT_EQUAL(2u, _spec.getTable(0).getNumRoutes())) return false;
        if (!EXPECT_EQUAL("foo-r1", _spec.getTable(0).getRoute(0).getName())) return false;
        if (!EXPECT_EQUAL(2u, _spec.getTable(0).getRoute(0).getNumHops())) return false;
        if (!EXPECT_EQUAL("foo-h1", _spec.getTable(0).getRoute(0).getHop(0))) return false;
        if (!EXPECT_EQUAL("foo-h2", _spec.getTable(0).getRoute(0).getHop(1))) return false;
        if (!EXPECT_EQUAL("foo-r2", _spec.getTable(0).getRoute(1).getName())) return false;
        if (!EXPECT_EQUAL(2u, _spec.getTable(0).getRoute(1).getNumHops())) return false;
        if (!EXPECT_EQUAL("foo-h2", _spec.getTable(0).getRoute(1).getHop(0))) return false;
        if (!EXPECT_EQUAL("foo-h1", _spec.getTable(0).getRoute(1).getHop(1))) return false;
    }
    if (numTables > 1) {
        if (!EXPECT_EQUAL("bar", _spec.getTable(1).getProtocol())) return false;
        if (!EXPECT_EQUAL(2u, _spec.getTable(1).getNumHops())) return false;
        if (!EXPECT_EQUAL("bar-h1", _spec.getTable(1).getHop(0).getName())) return false;
        if (!EXPECT_EQUAL("bar-h1-sel", _spec.getTable(1).getHop(0).getSelector())) return false;
        if (!EXPECT_EQUAL(2u, _spec.getTable(1).getHop(0).getNumRecipients())) return false;
        if (!EXPECT_EQUAL("bar-h1-r1", _spec.getTable(1).getHop(0).getRecipient(0))) return false;
        if (!EXPECT_EQUAL("bar-h1-r2", _spec.getTable(1).getHop(0).getRecipient(1))) return false;
        if (!EXPECT_EQUAL("bar-h2", _spec.getTable(1).getHop(1).getName())) return false;
        if (!EXPECT_EQUAL("bar-h2-sel", _spec.getTable(1).getHop(1).getSelector())) return false;
        if (!EXPECT_EQUAL(2u, _spec.getTable(1).getHop(1).getNumRecipients())) return false;
        if (!EXPECT_EQUAL("bar-h2-r1", _spec.getTable(1).getHop(1).getRecipient(0))) return false;
        if (!EXPECT_EQUAL("bar-h2-r2", _spec.getTable(1).getHop(1).getRecipient(1))) return false;
        if (!EXPECT_EQUAL(2u, _spec.getTable(1).getNumRoutes())) return false;
        if (!EXPECT_EQUAL("bar-r1", _spec.getTable(1).getRoute(0).getName())) return false;
        if (!EXPECT_EQUAL(2u, _spec.getTable(1).getRoute(0).getNumHops())) return false;
        if (!EXPECT_EQUAL("bar-h1", _spec.getTable(1).getRoute(0).getHop(0))) return false;
        if (!EXPECT_EQUAL("bar-h2", _spec.getTable(1).getRoute(0).getHop(1))) return false;
        if (!EXPECT_EQUAL("bar-r2", _spec.getTable(1).getRoute(1).getName())) return false;
        if (!EXPECT_EQUAL(2u, _spec.getTable(1).getRoute(1).getNumHops())) return false;
        if (!EXPECT_EQUAL("bar-h2", _spec.getTable(1).getRoute(1).getHop(0))) return false;
        if (!EXPECT_EQUAL("bar-h1", _spec.getTable(1).getRoute(1).getHop(1))) return false;
    }
    return true;
}

bool
Test::checkHalf()
{
    return _spec.getNumTables() == 1 && EXPECT_TRUE(checkTables(1));
}

bool
Test::checkFull()
{
    return _spec.getNumTables() == 2 && EXPECT_TRUE(checkTables(2));
}

int
Test::Main()
{
    TEST_INIT("configagent_test");
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
    TEST_DONE();
}
