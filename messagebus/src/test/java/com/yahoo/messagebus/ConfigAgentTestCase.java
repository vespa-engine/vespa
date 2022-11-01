// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.config.subscription.ConfigSet;
import com.yahoo.config.subscription.ConfigURI;
import com.yahoo.messagebus.routing.RoutingSpec;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class ConfigAgentTestCase {

    @Test
    void testRoutingConfig() throws InterruptedException {
        LocalHandler handler = new LocalHandler();
        assertFalse(testHalf(handler.spec));
        assertFalse(testFull(handler.spec));

        ConfigSet set = new ConfigSet();
        set.addBuilder("test", writeFull());

        ConfigAgent agent = new ConfigAgent(ConfigURI.createFromIdAndSource("test", set), handler);
        assertFalse(testHalf(handler.spec));
        assertFalse(testFull(handler.spec));
        agent.subscribe();
        assertFalse(testHalf(handler.spec));
        assertTrue(testFull(handler.spec));

        handler.reset();
        set.addBuilder("test", writeHalf());
        assertTrue(handler.await(120, TimeUnit.SECONDS));
        assertTrue(testHalf(handler.spec));
        assertFalse(testFull(handler.spec));

        handler.reset();
        set.addBuilder("test", writeFull());
        assertTrue(handler.await(120, TimeUnit.SECONDS));
        assertTrue(testFull(handler.spec));
        assertFalse(testHalf(handler.spec));
    }

    private boolean testHalf(RoutingSpec spec) {
        if (spec.getNumTables() != 1) {
            return false;
        }
        assertTables(spec, 1);
        return true;
    }

    private boolean testFull(RoutingSpec spec) {
        if (spec.getNumTables() != 2) {
            return false;
        }
        assertTables(spec, 2);
        return true;
    }

    private void assertTables(RoutingSpec spec, int numTables) {
        assertEquals(numTables, spec.getNumTables());
        if (numTables > 0) {
            assertEquals("foo", spec.getTable(0).getProtocol());
            assertEquals(2, spec.getTable(0).getNumHops());
            assertEquals("foo-h1", spec.getTable(0).getHop(0).getName());
            assertEquals("foo-h1-sel", spec.getTable(0).getHop(0).getSelector());
            assertEquals(2, spec.getTable(0).getHop(0).getNumRecipients());
            assertEquals("foo-h1-r1", spec.getTable(0).getHop(0).getRecipient(0));
            assertEquals("foo-h1-r2", spec.getTable(0).getHop(0).getRecipient(1));
            assertEquals(true, spec.getTable(0).getHop(0).getIgnoreResult());
            assertEquals("foo-h2", spec.getTable(0).getHop(1).getName());
            assertEquals("foo-h2-sel", spec.getTable(0).getHop(1).getSelector());
            assertEquals(2, spec.getTable(0).getHop(1).getNumRecipients());
            assertEquals("foo-h2-r1", spec.getTable(0).getHop(1).getRecipient(0));
            assertEquals("foo-h2-r2", spec.getTable(0).getHop(1).getRecipient(1));
            assertEquals(2, spec.getTable(0).getNumRoutes());
            assertEquals("foo-r1", spec.getTable(0).getRoute(0).getName());
            assertEquals(2, spec.getTable(0).getRoute(0).getNumHops());
            assertEquals("foo-h1", spec.getTable(0).getRoute(0).getHop(0));
            assertEquals("foo-h2", spec.getTable(0).getRoute(0).getHop(1));
            assertEquals("foo-r2", spec.getTable(0).getRoute(1).getName());
            assertEquals(2, spec.getTable(0).getRoute(1).getNumHops());
            assertEquals("foo-h2", spec.getTable(0).getRoute(1).getHop(0));
            assertEquals("foo-h1", spec.getTable(0).getRoute(1).getHop(1));
        }
        if (numTables > 1) {
            assertEquals("bar", spec.getTable(1).getProtocol());
            assertEquals(2, spec.getTable(1).getNumHops());
            assertEquals("bar-h1", spec.getTable(1).getHop(0).getName());
            assertEquals("bar-h1-sel", spec.getTable(1).getHop(0).getSelector());
            assertEquals(2, spec.getTable(1).getHop(0).getNumRecipients());
            assertEquals("bar-h1-r1", spec.getTable(1).getHop(0).getRecipient(0));
            assertEquals("bar-h1-r2", spec.getTable(1).getHop(0).getRecipient(1));
            assertEquals("bar-h2", spec.getTable(1).getHop(1).getName());
            assertEquals("bar-h2-sel", spec.getTable(1).getHop(1).getSelector());
            assertEquals(2, spec.getTable(1).getHop(1).getNumRecipients());
            assertEquals("bar-h2-r1", spec.getTable(1).getHop(1).getRecipient(0));
            assertEquals("bar-h2-r2", spec.getTable(1).getHop(1).getRecipient(1));
            assertEquals(2, spec.getTable(1).getNumRoutes());
            assertEquals("bar-r1", spec.getTable(1).getRoute(0).getName());
            assertEquals(2, spec.getTable(1).getRoute(0).getNumHops());
            assertEquals("bar-h1", spec.getTable(1).getRoute(0).getHop(0));
            assertEquals("bar-h2", spec.getTable(1).getRoute(0).getHop(1));
            assertEquals("bar-r2", spec.getTable(1).getRoute(1).getName());
            assertEquals(2, spec.getTable(1).getRoute(1).getNumHops());
            assertEquals("bar-h2", spec.getTable(1).getRoute(1).getHop(0));
            assertEquals("bar-h1", spec.getTable(1).getRoute(1).getHop(1));
        }
    }

    private static MessagebusConfig.Builder writeHalf() {
        return writeTables(1);
    }

    private static MessagebusConfig.Builder writeFull() {
        return writeTables(2);
    }

    private static MessagebusConfig.Builder writeTables(int numTables) {
        MessagebusConfig.Builder builder = new MessagebusConfig.Builder();
        if (numTables > 0) {
            MessagebusConfig.Routingtable.Builder table = new MessagebusConfig.Routingtable.Builder();
            table.protocol("foo");
            table.hop(getHop("foo-h1", "foo-h1-sel", "foo-h1-r1", "foo-h1-r2", true));
            table.hop(getHop("foo-h2", "foo-h2-sel", "foo-h2-r1", "foo-h2-r2", false));
            table.route(getRoute("foo-r1", "foo-h1", "foo-h2"));
            table.route(getRoute("foo-r2", "foo-h2", "foo-h1"));
            builder.routingtable(table);
        }
        if (numTables > 1) {
            MessagebusConfig.Routingtable.Builder table = new MessagebusConfig.Routingtable.Builder();
            table.protocol("bar");
            table.hop(getHop("bar-h1", "bar-h1-sel", "bar-h1-r1", "bar-h1-r2", false));
            table.hop(getHop("bar-h2", "bar-h2-sel", "bar-h2-r1", "bar-h2-r2", false));
            table.route(getRoute("bar-r1", "bar-h1", "bar-h2"));
            table.route(getRoute("bar-r2", "bar-h2", "bar-h1"));
            builder.routingtable(table);
        }
        return builder;
    }

    private static MessagebusConfig.Routingtable.Route.Builder getRoute(String name, String hop1, String hop2) {
        MessagebusConfig.Routingtable.Route.Builder route = new MessagebusConfig.Routingtable.Route.Builder();
        route.name(name);
        route.hop(hop1);
        route.hop(hop2);
        return route;
    }

    private static MessagebusConfig.Routingtable.Hop.Builder getHop(String name, String selector, String recipient1, String recipient2, boolean ignoreresult) {
        MessagebusConfig.Routingtable.Hop.Builder hop = new MessagebusConfig.Routingtable.Hop.Builder();
        hop.name(name);
        hop.selector(selector);
        hop.recipient(recipient1);
        hop.recipient(recipient2);
        hop.ignoreresult(ignoreresult);
        return hop;
    }

    private static class LocalHandler implements ConfigHandler {

        RoutingSpec spec = new RoutingSpec();

        public synchronized void setupRouting(RoutingSpec spec) {
            this.spec = spec;
            notify();
        }

        public synchronized void reset() {
            spec = null;
        }

        public synchronized boolean await(int timeout, TimeUnit unit) throws InterruptedException {
            long remaining, doom = System.currentTimeMillis() + unit.toMillis(timeout);
            while (spec == null && (remaining = doom - System.currentTimeMillis()) > 0)
                wait(remaining);

            return spec != null;
        }
    }
}
