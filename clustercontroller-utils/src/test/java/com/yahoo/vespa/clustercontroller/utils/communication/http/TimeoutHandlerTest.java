// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.communication.http;

import com.yahoo.vespa.clustercontroller.utils.communication.async.AsyncOperation;
import com.yahoo.vespa.clustercontroller.utils.communication.async.AsyncOperationImpl;
import com.yahoo.vespa.clustercontroller.utils.communication.async.AsyncUtils;
import com.yahoo.vespa.clustercontroller.utils.test.FakeClock;
import junit.framework.TestCase;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class TimeoutHandlerTest extends TestCase {

    public class TestClient implements AsyncHttpClient<HttpResult> {
        AsyncOperationImpl<HttpResult> lastOp;
        @Override
        public AsyncOperation<HttpResult> execute(HttpRequest r) {
            return lastOp = new AsyncOperationImpl<>("test");
        }
        @Override
        public void close() {}
    };

    private ThreadPoolExecutor executor;
    private TestClient client;
    private FakeClock clock;
    private TimeoutHandler<HttpResult> handler;

    public void setUp() {
        executor = new ThreadPoolExecutor(10, 100, 100, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(1000));
        clock = new FakeClock();
        client = new TestClient();
        handler = new TimeoutHandler<>(executor, clock, client);
    }

    public void tearDown() {
        handler.close();
        executor.shutdown();
    }

    public void testTimeout() {
        AsyncOperation<HttpResult> op = handler.execute(new HttpRequest().setTimeout(1000));
        assertFalse(op.isDone());
        clock.adjust(999);
            // Give it a bit of time for timeout handler to have a chance of timout out prematurely
        try{ Thread.sleep(1); } catch (InterruptedException e) {}
        assertFalse(op.isDone());
        clock.adjust(1);
        AsyncUtils.waitFor(op);
        assertTrue(op.isDone());
        assertFalse(op.isSuccess());
        assertTrue(op.getCause().getMessage(), op.getCause().getMessage().contains("Operation timeout"));
            // After timeout, finishing the original request no longer matter
        client.lastOp.setResult(new HttpResult());
        assertFalse(op.isSuccess());
        assertTrue(op.getCause().getMessage(), op.getCause().getMessage().contains("Operation timeout"));
    }

    public void testNoTimeout() {
        AsyncOperation<HttpResult> op = handler.execute(new HttpRequest().setTimeout(1000));
        clock.adjust(999);
        assertFalse(op.isDone());
        client.lastOp.setResult(new HttpResult().setContent("foo"));
        AsyncUtils.waitFor(op);
        assertTrue(op.isDone());
        assertTrue(op.isSuccess());
        assertEquals("foo", op.getResult().getContent());
    }

    public void testNoTimeoutFailing() {
        AsyncOperation<HttpResult> op = handler.execute(new HttpRequest().setTimeout(1000));
        clock.adjust(999);
        assertFalse(op.isDone());
        client.lastOp.setFailure(new Exception("foo"));
        AsyncUtils.waitFor(op);
        assertTrue(op.isDone());
        assertFalse(op.isSuccess());
        assertEquals("foo", op.getCause().getMessage());
    }

    public void testProvokeCompletedOpPurgeInTimeoutList() {
        AsyncOperation<HttpResult> op1 = handler.execute(new HttpRequest().setTimeout(1000));
        AsyncOperationImpl<HttpResult> op1Internal = client.lastOp;
        clock.adjust(300);
        AsyncOperation<HttpResult> op2 = handler.execute(new HttpRequest().setTimeout(1000));
        clock.adjust(300);
        op1Internal.setResult(new HttpResult().setContent("foo"));
        AsyncUtils.waitFor(op1);
        clock.adjust(800);
        AsyncUtils.waitFor(op2);
        assertEquals(true, op1.isDone());
        assertEquals(true, op2.isDone());
        assertEquals(true, op1.isSuccess());
        assertEquals(false, op2.isSuccess());
    }

    public void testNothingButGetCoverage() {
        AsyncOperation<HttpResult> op = handler.execute(new HttpRequest().setTimeout(1000));
        op.getProgress();
        op.cancel();
        assertFalse(op.isCanceled()); // Cancel not currently supported
        client.lastOp.setResult(new HttpResult().setContent("foo"));
        AsyncUtils.waitFor(op);
        op.getProgress();
        op = handler.execute(new HttpRequest().setTimeout(1000));
        handler.performTimeoutHandlerTick();
        handler.performTimeoutHandlerTick();
        client.lastOp.setResult(new HttpResult().setContent("foo"));
        AsyncUtils.waitFor(op);
    }
}
