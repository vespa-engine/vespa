// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.test;

import com.yahoo.jdisc.Response;
import org.junit.Test;

import static org.junit.Assert.fail;


/**
 * @author Simon Thoresen Hult
 */
public class NonWorkingResponseHandlerTestCase {

    @Test
    public void requireThatHandleResponseThrowsException() {
        NonWorkingResponseHandler handler = new NonWorkingResponseHandler();
        try {
            handler.handleResponse(new Response(Response.Status.OK));
            fail();
        } catch (UnsupportedOperationException e) {

        }
    }
}
