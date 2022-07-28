// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.test;

import com.yahoo.jdisc.handler.RequestHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;


/**
 * @author Simon Thoresen Hult
 */
public class NonWorkingRequestHandlerTestCase {

    @Test
    void requireThatHandleRequestThrowsException() {
        RequestHandler requestHandler = new NonWorkingRequestHandler();
        try {
            requestHandler.handleRequest(null, null);
            fail();
        } catch (UnsupportedOperationException e) {

        }
    }

    @Test
    void requireThatHandleTimeoutThrowsException() {
        RequestHandler requestHandler = new NonWorkingRequestHandler();
        try {
            requestHandler.handleTimeout(null, null);
            fail();
        } catch (UnsupportedOperationException e) {

        }
    }

    @Test
    void requireThatDestroyDoesNotThrow() {
        RequestHandler requestHandler = new NonWorkingRequestHandler();
        requestHandler.release();
    }
}
