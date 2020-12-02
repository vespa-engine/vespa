// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.apputil.communication.http;

import com.yahoo.vespa.clustercontroller.utils.communication.http.HttpRequest;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * The handler is mostly tested through the apache tests, using it as endpoint here..
 * This test class is just to test some special cases.
 */
public class JDiscHttpRequestHandlerTest {

    private ThreadPoolExecutor executor;

    @Before
    public void setUp() {
        executor = new ThreadPoolExecutor(10, 100, 100, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1000));
    }

    public void tearDown() {
        executor.shutdown();
    }

    @Test
    public void testInvalidMethod() throws Exception {
        try{
            HttpRequest request = new HttpRequest();
            JDiscHttpRequestHandler.setOperation(request, com.yahoo.jdisc.http.HttpRequest.Method.CONNECT);
            fail("Control should not reach here");
        } catch (IllegalStateException e) {
            assertEquals("Unhandled method CONNECT", e.getMessage());
        }
    }

    @Test
    public void testNothingButAddCoverage() throws Exception {
        new JDiscHttpRequestHandler.EmptyCompletionHandler().failed(null);
    }
}
