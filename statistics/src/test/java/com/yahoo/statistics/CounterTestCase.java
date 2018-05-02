// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.statistics;

import java.util.Arrays;
import java.util.logging.Logger;

import com.yahoo.container.StatisticsConfig;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Check counters work.
 *
 * @author Steinar Knutsen
 */

public class CounterTestCase {

    @Test
    public void testBasic() {
        Counter c = new Counter("test", Statistics.nullImplementation, false);
        c.increment();
        assertEquals(1, c.get());
        c.increment(499);
        assertEquals(500, c.get());
        c.reset();
        assertEquals(500, c.get());
        c = new Counter("test", Statistics.nullImplementation, false, null, true);
        c.increment();
        assertEquals(1, c.get());
        c.increment(499);
        assertEquals(500, c.get());
        c.reset();
        assertEquals(0, c.get());
    }

    @Test
    public void testObjectContracts() {
        final String counterName = "test";
        Counter c = new Counter(counterName, Statistics.nullImplementation, false);
        Counter c2 = new Counter(counterName, Statistics.nullImplementation, false);
        c2.increment();
        assertEquals(c, c2);
        assertEquals(c.hashCode(), c2.hashCode());
        c2 = new Counter("nalle", Statistics.nullImplementation, false);
        assertFalse("Different names should lead to different hashcodes",
                c.hashCode() == c2.hashCode());
        assertFalse("Different names should lead to equals() return false",
                c.equals(c2));
        String prefix = "com.yahoo.statistics.Counter";
        String suffix = counterName + " 0";
        String image = c.toString();
        assertEquals(suffix, image.substring(image.length() - suffix.length()));
        assertEquals(prefix, image.substring(0, prefix.length()));
    }

    @Test
    public void testConfigStuff() {
        Logger logger = Logger.getLogger(Counter.class.getName());
        boolean initUseParentHandlers = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        MockStatistics m = new MockStatistics();
        final String joppe = "joppe";
        StatisticsConfig config = new StatisticsConfig(
                new StatisticsConfig.Builder().counterresets(Arrays
                        .asList(new StatisticsConfig.Counterresets.Builder[] { new StatisticsConfig.Counterresets.Builder()
                                .name(joppe) })));
        m.config = config;
        Counter c = new Counter("nalle", m, true);
        Counter c2 = new Counter(joppe, m, true);
        c.increment();
        c2.increment();
        assertEquals(1L, c.get());
        assertEquals(1L, c2.get());
        c.run();
        c2.run();
        assertEquals(1L, c.get());
        assertEquals(0L, c2.get());
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
