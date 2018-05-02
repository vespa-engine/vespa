// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.routing;

import com.yahoo.messagebus.ConfigAgent;
import com.yahoo.messagebus.ConfigHandler;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen
 */
public class RoutingSpecTestCase {

    @Test
    public void testConfig() {
        assertConfig(new RoutingSpec());
        assertConfig(new RoutingSpec().addTable(new RoutingTableSpec("mytable1")));
        assertConfig(new RoutingSpec().addTable(new RoutingTableSpec("mytable1")
                .addHop(new HopSpec("myhop1", "myselector1"))));
        assertConfig(new RoutingSpec().addTable(new RoutingTableSpec("mytable1")
                .addHop(new HopSpec("myhop1", "myselector1"))
                .addRoute(new RouteSpec("myroute1").addHop("myhop1"))));
        assertConfig(new RoutingSpec().addTable(new RoutingTableSpec("mytable1")
                .addHop(new HopSpec("myhop1", "myselector1"))
                .addHop(new HopSpec("myhop2", "myselector2"))
                .addRoute(new RouteSpec("myroute1").addHop("myhop1"))
                .addRoute(new RouteSpec("myroute2").addHop("myhop2"))
                .addRoute(new RouteSpec("myroute12").addHop("myhop1").addHop("myhop2"))));
        assertConfig(new RoutingSpec().addTable(new RoutingTableSpec("mytable1")
                .addHop(new HopSpec("myhop1", "myselector1"))
                .addHop(new HopSpec("myhop2", "myselector2"))
                .addRoute(new RouteSpec("myroute1").addHop("myhop1"))
                .addRoute(new RouteSpec("myroute2").addHop("myhop2"))
                .addRoute(new RouteSpec("myroute12").addHop("myhop1").addHop("myhop2")))
                .addTable(new RoutingTableSpec("mytable2")));
        assertEquals("routingtable[2]\n" +
                     "routingtable[0].protocol \"mytable1\"\n" +
                     "routingtable[1].protocol \"mytable2\"\n" +
                     "routingtable[1].hop[3]\n" +
                     "routingtable[1].hop[0].name \"myhop1\"\n" +
                     "routingtable[1].hop[0].selector \"myselector1\"\n" +
                     "routingtable[1].hop[1].name \"myhop2\"\n" +
                     "routingtable[1].hop[1].selector \"myselector2\"\n" +
                     "routingtable[1].hop[1].ignoreresult true\n" +
                     "routingtable[1].hop[2].name \"myhop1\"\n" +
                     "routingtable[1].hop[2].selector \"myselector3\"\n" +
                     "routingtable[1].hop[2].recipient[2]\n" +
                     "routingtable[1].hop[2].recipient[0] \"myrecipient1\"\n" +
                     "routingtable[1].hop[2].recipient[1] \"myrecipient2\"\n" +
                     "routingtable[1].route[1]\n" +
                     "routingtable[1].route[0].name \"myroute1\"\n" +
                     "routingtable[1].route[0].hop[1]\n" +
                     "routingtable[1].route[0].hop[0] \"myhop1\"\n",
                     new RoutingSpec()
                             .addTable(new RoutingTableSpec("mytable1"))
                             .addTable(new RoutingTableSpec("mytable2")
                                     .addHop(new HopSpec("myhop1", "myselector1"))
                                     .addHop(new HopSpec("myhop2", "myselector2").setIgnoreResult(true))
                                     .addHop(new HopSpec("myhop1", "myselector3")
                                             .addRecipient("myrecipient1")
                                             .addRecipient("myrecipient2"))
                                     .addRoute(new RouteSpec("myroute1").addHop("myhop1"))).toString());
    }

    @Test
    public void testApplicationSpec() {
        assertApplicationSpec(Arrays.asList("foo"),
                              Arrays.asList("foo",
                                            "*"));
        assertApplicationSpec(Arrays.asList("foo/bar"),
                              Arrays.asList("foo/bar",
                                            "foo/*",
                                            "*/bar",
                                            "*/*"));
        assertApplicationSpec(Arrays.asList("foo/0/baz",
                                            "foo/1/baz",
                                            "foo/2/baz"),
                              Arrays.asList("foo/0/baz",
                                            "foo/1/baz",
                                            "foo/2/baz",
                                            "foo/0/*",
                                            "foo/1/*",
                                            "foo/2/*",
                                            "foo/*/baz",
                                            "*/0/baz",
                                            "*/1/baz",
                                            "*/2/baz",
                                            "foo/*/*",
                                            "*/0/*",
                                            "*/1/*",
                                            "*/2/*",
                                            "*/*/baz",
                                            "*/*/*"));
    }

