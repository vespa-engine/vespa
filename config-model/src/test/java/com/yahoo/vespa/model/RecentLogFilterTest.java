// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;


/**
 * @author hmusum
 */
public class RecentLogFilterTest {

    @Test
    public void basic() {
        RecentLogFilter rlf = new RecentLogFilter();
        List<LogRecord> logRecords = new ArrayList<>();
        for (int i = 0; i < RecentLogFilter.maxMessages + 1; i++) {
            logRecords.add(new LogRecord(Level.INFO, "" + i));
        }

        assertTrue(rlf.isLoggable(logRecords.get(0)));
        assertFalse(rlf.isLoggable(logRecords.get(0)));

        for (int i = 1; i < RecentLogFilter.maxMessages + 1; i++) {
            assertTrue(rlf.isLoggable(logRecords.get(i)));
        }

        // Should have filled up maxMessages slots with records 1-maxMessages
        // and pushed the first one out, so the below should return true
        assertTrue(rlf.isLoggable(logRecords.get(0)));
    }

}
