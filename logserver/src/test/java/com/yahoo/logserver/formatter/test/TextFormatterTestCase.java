// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/*
 * $Id$
 *
 */
package com.yahoo.logserver.formatter.test;

import com.yahoo.log.InvalidLogFormatException;
import com.yahoo.log.LogMessage;
import com.yahoo.logserver.formatter.TextFormatter;
import com.yahoo.logserver.test.MockLogEntries;

import org.junit.*;

import static org.junit.Assert.*;

/**
 * Test the TextFormatter
 *
 * @author Bjorn Borud
 */
public class TextFormatterTestCase {

    /**
     * Just simple test to make sure it doesn't die on us
     */
    @Test
    public void testTextFormatter() {
        TextFormatter tf = new TextFormatter();
        LogMessage[] ms = MockLogEntries.getMessages();
        for (int i = 0; i < ms.length; i++) {
            System.out.println(tf.format(ms[i]));
        }
    }

    /**
     * Test that a specific log message is formatted correctly
     */
    @Test
    public void testSpecificMessage() throws InvalidLogFormatException {
        String l = "1115200798.195568\texample.yahoo.com\t65819\ttopleveldispatch\tfdispatch.queryperf\tevent\tvalue/1 name=\"query_eval_time_avg_s\" value=0.0229635972697721825";
        String result = "2005-05-04 09:59:58 example.yahoo.com 65819 topleveldispatch fdispatch.queryperf EVENT value/1 name=\"query_eval_time_avg_s\" value=0.0229635972697721825\n";
        LogMessage m = LogMessage.parseNativeFormat(l);
        TextFormatter tf = new TextFormatter();
        assertEquals(result, tf.format(m));
    }
}