    @Test
    public void testVeriyfOk() {
        assertVerifyOk(new RoutingSpec().addTable(new RoutingTableSpec("mytable")
                .addHop(new HopSpec("hop1", "myservice1"))),
                       new ApplicationSpec().addService("mytable", "myservice1"));
        assertVerifyOk(new RoutingSpec().addTable(new RoutingTableSpec("mytable")
                .addRoute(new RouteSpec("route1").addHop("myservice1"))),
                       new ApplicationSpec().addService("mytable", "myservice1"));
        assertVerifyOk(new RoutingSpec().addTable(new RoutingTableSpec("mytable")
                .addHop(new HopSpec("hop1", "myservice1"))
                .addRoute(new RouteSpec("route1").addHop("hop1"))),
                       new ApplicationSpec().addService("mytable", "myservice1"));
        assertVerifyOk(new RoutingSpec().addTable(new RoutingTableSpec("mytable")
                .addHop(new HopSpec("hop1", "route:route2"))
                .addHop(new HopSpec("hop2", "myservice1"))
                .addRoute(new RouteSpec("route1").addHop("hop1"))
                .addRoute(new RouteSpec("route2").addHop("hop2"))),
                       new ApplicationSpec().addService("mytable", "myservice1"));
        assertVerifyOk(new RoutingSpec().addTable(new RoutingTableSpec("mytable")
                .addHop(new HopSpec("myhop1", "foo/[bar]/baz").addRecipient("foo/0/baz").addRecipient("foo/1/baz"))),
                       new ApplicationSpec()
                               .addService("mytable", "foo/0/baz")
                               .addService("mytable", "foo/1/baz"));
    }

    @Test
    public void testVerifyToggle() {
        assertVerifyOk(new RoutingSpec(false)
                .addTable(new RoutingTableSpec("mytable"))
                .addTable(new RoutingTableSpec("mytable")),
                       new ApplicationSpec());
        assertVerifyOk(new RoutingSpec().addTable(new RoutingTableSpec("mytable", false)
                .addHop(new HopSpec("foo", "bar"))
                .addHop(new HopSpec("foo", "baz"))),
                       new ApplicationSpec());
        assertVerifyOk(new RoutingSpec().addTable(new RoutingTableSpec("mytable")
                .addHop(new HopSpec("foo", "", false))),
                       new ApplicationSpec());
        assertVerifyOk(new RoutingSpec().addTable(new RoutingTableSpec("mytable")
                .addRoute(new RouteSpec("foo", false))),
                         new ApplicationSpec());
    }

