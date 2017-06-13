// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.apputil.communication.http;

import com.yahoo.vespa.clustercontroller.utils.communication.async.AsyncOperation;
import com.yahoo.vespa.clustercontroller.utils.communication.async.AsyncUtils;
import com.yahoo.vespa.clustercontroller.utils.communication.http.*;

import java.util.LinkedList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ApacheAsyncHttpClientTest {

    public static class Server {
        public class Request {
            HttpRequest request;
            Object result;
            String proxyHost;
            int proxyPort;
            long timeoutMs;

            public Request(HttpRequest r, String proxyHost, int proxyPort, long timeoutMs) {
                request = r;
                this.proxyHost = proxyHost;
                this.proxyPort = proxyPort;
                this.timeoutMs = timeoutMs;
            }

            public void answer(Object result) {
                synchronized (requests) {
                    this.result = result;
                    requests.notifyAll();
                }
            }
        }
        public final LinkedList<Request> requests = new LinkedList<>();

        public Request createRequest(HttpRequest r, String proxyHost, int proxyPort, long timeoutMs) {
            return new Request(r, proxyHost, proxyPort, timeoutMs);
        }

        public Request waitForRequest() {
            synchronized (requests) {
                while (true) {
                    if (!requests.isEmpty()) {
                        return requests.removeFirst();
                    }
                    try{ requests.wait(); } catch (InterruptedException e) {}
                }
            }
        }
    }

    private Server server = new Server();
    private int clientCount = 0;

    public class Client implements SyncHttpClient {
        String proxyHost;
        int proxyPort;
        long timeoutMs;
        private boolean running = true;

        public Client(String proxyHost, int proxyPort, long timeoutMs) {
            this.proxyHost = proxyHost;
            this.proxyPort = proxyPort;
            this.timeoutMs = timeoutMs;
        }

        @Override
        public HttpResult execute(HttpRequest r) {
            synchronized (server.requests) {
                Server.Request pair = server.createRequest(r, proxyHost, proxyPort, timeoutMs);
                server.requests.addLast(pair);
                server.requests.notifyAll();
                while (running) {
                    try{ server.requests.wait(); } catch (InterruptedException e) {}
                    if (pair.result != null) {
                        if (pair.result instanceof HttpResult) {
                            return (HttpResult) pair.result;
                        } else {
                            throw new RuntimeException((Exception) pair.result);
                        }
                    } else {
                    }
                }
            }
            return new HttpResult().setHttpCode(500, "Shutting down");
        }

        @Override
        public void close() {
            synchronized (server.requests) {
                running = false;
                server.requests.notifyAll();
            }
        }
    }

    public class ClientFactory implements ApacheAsyncHttpClient.SyncHttpClientFactory {
        @Override
        public SyncHttpClient createInstance(String proxyHost, int proxyPort, long timeoutMs) {
            ++clientCount;
            return new Client(proxyHost, proxyPort, timeoutMs);
        }
    }

    private Executor executor;

    @Before
    public void setUp() {
        executor = new ThreadPoolExecutor(10, 100, 100, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(1000));
    }

    @Test
    public void testOneInstancePerUniqueSettings() {
        HttpResult result = new HttpResult().setHttpCode(201, "This worked out good");
        ApacheAsyncHttpClient client = new ApacheAsyncHttpClient(executor, new ClientFactory());
        ProxyAsyncHttpClient<HttpResult> proxyClient = new ProxyAsyncHttpClient<>(client, "proxy", 8080);

            // A first request
        HttpRequest req = new HttpRequest().setHost("www.yahoo.com").setPath("/foo").setTimeout(400);
        AsyncOperation<HttpResult> op = proxyClient.execute(req);
        Server.Request r = server.waitForRequest();
        assertEquals(r.request.toString(true), "proxy", r.proxyHost);
        assertEquals(r.request.toString(true), 8080, r.proxyPort);
        assertEquals(r.request.toString(true), 80, r.request.getPort());
        assertEquals(r.request.toString(true), "www.yahoo.com", r.request.getHost());
        assertEquals(400, r.request.getTimeoutMillis());
        r.answer(result);
        AsyncUtils.waitFor(op);
        assertTrue(op.isDone());
        assertTrue(op.isSuccess());
        assertEquals(result, op.getResult());
        assertEquals(1, clientCount);

            // A second request should reuse first instance
        op = proxyClient.execute(req.clone());
        r = server.waitForRequest();
        r.answer(result);
        AsyncUtils.waitFor(op);
        assertEquals(1, clientCount);

            // Altering timeout create a new one
        op = proxyClient.execute(req.clone().setTimeout(800));
        r = server.waitForRequest();
        assertEquals(800, r.request.getTimeoutMillis());
        r.answer(result);
        AsyncUtils.waitFor(op);
        assertEquals(2, clientCount);

            // And altering proxy will create a new one
        ProxyAsyncHttpClient<HttpResult> proxyClient2 = new ProxyAsyncHttpClient<>(client, "proxy2", 8080);
        op = proxyClient2.execute(req.clone());
        r = server.waitForRequest();
        assertEquals(r.request.toString(true), "proxy2", r.proxyHost);
        r.answer(result);
        AsyncUtils.waitFor(op);
        assertEquals(3, clientCount);

            // And the old ones are still cached, even if port now is specified
        op = proxyClient.execute(req.clone().setPort(80));
        r = server.waitForRequest();
        assertEquals(r.request.toString(true), "proxy", r.proxyHost);
        assertEquals(r.request.toString(true), 8080, r.proxyPort);
        assertEquals(r.request.toString(true), 80, r.request.getPort());
        assertEquals(r.request.toString(true), "www.yahoo.com", r.request.getHost());
        assertEquals(400, r.request.getTimeoutMillis());
        r.answer(result);
        AsyncUtils.waitFor(op);
        assertEquals(3, clientCount);

        client.close();
    }

    @Test
    public void testFailingRequest() {
        ApacheAsyncHttpClient client = new ApacheAsyncHttpClient(executor, new ClientFactory());
        HttpRequest req = new HttpRequest();
        AsyncOperation<HttpResult> op = client.execute(req);
        Server.Request r = server.waitForRequest();
        r.answer(new IllegalStateException("Failed to run"));
        AsyncUtils.waitFor(op);
        assertEquals(false, op.isSuccess());
        assertTrue(op.getCause().getMessage(), op.getCause().getMessage().contains("Failed to run"));
    }

    @Test
    public void testClose() {
        ApacheAsyncHttpClient client = new ApacheAsyncHttpClient(executor, new ClientFactory());
        HttpRequest req = new HttpRequest();
        AsyncOperation<HttpResult> op = client.execute(req);
        Server.Request r = server.waitForRequest();
        client.close();
        r.answer(new HttpResult());
        AsyncUtils.waitFor(op);
        assertEquals(true, op.isSuccess());

        try{
            client.execute(req);
            assertTrue(false);
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Http client has been closed"));
        }
    }

    @Test
    public void testInvalidProxyRequest() {
        ApacheAsyncHttpClient client = new ApacheAsyncHttpClient(executor, new ClientFactory());
        HttpRequest req = new HttpRequest().setPath("foo");
        try{
            client.execute(req);
            assertTrue(false);
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("looks invalid"));
        }
    }

    @Test
    public void testNothingButGetCoverage() {
            // There is never any hash conflict for equals to be false in actual use
        HttpRequest r = new HttpRequest();
        new ApacheAsyncHttpClient.Settings(r.clone().setTimeout(15))
                .equals(new ApacheAsyncHttpClient.Settings(r));
            // Only actual container is meant to use this constructor
        ApacheAsyncHttpClient client = new ApacheAsyncHttpClient(executor);
        client.execute(new HttpRequest());
        client.close();
    }

}
