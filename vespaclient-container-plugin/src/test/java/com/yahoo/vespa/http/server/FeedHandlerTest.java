// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;
import com.yahoo.container.handler.threadpool.ContainerThreadPool;
import com.yahoo.container.jdisc.RequestHandlerTestDriver;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.jdisc.handler.OverloadException;
import com.yahoo.metrics.simple.MetricReceiver;
import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static com.yahoo.vespa.http.server.FeedHandlerV3Test.createRequest;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author bjorncs
 */
public class FeedHandlerTest {

    @Test
    public void response_has_status_code_429_when_throttling() {
        FeedHandler handler = new FeedHandler(
                new RejectingContainerThreadpool(),
                new CollectingMetric(),
                new DocumentTypeManager(new DocumentmanagerConfig.Builder().build()),
                null /* session cache */,
                MetricReceiver.nullImplementation);
        var responseHandler = new RequestHandlerTestDriver.MockResponseHandler();
        try {
            handler.handleRequest(createRequest(100).getJDiscRequest(), responseHandler);
            fail();
        } catch (OverloadException e) {}
        assertEquals(429, responseHandler.getStatus());
    }

    private static class RejectingContainerThreadpool implements ContainerThreadPool {
        private final Executor executor = ignored -> { throw new RejectedExecutionException(); };

        @Override public Executor executor() { return executor;  }
    }

}