    @Test
    public void testVerifyFail() {
        // Duplicate table.
        assertVerifyFail(new RoutingSpec()
                .addTable(new RoutingTableSpec("mytable"))
                .addTable(new RoutingTableSpec("mytable")),
                         new ApplicationSpec(),
                         Arrays.asList("Routing table 'mytable' is defined 2 times."));

        // Duplicate hop.
        assertVerifyFail(new RoutingSpec().addTable(new RoutingTableSpec("mytable")
                .addHop(new HopSpec("foo", "bar"))
                .addHop(new HopSpec("foo", "baz"))),
                         new ApplicationSpec()
                                 .addService("mytable", "bar")
                                 .addService("mytable", "baz"),
                         Arrays.asList("Hop 'foo' in routing table 'mytable' is defined 2 times."));

        // Duplicate route.
        assertVerifyFail(new RoutingSpec().addTable(new RoutingTableSpec("mytable")
                .addRoute(new RouteSpec("foo").addHop("bar"))
                .addRoute(new RouteSpec("foo").addHop("baz"))),
                         new ApplicationSpec()
                                 .addService("mytable", "bar")
                                 .addService("mytable", "baz"),
                         Arrays.asList("Route 'foo' in routing table 'mytable' is defined 2 times."));

        // Empty hop.
        assertVerifyFail(new RoutingSpec().addTable(new RoutingTableSpec("mytable")
                .addHop(new HopSpec("foo", ""))),
                         new ApplicationSpec(),
                         Arrays.asList("For hop 'foo' in routing table 'mytable'; Failed to parse empty string."));

        // Empty route.
        assertVerifyFail(new RoutingSpec().addTable(new RoutingTableSpec("mytable")
                .addRoute(new RouteSpec("foo"))),
                         new ApplicationSpec(),
                         Arrays.asList("Route 'foo' in routing table 'mytable' has no hops."));

        // Hop error.
        assertVerifyFail(new RoutingSpec().addTable(new RoutingTableSpec("mytable")
                .addHop(new HopSpec("foo", "bar/baz cox"))),
                         new ApplicationSpec(),
                         Arrays.asList("For hop 'foo' in routing table 'mytable'; Failed to completely parse 'bar/baz cox'."));

        // Hop error in recipient.
        assertVerifyFail(new RoutingSpec().addTable(new RoutingTableSpec("mytable")
                .addHop(new HopSpec("foo", "[bar]").addRecipient("bar/baz cox"))),
                         new ApplicationSpec(),
                         Arrays.asList("For recipient 'bar/baz cox' in hop 'foo' in routing table 'mytable'; Failed to completely parse 'bar/baz cox'."));

        // Hop error in route.
        assertVerifyFail(new RoutingSpec().addTable(new RoutingTableSpec("mytable")
                .addRoute(new RouteSpec("foo").addHop("bar/baz cox"))),
                         new ApplicationSpec(),
                         Arrays.asList("For hop 1 in route 'foo' in routing table 'mytable'; Failed to completely parse 'bar/baz cox'."));

        // Hop not found.
        assertVerifyFail(new RoutingSpec().addTable(new RoutingTableSpec("mytable")
                .addRoute(new RouteSpec("foo").addHop("bar"))),
                         new ApplicationSpec(),
                         Arrays.asList("Hop 1 in route 'foo' in routing table 'mytable' references 'bar' which is neither a service, a route nor another hop."));

        // Mismatched recipient.
        assertVerifyFail(new RoutingSpec().addTable(new RoutingTableSpec("mytable")
                .addHop(new HopSpec("foo", "bar/[baz]/cox").addRecipient("cox/0/bar"))),
                         new ApplicationSpec(),
                         Arrays.asList("Selector 'bar/[baz]/cox' does not match recipient 'cox/0/bar' in hop 'foo' in routing table 'mytable'."));

        // Route not found.
        assertVerifyFail(new RoutingSpec().addTable(new RoutingTableSpec("mytable")
                .addHop(new HopSpec("foo", "route:bar"))),
                         new ApplicationSpec(),
                         Arrays.asList("Hop 'foo' in routing table 'mytable' references route 'bar' which does not exist."));

        // Route not found in route.
        assertVerifyFail(new RoutingSpec().addTable(new RoutingTableSpec("mytable")
                .addRoute(new RouteSpec("foo").addHop("route:bar"))),
                         new ApplicationSpec(),
                         Arrays.asList("Hop 1 in route 'foo' in routing table 'mytable' references route 'bar' which does not exist."));

        // Service not found.
        assertVerifyFail(new RoutingSpec().addTable(new RoutingTableSpec("mytable")
                .addHop(new HopSpec("foo", "bar/baz"))),
                         new ApplicationSpec(),
                         Arrays.asList("Hop 'foo' in routing table 'mytable' references 'bar/baz' which is neither a service, a route nor another hop."));

        // Unexpected recipient.
        assertVerifyFail(new RoutingSpec().addTable(new RoutingTableSpec("mytable")
                .addHop(new HopSpec("foo", "bar").addRecipient("baz"))),
                         new ApplicationSpec()
                                 .addService("mytable", "bar")
                                 .addService("mytable", "baz"),
                         Arrays.asList("Hop 'foo' in routing table 'mytable' has recipients but no policy directive."));

        // Multiple errors.
        assertVerifyFail(new RoutingSpec()
                .addTable(new RoutingTableSpec("mytable"))
                .addTable(new RoutingTableSpec("mytable")
                .addHop(new HopSpec("hop1", "bar"))
                .addHop(new HopSpec("hop1", "baz"))
                .addHop(new HopSpec("hop2", ""))
                .addHop(new HopSpec("hop3", "bar/baz cox"))
                .addHop(new HopSpec("hop4", "[bar]").addRecipient("bar/baz cox"))
                .addHop(new HopSpec("hop5", "bar/[baz]/cox").addRecipient("cox/0/bar"))
                .addHop(new HopSpec("hop6", "route:route69"))
                .addHop(new HopSpec("hop7", "bar/baz"))
                .addHop(new HopSpec("hop8", "bar").addRecipient("baz"))
                .addRoute(new RouteSpec("route1").addHop("bar"))
                .addRoute(new RouteSpec("route1").addHop("baz"))
                .addRoute(new RouteSpec("route2").addHop(""))
                .addRoute(new RouteSpec("route3").addHop("bar/baz cox"))
                .addRoute(new RouteSpec("route4").addHop("hop69"))
                .addRoute(new RouteSpec("route5").addHop("route:route69"))),
                         new ApplicationSpec()
                                 .addService("mytable", "bar")
                                 .addService("mytable", "baz"),
                         Arrays.asList("Routing table 'mytable' is defined 2 times.",
                                       "For hop 'hop2' in routing table 'mytable'; Failed to parse empty string.",
                                       "For hop 'hop3' in routing table 'mytable'; Failed to completely parse 'bar/baz cox'.",
                                       "For hop 1 in route 'route2' in routing table 'mytable'; Failed to parse empty string.",
                                       "For hop 1 in route 'route3' in routing table 'mytable'; Failed to completely parse 'bar/baz cox'.",
                                       "For recipient 'bar/baz cox' in hop 'hop4' in routing table 'mytable'; Failed to completely parse 'bar/baz cox'.",
                                       "Hop 'hop1' in routing table 'mytable' is defined 2 times.",
                                       "Hop 'hop6' in routing table 'mytable' references route 'route69' which does not exist.",
                                       "Hop 'hop7' in routing table 'mytable' references 'bar/baz' which is neither a service, a route nor another hop.",
                                       "Hop 'hop8' in routing table 'mytable' has recipients but no policy directive.",
                                       "Hop 1 in route 'route4' in routing table 'mytable' references 'hop69' which is neither a service, a route nor another hop.",
                                       "Hop 1 in route 'route5' in routing table 'mytable' references route 'route69' which does not exist.",
                                       "Route 'route1' in routing table 'mytable' is defined 2 times.",
                                       "Selector 'bar/[baz]/cox' does not match recipient 'cox/0/bar' in hop 'hop5' in routing table 'mytable'."));
    }

