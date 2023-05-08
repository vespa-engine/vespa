// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author hakonhall
 */
public class FleetControllerContextImplTest {
    private final MockLogger logger = new MockLogger();
    public final FleetControllerId id = new FleetControllerId("clustername", 1);
    private final FleetControllerContextImpl context = new FleetControllerContextImpl(id);

    @Test
    void verify() {
        context.log(logger, Level.INFO, "A %s message", "log");

        assertEquals(1, logger.records.size());
        assertEquals(Level.INFO, logger.records.get(0).getLevel());
        assertEquals("Cluster 'clustername': A log message", logger.records.get(0).getMessage());
    }

    private static class MockLogger extends Logger {
        public final List<LogRecord> records = new ArrayList<>();

        public MockLogger() {
            super(MockLogger.class.getName(), null);
        }

        @Override
        public void log(LogRecord record) {
            records.add(record);
        }
    }
}
