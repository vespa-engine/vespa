// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.statistics;


import java.util.Arrays;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.yahoo.container.StatisticsConfig;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Test set for groups of counters.
 *
 * @author Steinar Knutsen
 */
public class CounterGroupTestCase {

    private volatile boolean gotRecord = false;

    private class CounterGroupHandler extends Handler {
        // This is for testing CounterProxy
        @Override
        public void publish(LogRecord record) {
            com.yahoo.log.event.CountGroup msg = (com.yahoo.log.event.CountGroup) record.getParameters()[0];
            assertEquals("test", msg.getValue("name"));
            String values = msg.getValue("values");
            assertFalse("Unexpected value for a.", values.indexOf("a=500") == -1);
            assertFalse("Unexpected value for b.", values.indexOf("b=1") == -1);
            assertFalse("Unexpected value for c.", values.indexOf("c=0") == -1);
            gotRecord = true;

        }

        @Override
        public void flush() {
        }

        @Override
        public void close() throws SecurityException {
        }
    }

    @Test
    public void testBasic() {
        Logger logger = Logger.getLogger(CounterGroup.class.getName());
        boolean initUseParentHandlers = logger.getUseParentHandlers();
        Handler logChecker = new CounterGroupHandler();
        logger.setUseParentHandlers(false);
        CounterGroup c = new CounterGroup("test", Statistics.nullImplementation, false);
        Counter n;
        c.increment("a");
        c.increment("b");
        c.increment("a", 499);
        n = c.getCounter("a");
        assertEquals(500, n.get());
        n = c.getCounter("b");
        assertEquals(1, n.get());
        n = c.getCounter("c");
        assertEquals(0, n.get());
        logger.addHandler(logChecker);
        c.run();
        assertFalse("The logging handler did not really run.", gotRecord == false);
        // cleanup:
        logger.removeHandler(logChecker);
        logger.setUseParentHandlers(initUseParentHandlers);
    }

    @Test
    public void testObjectContracts() {
        CounterGroup c = new CounterGroup("test", Statistics.nullImplementation, false);
        CounterGroup c2 = new CounterGroup("test", Statistics.nullImplementation, false);
        c2.increment("nalle");
        assertEquals(c, c2);
        assertEquals(c.hashCode(), c2.hashCode());
        c2 = new CounterGroup("nalle", Statistics.nullImplementation, false);
        assertFalse("Different names should lead to different hashcodes",
                c.hashCode() == c2.hashCode());
        assertFalse("Different names should lead to equals() return false",
                c.equals(c2));
    }

    @Test
    public void testConfigStuff() {
        Logger logger = Logger.getLogger(CounterGroup.class.getName());
        boolean initUseParentHandlers = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        MockStatistics m = new MockStatistics();
        final String joppe = "joppe";
        StatisticsConfig config = new StatisticsConfig(
                new StatisticsConfig.Builder().counterresets(Arrays
                        .asList(new StatisticsConfig.Counterresets.Builder[] {
                                new StatisticsConfig.Counterresets.Builder().name(joppe) })));
        m.config = config;
        CounterGroup c = new CounterGroup("nalle", m);
        CounterGroup c2 = new CounterGroup(joppe, m);
        final String bamse = "bamse";
        c.increment(bamse);
        c2.increment(bamse);
        assertEquals(1L, c.getCounter(bamse).get());
        assertEquals(1L, c2.getCounter(bamse).get());
        c2.increment(bamse);
        assertEquals(1L, c.getCounter(bamse).get());
        assertEquals(2L, c2.getCounter(bamse).get());
        c.run();
        c2.run();
        assertEquals(1L, c.getCounter(bamse).get());
        assertEquals(0L, c2.getCounter(bamse).get());
        logger.setUseParentHandlers(initUseParentHandlers);
    }

    public class MockStatistics implements Statistics {
        public StatisticsConfig config = null;
        public int registerCount = 0;

        @Override
        public void register(Handle h) {
            registerCount += 1;
        }

        @Override
        public void remove(String name) {
        }

        @Override
        public StatisticsConfig getConfig() {
            return config;
        }

        @Override
        public int purge() {
            return 0;
        }
    }

}
