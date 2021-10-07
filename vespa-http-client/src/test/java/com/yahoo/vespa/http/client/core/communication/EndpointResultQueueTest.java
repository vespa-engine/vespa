// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.communication;

import com.yahoo.vespa.http.client.Result;
import com.yahoo.vespa.http.client.config.Endpoint;
import com.yahoo.vespa.http.client.core.EndpointResult;
import com.yahoo.vespa.http.client.core.operationProcessor.OperationProcessor;
import org.junit.Test;

import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * @author Einar M R Rosenvinge
 */
public class EndpointResultQueueTest {

    @Test
    public void testBasics() {
        Endpoint endpoint = Endpoint.create("a");

        GatewayConnection connection = new DryRunGatewayConnection(endpoint, Clock.systemUTC());
        OperationProcessor mockAggregator = mock(OperationProcessor.class);
        final AtomicInteger resultCount = new AtomicInteger(0);

        doAnswer(invocationOnMock -> {
            resultCount.getAndIncrement();
            return null;
        }).when(mockAggregator).resultReceived(any(), eq(0));

        EndpointResultQueue q = new EndpointResultQueue(
                mockAggregator, endpoint, 0, new ScheduledThreadPoolExecutor(1), 100L * 1000L);

        q.operationSent("op1", connection);
        assertThat(q.getPendingSize(), is(1));
        q.operationSent("op2", connection);
        assertThat(q.getPendingSize(), is(2));
        q.operationSent("op3", connection);
        assertThat(q.getPendingSize(), is(3));
        q.resultReceived(new EndpointResult("op1", new Result.Detail(endpoint)), 0);
        assertThat(q.getPendingSize(), is(2));
        q.resultReceived(new EndpointResult("op2", new Result.Detail(endpoint)), 0);
        assertThat(q.getPendingSize(), is(1));
        q.resultReceived(new EndpointResult("op3", new Result.Detail(endpoint)), 0);
        assertThat(q.getPendingSize(), is(0));
        q.resultReceived(new EndpointResult("op1", new Result.Detail(endpoint)), 0);
        assertThat(q.getPendingSize(), is(0));
        q.resultReceived(new EndpointResult("abc", new Result.Detail(endpoint)), 0);
        assertThat(q.getPendingSize(), is(0));

        assertThat(resultCount.get(), is(5));

        q.operationSent("op4", connection);
        assertThat(q.getPendingSize(), is(1));
        q.operationSent("op5", connection);
        assertThat(q.getPendingSize(), is(2));

        q.failPending(new RuntimeException());

        assertThat(resultCount.get(), is(7));
    }


    @Test
    public void testTimeout() throws InterruptedException {
        Endpoint endpoint = Endpoint.create("a");
        OperationProcessor mockAggregator = mock(OperationProcessor.class);
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocationOnMock -> {
            latch.countDown();
            return null;
        }).when(mockAggregator).resultReceived(any(), eq(0));
        EndpointResultQueue q = new EndpointResultQueue(
                mockAggregator, endpoint, 0, new ScheduledThreadPoolExecutor(1), 100L);
        q.operationSent("1234", new DryRunGatewayConnection(endpoint, Clock.systemUTC()));
        assert(latch.await(120, TimeUnit.SECONDS));
    }

}
