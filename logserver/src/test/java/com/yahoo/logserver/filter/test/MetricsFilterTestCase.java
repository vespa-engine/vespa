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

import org.junit.*;

import static org.junit.Assert.*;

public class MetricsFilterTestCase {

    @Test
    public void testValueEvents() throws InvalidLogFormatException, IOException {
        MetricsFilter filter = new MetricsFilter();
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
                assertTrue(true);
            } else {
                fail();
            }
        }

    }
}
