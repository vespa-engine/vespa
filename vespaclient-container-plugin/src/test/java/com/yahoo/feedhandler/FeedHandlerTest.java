// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedhandler;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.jdisc.HeaderFields;
import com.yahoo.jdisc.Metric;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.vespa.http.client.core.Headers;
import com.yahoo.vespa.http.client.core.OperationStatus;
import com.yahoo.vespa.http.server.FeedHandler;
import com.yahoo.vespa.http.server.Feeder;
import org.junit.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for FeedHandler class.
 *
 * @author dybis
 */
public class FeedHandlerTest {

    /**
     * This class extends FeedHandler and allows to create a custom Feeder.
     */
    static class TestFeedHandler extends FeedHandler {
        private final CountDownLatch countDownLatch = new CountDownLatch(1);

        public TestFeedHandler() throws Exception {
            super(Executors.newCachedThreadPool(), null, null, mock(Metric.class), mock(AccessLog.class), null, MetricReceiver.nullImplementation);
        }

        /**
         * Builds a feeder that blocks until countDownLatch is stepped down.
         */
        @Override
        protected Feeder createFeeder(
                com.yahoo.container.jdisc.HttpRequest request,
                InputStream requestInputStream,
                final BlockingQueue<OperationStatus> operations,
                String clientId,
                boolean sessionIdWasGeneratedJustNow,
                int protocolVersion) throws Exception {
            Feeder feeder = mock(Feeder.class);
            doAnswer(invocation -> {
                try {
                    countDownLatch.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                return null;
            }).when(feeder).waitForRequestReceived();
            return feeder;
        }
    }

    /**
     * nginx require that a post is finished before the server ack with a response. This behaviour is verified
     * in this test
     */
    @Test
    public void testResponseIsSentAfterWaitForRequestReceivedReturns() throws Exception {
        HttpRequest request = mock(HttpRequest.class);

        // Create a request with valid version.
        com.yahoo.jdisc.http.HttpRequest jdiscRequest = mock(com.yahoo.jdisc.http.HttpRequest.class);
        HeaderFields headerFields = mock(HeaderFields.class);
        List<String> version = new ArrayList<>();
        version.add("2");
        when(headerFields.get(Headers.VERSION)).thenReturn(version);
        when(jdiscRequest.headers()).thenReturn(headerFields);
        when(request.getJDiscRequest()).thenReturn(jdiscRequest);

        TestFeedHandler feedHandler = new TestFeedHandler();
        // After a short period, make the feed finish.
        new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            feedHandler.countDownLatch.countDown();
        }).start();
        // This should not return before countdown latch is stepped down.
        feedHandler.handle(request);
        // This should always returns after the countDownLatch has become zero. This might cause false positive,
        // but not false negatives. This is fine.
        assertThat(feedHandler.countDownLatch.getCount(), is(0L));

    }

}
