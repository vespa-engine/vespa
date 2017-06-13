// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

import java.util.Set;
import java.util.HashSet;
import java.util.logging.Level;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Testcases for LogLevel
 *
 * @author  <a href="mailto:borud@yahoo-inc.com">Bjorn Borud</a>
 */
public class LogLevelTestCase {

    /**
     * Ensure that all the log levels we need are present
     * and that they are distinct.
     */
    @Test
    public void testLogLevels () {
        assertNotNull(LogLevel.UNKNOWN);
        assertNotNull(LogLevel.FATAL);
        assertNotNull(LogLevel.ERROR);
        assertNotNull(LogLevel.WARNING);
        assertNotNull(LogLevel.INFO);
        assertNotNull(LogLevel.CONFIG);
        assertNotNull(LogLevel.EVENT);
        assertNotNull(LogLevel.DEBUG);
        assertNotNull(LogLevel.SPAM);

        // use a set to verify that all are distinct
        Set<Level> seen = new HashSet<Level>();
        assertTrue(seen.add(LogLevel.UNKNOWN));
        assertTrue(seen.add(LogLevel.FATAL));
        assertTrue(seen.add(LogLevel.ERROR));
        assertTrue(seen.add(LogLevel.WARNING));
        assertTrue(seen.add(LogLevel.INFO));
        assertTrue(seen.add(LogLevel.CONFIG));
        assertTrue(seen.add(LogLevel.EVENT));
        assertTrue(seen.add(LogLevel.DEBUG));
        assertTrue(seen.add(LogLevel.SPAM));

        // verify that set would trigger error (not necessary)
        assertTrue(! seen.add(LogLevel.SPAM));
    }

    /**
     * Test that given the log level name we are able to
     * map it to the correct static instance.
     */
    @Test
    public void testNameToLevelMapping () {
        assertEquals(LogLevel.UNKNOWN, LogLevel.parse("unknown"));
        assertEquals(LogLevel.FATAL, LogLevel.parse("fatal"));
        assertEquals(LogLevel.ERROR, LogLevel.parse("error"));
        assertEquals(LogLevel.WARNING, LogLevel.parse("warning"));
        assertEquals(LogLevel.INFO, LogLevel.parse("info"));
        assertEquals(LogLevel.CONFIG, LogLevel.parse("config"));
        assertEquals(LogLevel.EVENT, LogLevel.parse("event"));
        assertEquals(LogLevel.DEBUG, LogLevel.parse("debug"));
        assertEquals(LogLevel.SPAM, LogLevel.parse("spam"));
    }

    @Test
    public void testJavaToLevelMapping () {
        assertTrue(LogLevel.ERROR   == LogLevel.getVespaLogLevel(Level.SEVERE));
        assertTrue(LogLevel.WARNING == LogLevel.getVespaLogLevel(Level.WARNING));
        assertTrue(LogLevel.INFO    == LogLevel.getVespaLogLevel(Level.INFO));
        assertTrue(LogLevel.CONFIG  == LogLevel.getVespaLogLevel(Level.CONFIG));
        assertTrue(LogLevel.DEBUG   == LogLevel.getVespaLogLevel(Level.FINE));
        assertTrue(LogLevel.DEBUG   == LogLevel.getVespaLogLevel(Level.FINER));
        assertTrue(LogLevel.SPAM    == LogLevel.getVespaLogLevel(Level.FINEST));
    }
}
