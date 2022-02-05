// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/configagent.h>
#include <vespa/messagebus/iconfighandler.h>
#include <vespa/messagebus/routing/routingspec.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/messagebus/config-messagebus.h>
#include <vespa/config/helper/configgetter.hpp>


using namespace mbus;
using namespace messagebus;
using namespace config;

class ConfigStore : public IConfigHandler {
private:
    RoutingSpec _routing;

public:
    ConfigStore() : _routing() {
        // empty
    }

    bool setupRouting(const RoutingSpec &spec) override {
        _routing = spec;
        return true;
    }

    const RoutingSpec &getRoutingSpec() {
        return _routing;
    }
};

class Test : public vespalib::TestApp {
private:
    bool testRouting(const RoutingSpec &spec);
    bool testConfig(const RoutingSpec &spec);

public:
    void testConstructors();
    void testConfigGeneration();
    int Main() override;
};

TEST_APPHOOK(Test);

int
Test::Main()
{
    TEST_INIT("routingspec_test");

    testConstructors();     TEST_FLUSH();
    testConfigGeneration(); TEST_FLUSH();

    TEST_DONE();
}

void
Test::testConstructors()
{
    {
        RoutingSpec spec;
        spec.addTable(RoutingTableSpec("foo"));
        spec.getTable(0).addHop(HopSpec("foo-h1", "foo-h1-sel"));
        spec.getTable(0).getHop(0).addRecipient("foo-h1-r1");
        spec.getTable(0).getHop(0).addRecipient("foo-h1-r2");
        spec.getTable(0).addHop(HopSpec("foo-h2", "foo-h2-sel"));
        spec.getTable(0).getHop(1).addRecipient("foo-h2-r1");
        spec.getTable(0).getHop(1).addRecipient("foo-h2-r2");
        spec.getTable(0).addRoute(RouteSpec("foo-r1"));
        spec.getTable(0).getRoute(0).addHop("foo-h1");
        spec.getTable(0).getRoute(0).addHop("foo-h2");
        spec.getTable(0).addRoute(RouteSpec("foo-r2"));
        spec.getTable(0).getRoute(1).addHop("foo-h2");
        spec.getTable(0).getRoute(1).addHop("foo-h1");
        spec.addTable(RoutingTableSpec("bar"));
        spec.getTable(1).addHop(HopSpec("bar-h1", "bar-h1-sel"));
        spec.getTable(1).getHop(0).addRecipient("bar-h1-r1");
        spec.getTable(1).getHop(0).addRecipient("bar-h1-r2");
        spec.getTable(1).addHop(HopSpec("bar-h2", "bar-h2-sel"));
        spec.getTable(1).getHop(1).addRecipient("bar-h2-r1");
        spec.getTable(1).getHop(1).addRecipient("bar-h2-r2");
        spec.getTable(1).addRoute(RouteSpec("bar-r1"));
        spec.getTable(1).getRoute(0).addHop("bar-h1");
        spec.getTable(1).getRoute(0).addHop("bar-h2");
        spec.getTable(1).addRoute(RouteSpec("bar-r2"));
        spec.getTable(1).getRoute(1).addHop("bar-h2");
        spec.getTable(1).getRoute(1).addHop("bar-h1");
        EXPECT_TRUE(testRouting(spec));

        RoutingSpec specCopy = spec;
        EXPECT_TRUE(testRouting(specCopy));
    }
    {
        RoutingSpec spec = RoutingSpec()
            .addTable(RoutingTableSpec("foo")
                      .addHop(HopSpec("foo-h1", "foo-h1-sel")
                              .addRecipient("foo-h1-r1")
                              .addRecipient("foo-h1-r2"))
                      .addHop(HopSpec("foo-h2", "foo-h2-sel")
                              .addRecipient("foo-h2-r1")
                              .addRecipient("foo-h2-r2"))
                      .addRoute(RouteSpec("foo-r1")
                                .addHop("foo-h1")
                                .addHop("foo-h2"))
                      .addRoute(RouteSpec("foo-r2")
                                .addHop("foo-h2")
                                .addHop("foo-h1")))
            .addTable(RoutingTableSpec("bar")
                      .addHop(HopSpec("bar-h1", "bar-h1-sel")
                              .addRecipient("bar-h1-r1")
                              .addRecipient("bar-h1-r2"))
                      .addHop(HopSpec("bar-h2", "bar-h2-sel")
                              .addRecipient("bar-h2-r1")
                              .addRecipient("bar-h2-r2"))
                      .addRoute(RouteSpec("bar-r1")
                                .addHop("bar-h1")
                                .addHop("bar-h2"))
                      .addRoute(RouteSpec("bar-r2")
                                .addHop("bar-h2")
                                .addHop("bar-h1")));
        EXPECT_TRUE(testRouting(spec));

        RoutingSpec specCopy = spec;
        EXPECT_TRUE(testRouting(specCopy));
    }
}

