// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi.resource;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.document.restapi.OperationHandler;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

public class RestApiMaxThreadTest {
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicInteger requestsInFlight = new AtomicInteger(0);
    private  class RestApiMocked extends RestApi {

        public RestApiMocked() {
            super(mock(Executor.class), null, (OperationHandler)null, 20);
        }

        @Override
        protected HttpResponse handleInternal(HttpRequest request) {
            requestsInFlight.incrementAndGet();
            try {
                latch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return null;
        }
    }

    @Test
    public void testCallsAreThrottled() throws InterruptedException {
        RestApiMocked restApiMocked = new RestApiMocked();
        // Fire lots of requests.
        for (int x = 0; x < 30; x++) {
            new Thread(() -> restApiMocked.handle(null)).start();
        }
        // Wait for all threads to be used
        while (requestsInFlight.get() != 19) {
            Thread.sleep(1);
        }
        // A new request should be blocked.
        final HttpResponse response = restApiMocked.handle(null);
        assertThat(response.getStatus(), is(429));
        latch.countDown();
    }
}
