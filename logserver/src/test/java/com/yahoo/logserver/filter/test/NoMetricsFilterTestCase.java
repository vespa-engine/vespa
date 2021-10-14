// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.filter.test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import com.yahoo.log.event.Event;
import com.yahoo.log.event.MalformedEventException;
import com.yahoo.log.InvalidLogFormatException;
import com.yahoo.log.LogMessage;
import com.yahoo.logserver.filter.MetricsFilter;
import com.yahoo.logserver.filter.NoMetricsFilter;

import org.junit.*;

import static org.junit.Assert.*;

/**
 * Ensure that the NoMetricsFilter does the opposite of MetricsFilter
 */
public class NoMetricsFilterTestCase {

    @Test
    public void testValueEvents() throws InvalidLogFormatException, IOException {
        NoMetricsFilter filter = new NoMetricsFilter();
        MetricsFilter metricsFilter = new MetricsFilter();

        String filename = "src/test/files/value-events.txt";
        BufferedReader br = new BufferedReader(new FileReader(filename));
        for (String line = br.readLine(); line != null; line = br.readLine()) {
            LogMessage m = LogMessage.parseNativeFormat(line);
            assertNotNull(m);

            try {
                Event event = m.getEvent();
                assertNotNull(event);
            } catch (MalformedEventException e) {
                fail();
            }

            if (filter.isLoggable(m)) {
                fail();
            } else {
                assertTrue(true);
            }


            if (metricsFilter.isLoggable(m)) {
                assertTrue(true);
            } else {
                fail();
            }
        }
    }
}