bool
Test::testRouting(const RoutingSpec &spec)
{
    if (!ASSERT_TRUE(spec.getNumTables() == 2)) { return false; }
    if (!EXPECT_TRUE(spec.getTable(0).getProtocol() == "foo")) { return false; }
    if (!ASSERT_TRUE(spec.getTable(0).getNumHops() == 2)) { return false; }
    if (!EXPECT_TRUE(spec.getTable(0).getHop(0).getName() == "foo-h1")) { return false; }
    if (!EXPECT_TRUE(spec.getTable(0).getHop(0).getSelector() == "foo-h1-sel")) { return false; }
    if (!ASSERT_TRUE(spec.getTable(0).getHop(0).getNumRecipients() == 2)) { return false; }
    if (!EXPECT_TRUE(spec.getTable(0).getHop(0).getRecipient(0) == "foo-h1-r1")) { return false; }
    if (!EXPECT_TRUE(spec.getTable(0).getHop(0).getRecipient(1) == "foo-h1-r2")) { return false; }
    if (!EXPECT_TRUE(spec.getTable(0).getHop(1).getName() == "foo-h2")) { return false; }
    if (!EXPECT_TRUE(spec.getTable(0).getHop(1).getSelector() == "foo-h2-sel")) { return false; }
    if (!ASSERT_TRUE(spec.getTable(0).getHop(1).getNumRecipients() == 2)) { return false; }
    if (!EXPECT_TRUE(spec.getTable(0).getHop(1).getRecipient(0) == "foo-h2-r1")) { return false; }
    if (!EXPECT_TRUE(spec.getTable(0).getHop(1).getRecipient(1) == "foo-h2-r2")) { return false; }
    if (!ASSERT_TRUE(spec.getTable(0).getNumRoutes() == 2)) { return false; }
    if (!EXPECT_TRUE(spec.getTable(0).getRoute(0).getName() == "foo-r1")) { return false; }
    if (!ASSERT_TRUE(spec.getTable(0).getRoute(0).getNumHops() == 2)) { return false; }
    if (!EXPECT_TRUE(spec.getTable(0).getRoute(0).getHop(0) == "foo-h1")) { return false; }
    if (!EXPECT_TRUE(spec.getTable(0).getRoute(0).getHop(1) == "foo-h2")) { return false; }
    if (!EXPECT_TRUE(spec.getTable(0).getRoute(1).getName() == "foo-r2")) { return false; }
    if (!ASSERT_TRUE(spec.getTable(0).getRoute(1).getNumHops() == 2)) { return false; }
    if (!EXPECT_TRUE(spec.getTable(0).getRoute(1).getHop(0) == "foo-h2")) { return false; }
    if (!EXPECT_TRUE(spec.getTable(0).getRoute(1).getHop(1) == "foo-h1")) { return false; }
    if (!EXPECT_TRUE(spec.getTable(1).getProtocol() == "bar")) { return false; }
    if (!ASSERT_TRUE(spec.getTable(1).getNumHops() == 2)) { return false; }
    if (!EXPECT_TRUE(spec.getTable(1).getHop(0).getName() == "bar-h1")) { return false; }
    if (!EXPECT_TRUE(spec.getTable(1).getHop(0).getSelector() == "bar-h1-sel")) { return false; }
    if (!ASSERT_TRUE(spec.getTable(1).getHop(0).getNumRecipients() == 2)) { return false; }
    if (!EXPECT_TRUE(spec.getTable(1).getHop(0).getRecipient(0) == "bar-h1-r1")) { return false; }
    if (!EXPECT_TRUE(spec.getTable(1).getHop(0).getRecipient(1) == "bar-h1-r2")) { return false; }
    if (!EXPECT_TRUE(spec.getTable(1).getHop(1).getName() == "bar-h2")) { return false; }
    if (!EXPECT_TRUE(spec.getTable(1).getHop(1).getSelector() == "bar-h2-sel")) { return false; }
    if (!ASSERT_TRUE(spec.getTable(1).getHop(1).getNumRecipients() == 2)) { return false; }
    if (!EXPECT_TRUE(spec.getTable(1).getHop(1).getRecipient(0) == "bar-h2-r1")) { return false; }
    if (!EXPECT_TRUE(spec.getTable(1).getHop(1).getRecipient(1) == "bar-h2-r2")) { return false; }
    if (!ASSERT_TRUE(spec.getTable(1).getNumRoutes() == 2)) { return false; }
    if (!EXPECT_TRUE(spec.getTable(1).getRoute(0).getName() == "bar-r1")) { return false; }
    if (!ASSERT_TRUE(spec.getTable(1).getRoute(0).getNumHops() == 2)) { return false; }
    if (!EXPECT_TRUE(spec.getTable(1).getRoute(0).getHop(0) == "bar-h1")) { return false; }
    if (!EXPECT_TRUE(spec.getTable(1).getRoute(0).getHop(1) == "bar-h2")) { return false; }
    if (!EXPECT_TRUE(spec.getTable(1).getRoute(1).getName() == "bar-r2")) { return false; }
    if (!ASSERT_TRUE(spec.getTable(1).getRoute(1).getNumHops() == 2)) { return false; }
    if (!EXPECT_TRUE(spec.getTable(1).getRoute(1).getHop(0) == "bar-h2")) { return false; }
    if (!EXPECT_TRUE(spec.getTable(1).getRoute(1).getHop(1) == "bar-h1")) { return false; }
    return true;
}

