// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Simon Thoresen Hult
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
