// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/* -*- c-basic-offset: 4 -*-
 *
 * $Id$
 *
 */
package com.yahoo.logserver.handlers.replicator;

import java.nio.ByteBuffer;

import com.yahoo.logserver.handlers.replicator.FormattedBufferCache;
import com.yahoo.log.LogMessage;
import com.yahoo.logserver.formatter.LogFormatter;
import com.yahoo.logserver.formatter.LogFormatterManager;
import com.yahoo.logserver.test.MockLogEntries;

import org.junit.*;

import static org.junit.Assert.*;

/**
 * Unit tests for thge LogMessage class.
 *
 * @author Bjorn Borud
 */
public class FormattedBufferCacheTestCase {

    @Test
    public void testCache() {
        LogMessage msgs[] = MockLogEntries.getMessages();
        FormattedBufferCache cache = new FormattedBufferCache();
        String n[] = LogFormatterManager.getFormatterNames();
        for (int i = 0; i < n.length; i++) {
            LogFormatter f = LogFormatterManager.getLogFormatter(n[i]);
            for (int j = 0; j < msgs.length; j++) {
                ByteBuffer bb = cache.getFormatted(msgs[j], f);
                assertNotNull(bb);
            }
        }

        assertTrue(cache.getUnderlyingMapOnlyForTesting().size() > 0);
        cache.reset();
        assertTrue(cache.getUnderlyingMapOnlyForTesting().size() == 0);
    }
}
