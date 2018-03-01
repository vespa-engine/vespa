// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.communication.http;

import com.yahoo.vespa.clustercontroller.utils.communication.async.AsyncCallback;
import com.yahoo.vespa.clustercontroller.utils.communication.async.AsyncOperation;
import com.yahoo.vespa.clustercontroller.utils.communication.async.AsyncOperationImpl;
import org.junit.Test;

import java.util.LinkedList;

import static org.junit.Assert.assertEquals;

public class RequestQueueTest {

    public static class Request {
        public final HttpRequest request;
        public final AsyncOperationImpl<HttpResult> result;

        public Request(HttpRequest r, AsyncOperationImpl<HttpResult> rr) {
            this.request = r;
            this.result = rr;
        }
    }

    public class TestClient implements AsyncHttpClient<HttpResult> {
        LinkedList<Request> requests = new LinkedList<>();
        @Override
        public AsyncOperation<HttpResult> execute(HttpRequest r) {
            Request p = new Request(r, new AsyncOperationImpl<HttpResult>(r.toString()));
            synchronized (requests) {
                requests.addLast(p);
            }
            return p.result;
        }
        @Override
        public void close() {}
    };

    @Test
    public void testNormalUsage() {
        TestClient client = new TestClient();
        RequestQueue<HttpResult> queue = new RequestQueue<>(client, 4);
        final LinkedList<HttpResult> results = new LinkedList<>();
        for (int i=0; i<10; ++i) {
            queue.schedule(new HttpRequest().setPath("/" + i), new AsyncCallback<HttpResult>() {
                @Override
                public void done(AsyncOperation<HttpResult> op) {
                    if (op.isSuccess()) {
                        results.add(op.getResult());
                    } else {
                        results.add(new HttpResult().setHttpCode(500, op.getCause().getMessage()));
                    }
                }
            });
        }
        assertEquals(4, client.requests.size());
        for (int i=0; i<3; ++i) {
            Request p = client.requests.removeFirst();
            p.result.setResult(new HttpResult());
            assertEquals(true, results.getLast().isSuccess());
        }
        assertEquals(4, client.requests.size());
        for (int i=0; i<7; ++i) {
            Request p = client.requests.removeFirst();
            p.result.setFailure(new Exception("Fail"));
            assertEquals(false, results.getLast().isSuccess());
        }
        assertEquals(0, client.requests.size());
        assertEquals(true, queue.empty());
        assertEquals(10, results.size());
    }

    public class Waiter implements Runnable {
        boolean waiting = false;
        boolean completed = false;
        RequestQueue<HttpResult> queue;
        Waiter(RequestQueue<HttpResult> queue) {
            this.queue = queue;
        }
        public void run() {
            try{
                waiting = true;
                queue.waitUntilEmpty();
            } catch (InterruptedException e) { throw new Error(e); }
            completed = true;
        }
    }

    @Test
    public void testWaitUntilEmpty() throws Exception {
        TestClient client = new TestClient();
        RequestQueue<HttpResult> queue = new RequestQueue<>(client, 4);
        final LinkedList<HttpResult> result = new LinkedList<>();
        queue.schedule(new HttpRequest().setPath("/foo"), new AsyncCallback<HttpResult>() {
            @Override
            public void done(AsyncOperation<HttpResult> op) {
                result.add(op.getResult());
            }
        });
        Waiter waiter = new Waiter(queue);
        Thread thread = new Thread(waiter);
        thread.start();
        while (!waiter.waiting) {
            Thread.sleep(1);
        }
        assertEquals(0, result.size());
        client.requests.getFirst().result.setResult(new HttpResult());
        while (!waiter.completed) {
            Thread.sleep(1);
        }
        assertEquals(1, result.size());
    }

}
