// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class ContinuationTestCase {

    private static final String KNOWN_CONTINUATION = "BCBCBCBEBGBCBKCBACBKCCK";

    @Test
    public void requireThatToStringCanBeParsedByFromString() {
        Continuation cnt = Continuation.fromString(KNOWN_CONTINUATION);
        assertNotNull(cnt);
        assertEquals(KNOWN_CONTINUATION, cnt.toString());
    }
}
