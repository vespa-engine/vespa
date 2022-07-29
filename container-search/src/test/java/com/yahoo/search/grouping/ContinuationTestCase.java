// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Simon Thoresen Hult
 */
public class ContinuationTestCase {

    private static final String KNOWN_CONTINUATION = "BCBCBCBEBGBCBKCBACBKCCK";

    @Test
    void requireThatToStringCanBeParsedByFromString() {
        Continuation cnt = Continuation.fromString(KNOWN_CONTINUATION);
        assertNotNull(cnt);
        assertEquals(KNOWN_CONTINUATION, cnt.toString());
    }
}
