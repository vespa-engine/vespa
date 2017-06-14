// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.test;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;

import com.yahoo.log.event.Event;
import com.yahoo.log.event.MalformedEventException;
import com.yahoo.log.InvalidLogFormatException;
import com.yahoo.log.LogMessage;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * Unit tests for the LogMessage class.
 *
 * @author Bjorn Borud
 */
public class LogMessageTestCase {

    /**
     * The original test was rubbish.  We just test that parsing
     * is okay here.  The way we do it is to check that we have
     * some log messages available from the MockLogEntries class.
     * If there are none the parsing failed.
     */
    @Test
    public void testLogParsing ()
    {
        assertTrue(MockLogEntries.getMessages().length > 0);
    }

    /**
     * Read in some events and make sure we are able to identify
     * them as such.
     */
    @Test
    public void testEvents () throws IOException {
        String eventfile = "src/test/files/event.txt.gz";
        BufferedReader br = new BufferedReader(
                            new InputStreamReader(
                            new GZIPInputStream(
                            new FileInputStream(eventfile))));

        for (String line = br.readLine(); line != null; line = br.readLine()) {
            try {
                LogMessage m = LogMessage.parseNativeFormat(line);
                try {
                    Event event = m.getEvent();
                    assertNotNull(event);
                } catch (MalformedEventException evx) {
                    fail();
                }
            } catch (InvalidLogFormatException e) {
                fail();
            }
        }
    }
}

