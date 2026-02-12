// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging.test;

import com.yahoo.container.logging.LogFormatter;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Bob Travis
 */
public class LogFormatterTestCase {

    @Test
    void testIt() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        // Use Instant instead of deprecated Date constructor
        long time = Instant.parse("2003-08-25T13:30:35Z").toEpochMilli();
        String result = LogFormatter.insertDate("test%Y%m%d%H%M%S%x", time);
        assertEquals("test20030825133035Aug", result);
        result = LogFormatter.insertDate("test%s%T", time);
        assertEquals("test000" + time, result);
    }

}
