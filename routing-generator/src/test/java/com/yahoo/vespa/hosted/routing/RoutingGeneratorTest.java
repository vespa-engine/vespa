// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.routing;

import com.yahoo.cloud.config.LbServicesConfig;
import com.yahoo.config.ConfigInstance;
import com.yahoo.config.subscription.ConfigSet;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.config.ConfigKey;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author mpolden
 */
public class RoutingGeneratorTest {

    @Test(timeout = 2000)
    public void config_subscription() {
        RouterMock router = new RouterMock();
        RoutingGenerator generator = new RoutingGenerator(new ConfigSetMock(), router, new ManualClock());
        try {
            router.awaitLoad(generator);
            assertNotNull("Router loads table", router.currentTable);
            assertEquals("Routing generator and router has same table",
                         generator.routingTable().get(),
                         router.currentTable);
        } finally {
            generator.deconstruct();
        }
    }

    private static class RouterMock implements Router {
        private volatile RoutingTable currentTable = null;

        @Override
        public void load(RoutingTable table) {
            currentTable = table;
        }

        public void awaitLoad(RoutingGenerator generator) {
            AtomicBoolean completed = new AtomicBoolean(false);
            while (!completed.get()) {
                generator.routingTable().ifPresent(table -> completed.set(table == currentTable));
                if (!completed.get()) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

    }

    private static class ConfigSetMock extends ConfigSet {

        private int attempt = 0;

        public ConfigSetMock() {
            addBuilder("*", new LbServicesConfig.Builder());
        }

        @Override
        public ConfigInstance.Builder get(ConfigKey<?> key) {
            if (++attempt <= 5) {
                throw new RuntimeException("Failed to get config on attempt " + attempt);
            }
            return super.get(key);
        }

    }

}