    private static void assertVerifyOk(RoutingSpec routing, ApplicationSpec app) {
        assertVerifyFail(routing, app, new ArrayList<String>());
    }

    private static void assertVerifyFail(RoutingSpec routing, ApplicationSpec app, List<String> expectedErrors) {
        List<String> errors = new ArrayList<>();
        routing.verify(app, errors);

        Collections.sort(errors);
        Collections.sort(expectedErrors);
        assertEquals(expectedErrors.toString(), errors.toString());
    }

    private static void assertConfig(RoutingSpec routing) {
        assertEquals(routing, routing);
        assertEquals(routing, new RoutingSpec(routing));

        ConfigStore store = new ConfigStore();
        ConfigAgent subscriber = new ConfigAgent("raw:" + routing.toString(), store);
        subscriber.subscribe();
        assertTrue(store.routing.equals(routing));
    }

    private static void assertApplicationSpec(List<String> services, List<String> patterns) {
        ApplicationSpec app = new ApplicationSpec();
        for (String pattern : patterns) {
            assertFalse(app.isService("foo", pattern));
            assertFalse(app.isService("bar", pattern));
        }
        for (String service : services) {
            app.addService("foo", service);
        }
        for (String pattern : patterns) {
            assertTrue(app.isService("foo", pattern));
            assertFalse(app.isService("bar", pattern));
        }
        for (String service : services) {
            app.addService("bar", service);
        }
        for (String pattern : patterns) {
            assertTrue(app.isService("foo", pattern));
            assertTrue(app.isService("bar", pattern));
        }
    }

    private static class ConfigStore implements ConfigHandler {

        RoutingSpec routing = null;

        public void setupRouting(RoutingSpec routing) {
            this.routing = routing;
        }
    }

}
