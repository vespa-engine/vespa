// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/*
 * $Id$
 *
 */
package com.yahoo.logserver.formatter.test;

import com.yahoo.log.LogMessage;
import com.yahoo.logserver.formatter.NullFormatter;
import com.yahoo.logserver.test.MockLogEntries;

import org.junit.*;

import static org.junit.Assert.*;

/**
 * Test the NullFormatter
 *
 * @author Bjorn Borud
 */
public class NullFormatterTestCase {

    @Test
    public void testNullFormatter() {
        NullFormatter nf = new NullFormatter();
        LogMessage[] ms = MockLogEntries.getMessages();
        for (LogMessage m : ms) {
            assertEquals(m.toString(), nf.format(m));
        }
    }
}
