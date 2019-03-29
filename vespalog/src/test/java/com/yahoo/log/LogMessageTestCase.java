// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

import com.yahoo.log.event.Event;
import com.yahoo.log.event.MalformedEventException;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import static org.junit.Assert.assertNotNull;

/**
 * Unit tests for the LogMessage class.
 *
 * @author Bjorn Borud
 * @author bjorncs
 */
public class LogMessageTestCase {

    @Test
    public void testLogParsing () throws IOException, InvalidLogFormatException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(LogMessageTestCase.class.getResourceAsStream("/logEntries.txt")))) {
            for (String line = br.readLine(); line != null; line = br.readLine()) {
                LogMessage.parseNativeFormat(line);
            }
        }
    }

    /**
     * Read in some events and make sure we are able to identify
     * them as such.
     */
    @Test
    public void testEvents () throws IOException, InvalidLogFormatException, MalformedEventException {
        try (BufferedReader br =
                     new BufferedReader(
                             new InputStreamReader(
                                     LogMessageTestCase.class.getResourceAsStream("/event.txt")))) {
            for (String line = br.readLine(); line != null; line = br.readLine()) {
                LogMessage m = LogMessage.parseNativeFormat(line);
                Event event = m.getEvent();
                assertNotNull(event);
            }
        }

    }
}