void
Test::testConfigGeneration()
{
    EXPECT_TRUE(testConfig(RoutingSpec()));
    EXPECT_TRUE(testConfig(RoutingSpec().addTable(RoutingTableSpec("mytable1"))));
    EXPECT_TRUE(testConfig(RoutingSpec().addTable(RoutingTableSpec("mytable1")
                                                 .addHop(HopSpec("myhop1", "myselector1")))));
    EXPECT_TRUE(testConfig(RoutingSpec().addTable(RoutingTableSpec("mytable1")
                                                 .addHop(HopSpec("myhop1", "myselector1"))
                                                 .addRoute(RouteSpec("myroute1").addHop("myhop1")))));
    EXPECT_TRUE(testConfig(RoutingSpec().addTable(RoutingTableSpec("mytable1")
                                                 .addHop(HopSpec("myhop1", "myselector1"))
                                                 .addHop(HopSpec("myhop2", "myselector2"))
                                                 .addRoute(RouteSpec("myroute1").addHop("myhop1"))
                                                 .addRoute(RouteSpec("myroute2").addHop("myhop2"))
                                                 .addRoute(RouteSpec("myroute12").addHop("myhop1").addHop("myhop2")))));
    EXPECT_TRUE(testConfig(RoutingSpec()
                          .addTable(RoutingTableSpec("mytable1")
                                    .addHop(HopSpec("myhop1", "myselector1"))
                                    .addHop(HopSpec("myhop2", "myselector2"))
                                    .addRoute(RouteSpec("myroute1").addHop("myhop1"))
                                    .addRoute(RouteSpec("myroute2").addHop("myhop2"))
                                    .addRoute(RouteSpec("myroute12").addHop("myhop1").addHop("myhop2")))
                          .addTable(RoutingTableSpec("mytable2"))));

    EXPECT_EQUAL("routingtable[2]\n"
               "routingtable[0].protocol \"mytable1\"\n"
               "routingtable[1].protocol \"mytable2\"\n"
               "routingtable[1].hop[3]\n"
               "routingtable[1].hop[0].name \"myhop1\"\n"
               "routingtable[1].hop[0].selector \"myselector1\"\n"
               "routingtable[1].hop[1].name \"myhop2\"\n"
               "routingtable[1].hop[1].selector \"myselector2\"\n"
               "routingtable[1].hop[1].ignoreresult true\n"
               "routingtable[1].hop[2].name \"myhop1\"\n"
               "routingtable[1].hop[2].selector \"myselector3\"\n"
               "routingtable[1].hop[2].recipient[2]\n"
               "routingtable[1].hop[2].recipient[0] \"myrecipient1\"\n"
               "routingtable[1].hop[2].recipient[1] \"myrecipient2\"\n"
               "routingtable[1].route[1]\n"
               "routingtable[1].route[0].name \"myroute1\"\n"
               "routingtable[1].route[0].hop[1]\n"
               "routingtable[1].route[0].hop[0] \"myhop1\"\n",
               RoutingSpec()
               .addTable(RoutingTableSpec("mytable1"))
               .addTable(RoutingTableSpec("mytable2")
                         .addHop(HopSpec("myhop1", "myselector1"))
                         .addHop(HopSpec("myhop2", "myselector2").setIgnoreResult(true))
                         .addHop(HopSpec("myhop1", "myselector3")
                                 .addRecipient("myrecipient1")
                                 .addRecipient("myrecipient2"))
                         .addRoute(RouteSpec("myroute1").addHop("myhop1"))).toString());
}

bool
Test::testConfig(const RoutingSpec &spec)
{
    if (!EXPECT_TRUE(spec == spec)) {
        return false;
    }
    if (!EXPECT_TRUE(spec == RoutingSpec(spec))) {
        return false;
    }
    ConfigStore store;
    ConfigAgent agent(store);
    agent.configure(ConfigGetter<MessagebusConfig>().getConfig("", RawSpec(spec.toString())));
    if (!EXPECT_TRUE(store.getRoutingSpec() == spec)) {
        return false;
    }
    return true;
}

