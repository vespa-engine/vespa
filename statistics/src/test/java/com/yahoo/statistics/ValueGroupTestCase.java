// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.statistics;


import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.yahoo.container.StatisticsConfig;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Test set for groups of values.
 *
 * @author Steinar Knutsen
 */
public class ValueGroupTestCase {

    private volatile boolean gotRecord = false;

    private class ValueGroupHandler extends Handler {
        // this is for testing ValueProxy

        @Override
        public void publish(LogRecord record) {
            com.yahoo.log.event.ValueGroup msg = (com.yahoo.log.event.ValueGroup) record.getParameters()[0];
            assertEquals("test", msg.getValue("name"));
            String values = msg.getValue("values");
            assertFalse("Unexpected value for a.", values.indexOf("a=-50.0") == -1);
            assertFalse("Unexpected value for b.", values.indexOf("b=40.0") == -1);
            assertFalse("Unexpected value for c.", values.indexOf("c=0.0") == -1);
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
        Logger logger = Logger.getLogger(ValueGroup.class.getName());
        boolean initUseParentHandlers = logger.getUseParentHandlers();
        Handler logChecker = new ValueGroupHandler();
        logger.setUseParentHandlers(false);
        final MockStatistics manager = new MockStatistics();
        ValueGroup v = new ValueGroup("test", manager);
        v.put("a", 50.0);
        v.put("b", 40.0);
        v.put("a", -50.0);
        assertTrue("Last value inserted to a was -50",
                   -50.0 == v.getValue("a").get());
        assertTrue("Last value inserted to b was 40.",
                   40.0 == v.getValue("b").get());
        assertTrue("c has not been used yet",
                   0.0 == v.getValue("c").get());
        logger.addHandler(logChecker);
        v.run();
        assertFalse("The logging handler did not really run.", gotRecord == false);
        assertEquals(1, manager.registerCount);
        // cleanup:
        logger.removeHandler(logChecker);
        logger.setUseParentHandlers(initUseParentHandlers);
    }

    @Test
    public void testOverlappingSubnames() {
        final MockStatistics manager = new MockStatistics();
        ValueGroup v = new ValueGroup("jappe", manager);
        ValueGroup v2 = new ValueGroup("nalle", manager);
        final String name = "mobil";
        v.put(name, 50.0);
        v2.put(name, 40.0);
        assertEquals(50.0, v.getValue(name).get(), 1e-9);
        assertEquals(40.0, v2.getValue(name).get(), 1e-9);
        assertEquals(2, manager.registerCount);
    }

    @Test
    public void testObjectContracts() {
        ValueGroup v = new ValueGroup("test", new MockStatistics());
        ValueGroup v2 = new ValueGroup("test", new MockStatistics());
        v2.put("nalle", 2.0);
        assertEquals(v, v2);
        assertEquals(v.hashCode(), v2.hashCode());
        v2 = new ValueGroup("nalle", new MockStatistics());
        assertFalse("Different names should lead to different hashcodes",
                v.hashCode() == v2.hashCode());
        assertFalse("Different names should lead to equals() return false",
                v.equals(v2));
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
