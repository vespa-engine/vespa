// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/*
 * $Id$
 *
 */
package com.yahoo.logserver.formatter.test;

import com.yahoo.logserver.formatter.LogFormatter;
import com.yahoo.logserver.formatter.LogFormatterManager;
import com.yahoo.logserver.formatter.NullFormatter;
import com.yahoo.logserver.formatter.TextFormatter;

import org.junit.*;

import static org.junit.Assert.*;

/**
 * Test the LogFormatterManager
 *
 * @author Bjorn Borud
 */
public class LogFormatterManagerTestCase {

    /**
     * Ensure the system formatters are present
     */
    @Test
    public void testSystemFormatters() {
        LogFormatter lf = LogFormatterManager.getLogFormatter("system.textformatter");
        assertNotNull(lf);
        assertEquals(TextFormatter.class, lf.getClass());

        lf = LogFormatterManager.getLogFormatter("system.nullformatter");
        assertNotNull(lf);
        assertEquals(NullFormatter.class, lf.getClass());
    }
}
