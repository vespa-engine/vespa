// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.test;

import com.yahoo.jdisc.service.ClientProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;


/**
 * @author Simon Thoresen Hult
 */
public class NonWorkingClientTestCase {

    @Test
    void requireThatHandleRequestThrowsException() {
        ClientProvider client = new NonWorkingClientProvider();
        try {
            client.handleRequest(null, null);
            fail();
        } catch (UnsupportedOperationException e) {

        }
    }

    @Test
    void requireThatHandleTimeoutThrowsException() {
        ClientProvider client = new NonWorkingClientProvider();
        try {
            client.handleTimeout(null, null);
            fail();
        } catch (UnsupportedOperationException e) {

        }
    }

    @Test
    void requireThatStartDoesNotThrow() {
        ClientProvider client = new NonWorkingClientProvider();
        client.start();
    }

    @Test
    void requireThatRetainDoesNotThrow() {
        ClientProvider client = new NonWorkingClientProvider();
        client.release();
    }

    @Test
    void requireThatReleaseDoesNotThrow() {
        ClientProvider client = new NonWorkingClientProvider();
        client.release();
    }
}
