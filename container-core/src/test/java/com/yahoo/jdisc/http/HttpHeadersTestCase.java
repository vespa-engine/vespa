// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Simon Thoresen Hult
 */
public class HttpHeadersTestCase {

    @Test
    void requireThatHeadersDoNotChange() {
        assertEquals("X-JDisc-Disable-Chunking", HttpHeaders.Names.X_DISABLE_CHUNKING);
    }
}
