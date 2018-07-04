// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import com.google.common.util.concurrent.MoreExecutors;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.test.NonWorkingContentChannel;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

/**
 * @author Simon Thoresen Hult
 */
public class FutureResponseTestCase {

    @Test
    public void requireThatCancelIsUnsupported() {
        FutureResponse future = new FutureResponse();
        assertFalse(future.isCancelled());
        try {
            future.cancel(true);
            fail();
        } catch (UnsupportedOperationException e) {

        }
        assertFalse(future.isCancelled());
        try {
            future.cancel(false);
            fail();
        } catch (UnsupportedOperationException e) {

        }
        assertFalse(future.isCancelled());
    }

    @Test
    public void requireThatCompletionIsDoneWhenHandlerIsCalled() {
        FutureResponse future = new FutureResponse();
        assertFalse(future.isDone());
        future.handleResponse(new Response(69));
        assertTrue(future.isDone());
    }

    @Test
    public void requireThatResponseBecomesAvailable() throws Exception {
        FutureResponse future = new FutureResponse();
        try {
            future.get(0, TimeUnit.MILLISECONDS);
            fail();
        } catch (TimeoutException e) {

        }
        Response response = new Response(Response.Status.OK);
        future.handleResponse(response);
        assertSame(response, future.get(0, TimeUnit.MILLISECONDS));
    }

    @Test
    public void requireThatResponseContentIsReturnedToCaller() throws Exception {
        ContentChannel content = new NonWorkingContentChannel();
        FutureResponse future = new FutureResponse(content);
        Response response = new Response(Response.Status.OK);
        assertSame(content, future.handleResponse(response));
    }

    @Test
    public void requireThatResponseCanBeListenedTo() throws InterruptedException {
        FutureResponse response = new FutureResponse();
        RunnableLatch listener = new RunnableLatch();
        response.addListener(listener, MoreExecutors.directExecutor());
        assertFalse(listener.await(100, TimeUnit.MILLISECONDS));
        response.handleResponse(new Response(Response.Status.OK));
        assertTrue(listener.await(600, TimeUnit.SECONDS));
    }
}
