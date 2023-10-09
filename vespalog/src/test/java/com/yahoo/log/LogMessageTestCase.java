// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

import com.yahoo.log.event.Event;
import com.yahoo.log.event.MalformedEventException;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import static org.junit.Assert.assertEquals;
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

    @Test
    public void testParsingTimestampAndRendering() throws InvalidLogFormatException {
        {
            LogMessage message = LogMessage.parseNativeFormat("1096639280.524133935\tmalfunction\t26851\t-\tlogtest\tinfo\tStarting up, called as ./log/logtest");
            assertEquals(1096639280L, message.getTimestamp().getEpochSecond());
            assertEquals(524133935L, message.getTimestamp().getNano());
            assertEquals("1096639280.524133\tmalfunction\t26851\t-\tlogtest\tinfo\tStarting up, called as ./log/logtest\n", message.toString());
        }
        {
            LogMessage message = LogMessage.parseNativeFormat("1096639280.524\tmalfunction\t26851\t-\tlogtest\tinfo\tbackslash: \\\\");
            assertEquals(1096639280L, message.getTimestamp().getEpochSecond());
            assertEquals(524_000_000L, message.getTimestamp().getNano());
            assertEquals("1096639280.524000\tmalfunction\t26851\t-\tlogtest\tinfo\tbackslash: \\\\\n", message.toString());
        }
    }
}

