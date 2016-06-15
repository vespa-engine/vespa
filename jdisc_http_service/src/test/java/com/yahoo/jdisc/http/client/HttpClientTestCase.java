// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.client;

import com.google.inject.AbstractModule;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.application.MetricConsumer;
import com.yahoo.jdisc.handler.RequestDispatch;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.HttpResponse;
import com.yahoo.jdisc.http.client.filter.FilterException;
import com.yahoo.jdisc.http.client.filter.ResponseFilter;
import com.yahoo.jdisc.http.client.filter.ResponseFilterContext;
import com.yahoo.jdisc.http.test.ClientTestDriver;
import com.yahoo.jdisc.http.test.RemoteServer;
import org.testng.annotations.Test;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.yahoo.jdisc.http.test.ClientTestDriver.newFilterModule;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertSame;
import static org.testng.AssertJUnit.assertTrue;
import static org.testng.AssertJUnit.fail;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class HttpClientTestCase extends AbstractClientTestCase {

    private static final int NUM_REQUESTS = 10;

    @Test(enabled = false)
    public void requireThatRequestCanBeSent() throws Exception {
        ClientTestDriver driver = ClientTestDriver.newInstance();
        for (int i = 0; i < NUM_REQUESTS; ++i) {
            assertRequest(driver.currentContainer(), driver.server(),
                          requestUri("/foo.html"),
                          requestHeaders(),
                          requestContent(),
                          expectedRequestChunks("POST /foo.html HTTP/1.1\r\n" +
                                                "Host: .+\r\n" +
                                                "Connection: keep-alive\r\n" +
                                                "Accept: .+/.+\r\n" +
                                                "User-Agent: JDisc/1.0\r\n" +
                                                "\r\n"),
                          responseChunks("HTTP/1.1 200 OK\r\n" +
                                         "Content-Type: text/plain; charset=UTF-8\r\n" +
                                         "\r\n"),
                          expectedResponseStatus(200),
                          expectedResponseMessage("OK"),
                          expectedResponseHeaders(),
                          expectedResponseContent());
        }
        assertTrue(driver.close());
    }

    @Test(enabled = false)
    public void requireThatRequestHeadersAreSent() throws Exception {
        ClientTestDriver driver = ClientTestDriver.newInstance();
        for (int i = 0; i < NUM_REQUESTS; ++i) {
            assertRequest(driver.currentContainer(), driver.server(),
                          requestUri("/status.html"),
                          requestHeaders(newHeader("foo", "bar")),
                          requestContent(),
                          expectedRequestChunks("POST /status.html HTTP/1.1\r\n" +
                                                "Host: .+\r\n" +
                                                "foo: bar\r\n" +
                                                "Connection: keep-alive\r\n" +
                                                "Accept: .+/.+\r\n" +
                                                "User-Agent: JDisc/1.0\r\n" +
                                                "\r\n"),
                          responseChunks("HTTP/1.1 200 OK\r\n" +
                                         "Content-Type: text/plain; charset=UTF-8\r\n" +
                                         "\r\n"),
                          expectedResponseStatus(200),
                          expectedResponseMessage("OK"),
                          expectedResponseHeaders(),
                          expectedResponseContent());
        }
        assertTrue(driver.close());
    }

    @Test(enabled = false)
    public void requireThatRequestContentIsSent() throws Exception {
        ClientTestDriver driver = ClientTestDriver.newInstance();
        for (int i = 0; i < NUM_REQUESTS; ++i) {
            assertRequest(driver.currentContainer(), driver.server(),
                          requestUri("/status.html"),
                          requestHeaders(),
                          requestContent("foo", "bar"),
                          expectedRequestChunks("POST /status.html HTTP/1.1\r\n" +
                                                "Host: .+\r\n" +
                                                "Connection: keep-alive\r\n" +
                                                "Accept: .+/.+\r\n" +
                                                "User-Agent: JDisc/1.0\r\n" +
                                                "Content-Length: 6\r\n" +
                                                "\r\n" +
                                                "foobar"),
                          responseChunks("HTTP/1.1 200 OK\r\n" +
                                         "Content-Type: text/plain; charset=UTF-8\r\n" +
                                         "\r\n"),
                          expectedResponseStatus(200),
                          expectedResponseMessage("OK"),
                          expectedResponseHeaders(),
                          expectedResponseContent());
        }
        assertTrue(driver.close());
    }

    @Test(enabled = false)
    public void requireThatRequestContentCanBeChunked() throws Exception {
        ClientTestDriver driver = ClientTestDriver.newInstance(new HttpClientConfig.Builder()
                                                                       .chunkedEncodingEnabled(true));
        for (int i = 0; i < NUM_REQUESTS; ++i) {
            assertRequest(driver.currentContainer(), driver.server(),
                          requestUri("/status.html"),
                          requestHeaders(),
                          requestContent("foo", "bar"),
                          expectedRequestChunks("POST /status.html HTTP/1.1\r\n" +
                                                "Host: .+\r\n" +
                                                "Connection: keep-alive\r\n" +
                                                "Accept: .+/.+\r\n" +
                                                "User-Agent: JDisc/1.0\r\n" +
                                                "Transfer-Encoding: chunked\r\n" +
                                                "\r\n",
                                                "3\r\nfoo\r\n",
                                                "3\r\nbar\r\n",
                                                "0\r\n\r\n"),
                          responseChunks("HTTP/1.1 200 OK\r\n" +
                                         "Content-Type: text/plain; charset=UTF-8\r\n" +
                                         "\r\n"),
                          expectedResponseStatus(200),
                          expectedResponseMessage("OK"),
                          expectedResponseHeaders(),
                          expectedResponseContent());
        }
        assertTrue(driver.close());
    }

    @Test(enabled = false)
    public void requireThatGetRequestsAreNeverChunked() throws Exception {
        final ClientTestDriver driver = ClientTestDriver.newInstance(new HttpClientConfig.Builder()
                                                                             .chunkedEncodingEnabled(true));
        for (int i = 0; i < NUM_REQUESTS; ++i) {
            Future<Response> future = new RequestDispatch() {

                @Override
                protected Request newRequest() {
                    return HttpRequest.newServerRequest(driver.currentContainer(),
                                                        driver.server().newRequestUri("/status.html"),
                                                        HttpRequest.Method.GET);
                }
            }.dispatch();
            assertRequest(driver.server(),
                          expectedRequestChunks("GET /status.html HTTP/1.1\r\n" +
                                                "Host: .+\r\n" +
                                                "Connection: keep-alive\r\n" +
                                                "Accept: .+/.+\r\n" +
                                                "User-Agent: JDisc/1.0\r\n" +
                                                "\r\n"),
                          responseChunks("HTTP/1.1 200 OK\r\n" +
                                         "Content-Type: text/plain; charset=UTF-8\r\n" +
                                         "\r\n"),
                          future);
        }
        assertTrue(driver.close());
    }

    @Test(enabled = false)
    public void requireThatTraceRequestsDoNotAcceptContent() throws Exception {
        final ClientTestDriver driver = ClientTestDriver.newInstance(new HttpClientConfig.Builder()
                                                                             .chunkedEncodingEnabled(true));
        RequestDispatch dispatch = new RequestDispatch() {

            @Override
            protected Request newRequest() {
                return HttpRequest.newServerRequest(driver.currentContainer(),
                                                    driver.server().newRequestUri("/status.html"),
                                                    HttpRequest.Method.TRACE);
            }

            @Override
            protected Iterable<ByteBuffer> requestContent() {
                return Arrays.asList(ByteBuffer.allocate(69));
            }
        };
        try {
            dispatch.dispatch();
            fail();
        } catch (UnsupportedOperationException e) {

        }
        assertRequest(driver.server(),
                      expectedRequestChunks("TRACE /status.html HTTP/1.1\r\n" +
                                            "Host: .+\r\n" +
                                            "Connection: keep-alive\r\n" +
                                            "Accept: .+/.+\r\n" +
                                            "User-Agent: JDisc/1.0\r\n" +
                                            "\r\n"),
                      responseChunks("HTTP/1.1 200 OK\r\n" +
                                     "Content-Type: text/plain; charset=UTF-8\r\n" +
                                     "\r\n"),
                      dispatch);
        assertTrue(driver.close());
    }

    @Test(enabled = false)
    public void requireThatResponseCodeIsRead() throws Exception {
        ClientTestDriver driver = ClientTestDriver.newInstance();
        for (int i = 0; i < NUM_REQUESTS; ++i) {
            assertRequest(driver.currentContainer(), driver.server(),
                          requestUri("/status.html"),
                          requestHeaders(),
                          requestContent(),
                          expectedRequestChunks("POST /status.html HTTP/1.1\r\n" +
                                                "Host: .+\r\n" +
                                                "Connection: keep-alive\r\n" +
                                                "Accept: .+/.+\r\n" +
                                                "User-Agent: JDisc/1.0\r\n" +
                                                "\r\n"),
                          responseChunks("HTTP/1.1 69 foo\r\n" +
                                         "Content-Type: text/plain; charset=UTF-8\r\n" +
                                         "\r\n"),
                          expectedResponseStatus(69),
                          expectedResponseMessage("foo"),
                          expectedResponseHeaders(),
                          expectedResponseContent());
        }
        assertTrue(driver.close());
    }

    @Test(enabled = false)
    public void requireThatResponseHeadersAreRead() throws Exception {
        ClientTestDriver driver = ClientTestDriver.newInstance();
        for (int i = 0; i < NUM_REQUESTS; ++i) {
            assertRequest(driver.currentContainer(), driver.server(),
                          requestUri("/status.html"),
                          requestHeaders(),
                          requestContent(),
                          expectedRequestChunks("POST /status.html HTTP/1.1\r\n" +
                                                "Host: .+\r\n" +
                                                "Connection: keep-alive\r\n" +
                                                "Accept: .+/.+\r\n" +
                                                "User-Agent: JDisc/1.0\r\n" +
                                                "\r\n"),
                          responseChunks("HTTP/1.1 200 OK\r\n" +
                                         "Content-Type: text/plain; charset=UTF-8\r\n" +
                                         "foo: bar\r\n" +
                                         "baz: cox\r\n" +
                                         "\r\n"),
                          expectedResponseStatus(200),
                          expectedResponseMessage("OK"),
                          expectedResponseHeaders(newHeader("foo", "bar"), newHeader("baz", "cox")),
                          expectedResponseContent());
        }
        assertTrue(driver.close());
    }

    @Test(enabled = false)
    public void requireThatResponseContentIsRead() throws Exception {
        ClientTestDriver driver = ClientTestDriver.newInstance();
        for (int i = 0; i < NUM_REQUESTS; ++i) {
            assertRequest(driver.currentContainer(), driver.server(),
                          requestUri("/status.html"),
                          requestHeaders(),
                          requestContent(),
                          expectedRequestChunks("POST /status.html HTTP/1.1\r\n" +
                                                "Host: .+\r\n" +
                                                "Connection: keep-alive\r\n" +
                                                "Accept: .+/.+\r\n" +
                                                "User-Agent: JDisc/1.0\r\n" +
                                                "\r\n"),
                          responseChunks("HTTP/1.1 200 OK\r\n" +
                                         "Content-Type: text/plain; charset=UTF-8\r\n" +
                                         "Content-Length: 6\r\n" +
                                         "\r\n" +
                                         "foobar"),
                          expectedResponseStatus(200),
                          expectedResponseMessage("OK"),
                          expectedResponseHeaders(),
                          expectedResponseContent("foobar"));
        }
        assertTrue(driver.close());
    }

    private void requireThatChunkedResponseContentIsRead() throws Exception {
        ClientTestDriver driver = ClientTestDriver.newInstance();
        for (int i = 0; i < NUM_REQUESTS; ++i) {
            assertRequest(driver.currentContainer(), driver.server(),
                          requestUri("/status.html"),
                          requestHeaders(),
                          requestContent(),
                          expectedRequestChunks("POST /status.html HTTP/1.1\r\n" +
                                                "Host: .+\r\n" +
                                                "Connection: keep-alive\r\n" +
                                                "Accept: .+/.+\r\n" +
                                                "User-Agent: JDisc/1.0\r\n" +
                                                "\r\n"),
                          responseChunks("HTTP/1.1 200 OK\r\n" +
                                         "Transfer-Encoding: chunked\r\n" +
                                         "Content-Type: text/plain; charset=UTF-8\r\n" +
                                         "Connection: keep-alive\r\n" +
                                         "\r\n",
                                         "3\r\nfoo\r\n",
                                         "3\r\nbar\r\n",
                                         "0\r\n\r\n"),
                          expectedResponseStatus(200),
                          expectedResponseMessage("OK"),
                          expectedResponseHeaders(),
                          expectedResponseContent("foo", "bar"));
        }
        assertTrue(driver.close());
    }

    @Test(enabled = false)
    public void requireThatRequestTimeoutCanOccur() throws Exception {
        final ClientTestDriver driver = ClientTestDriver.newInstance();
        Response response = new RequestDispatch() {

            @Override
            protected Request newRequest() {
                Request request = new Request(driver.currentContainer(), driver.server().connectionSpec());
                request.setTimeout(1, TimeUnit.MILLISECONDS);
                return request;
            }
        }.dispatch().get(60, TimeUnit.SECONDS);
        assertNotNull(response);
        assertEquals(Response.Status.REQUEST_TIMEOUT, response.getStatus());
        assertTrue(driver.close());
    }

    @Test(enabled = false)
    public void requireThatConnectionTimeoutCanOccur() throws Exception {
        final ClientTestDriver driver = ClientTestDriver.newInstance();
        Response response = new RequestDispatch() {

            @Override
            protected Request newRequest() {
                HttpRequest request = HttpRequest.newServerRequest(driver.currentContainer(),
                                                                   driver.server().connectionSpec());
                request.setConnectionTimeout(1, TimeUnit.MILLISECONDS);
                return request;
            }
        }.dispatch().get(60, TimeUnit.SECONDS);
        assertTrue(response instanceof HttpResponse);
        HttpResponse httpResponse = (HttpResponse)response;
        assertEquals(Response.Status.REQUEST_TIMEOUT, httpResponse.getStatus());
        assertEquals("java.util.concurrent.TimeoutException: No response received after 1", httpResponse.getMessage());
        assertTrue(driver.close());
    }

    @Test(enabled = false)
    public void requireThatMetricContextIsCachedPerServer() throws Exception {
        MyMetric metric = new MyMetric(new Metric.Context() {

        });
        ClientTestDriver driver = ClientTestDriver.newInstance(metric);
        RemoteServer server1 = driver.server();
        RemoteServer server2 = RemoteServer.newInstance();
        for (int i = 0; i < NUM_REQUESTS; ++i) {
            assertRequest(driver.currentContainer(), server1,
                          requestUri("/foo.html"),
                          requestHeaders(),
                          requestContent(),
                          expectedRequestChunks("POST /foo.html HTTP/1.1\r\n" +
                                                "Host: .+\r\n" +
                                                "Connection: keep-alive\r\n" +
                                                "Accept: .+/.+\r\n" +
                                                "User-Agent: JDisc/1.0\r\n" +
                                                "\r\n"),
                          responseChunks("HTTP/1.1 200 OK\r\n" +
                                         "Content-Type: text/plain; charset=UTF-8\r\n" +
                                         "\r\n"),
                          expectedResponseStatus(200),
                          expectedResponseMessage("OK"),
                          expectedResponseHeaders(),
                          expectedResponseContent());
            assertRequest(driver.currentContainer(), server2,
                          requestUri("/foo.html"),
                          requestHeaders(),
                          requestContent(),
                          expectedRequestChunks("POST /foo.html HTTP/1.1\r\n" +
                                                "Host: .+\r\n" +
                                                "Connection: keep-alive\r\n" +
                                                "Accept: .+/.+\r\n" +
                                                "User-Agent: JDisc/1.0\r\n" +
                                                "\r\n"),
                          responseChunks("HTTP/1.1 200 OK\r\n" +
                                         "Content-Type: text/plain; charset=UTF-8\r\n" +
                                         "\r\n"),
                          expectedResponseStatus(200),
                          expectedResponseMessage("OK"),
                          expectedResponseHeaders(),
                          expectedResponseContent());
        }
        assertTrue(driver.close());
        assertTrue(server2.close(60, TimeUnit.SECONDS));
        assertEquals(2, metric.numContexts.get());
        assertTrue(metric.numCalls.get() > 0);
    }

    @Test(enabled = false)
    public void requireThatNullMetricContextIsLegal() throws Exception {
        MyMetric metric = new MyMetric(null);
        ClientTestDriver driver = ClientTestDriver.newInstance(metric);
        for (int i = 0; i < NUM_REQUESTS; ++i) {
            assertRequest(driver.currentContainer(), driver.server(),
                          requestUri("/foo.html"),
                          requestHeaders(),
                          requestContent(),
                          expectedRequestChunks("POST /foo.html HTTP/1.1\r\n" +
                                                "Host: .+\r\n" +
                                                "Connection: keep-alive\r\n" +
                                                "Accept: .+/.+\r\n" +
                                                "User-Agent: JDisc/1.0\r\n" +
                                                "\r\n"),
                          responseChunks("HTTP/1.1 200 OK\r\n" +
                                         "Content-Type: text/plain; charset=UTF-8\r\n" +
                                         "\r\n"),
                          expectedResponseStatus(200),
                          expectedResponseMessage("OK"),
                          expectedResponseHeaders(),
                          expectedResponseContent());
        }
        assertTrue(driver.close());
        assertEquals(1, metric.numContexts.get());
        assertTrue(metric.numCalls.get() > 0);
    }

    @Test(enabled = false)
    public void requireThatUnsupportedURISchemeThrowsException() throws Exception {
        final ClientTestDriver driver = ClientTestDriver.newInstance(new HttpClientConfig.Builder()
                                                                             .chunkedEncodingEnabled(true)
                                                                             .connectionPoolEnabled(false));

        try {
            new RequestDispatch() {
                @Override
                public Request newRequest() {
                    return HttpRequest.newServerRequest(
                            driver.currentContainer(),
                            URI.create("ftp://localhost/"),
                            HttpRequest.Method.GET);
                }
            }.dispatch();
            fail();
        } catch (UnsupportedOperationException e) {
            assertEquals("Unknown protocol: ftp", e.getMessage());
        }

        assertTrue(driver.close());
    }

    @Test(enabled = false)
    public void requireThatResponseFilterIsInvoked() throws Exception {
        final CountDownLatch filterInvokeCount = new CountDownLatch(2);
        final StringBuffer responseBuffer = new StringBuffer();
        ResponseFilter[] filters = new ResponseFilter[2];
        filters[0] = (new ResponseFilter() {

            @Override
            public ResponseFilterContext filter(ResponseFilterContext filterContext) {
                filterInvokeCount.countDown();
                return filterContext;
            }
        });
        filters[1] = (new ResponseFilter() {

            @Override
            public ResponseFilterContext filter(ResponseFilterContext filterContext) {
                filterInvokeCount.countDown();
                responseBuffer.append(filterContext.getRequestURI().getHost())
                        .append(filterContext.getRequestURI().getPath())
                        .append(filterContext.getResponseStatusCode())
                        .append(filterContext.getResponseFirstHeader("Content-Type"))
                        .append(filterContext.getRequestContext().get("key1"))
                        .append(filterContext.getRequestContext().get("key2"));
                return filterContext;
            }
        });
        Map<String, Object> context = new HashMap<>();
        context.put("key1", "value1");
        context.put("key2", "value2");
        ClientTestDriver driver = ClientTestDriver.newInstance(newFilterModule(filters));
        assertRequest(driver.currentContainer(), driver.server(),
                requestUri("/foo.html"),
                requestHeaders(),
                requestContent(),
                expectedRequestChunks("POST /foo.html HTTP/1.1\r\n" +
                        "Host: .+\r\n" +
                        "Connection: keep-alive\r\n" +
                        "Accept: .+/.+\r\n" +
                        "User-Agent: JDisc/1.0\r\n" +
                        "\r\n"),
                responseChunks("HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/plain; charset=UTF-8\r\n" +
                        "\r\n"),
                expectedResponseStatus(200),
                expectedResponseMessage("OK"),
                expectedResponseHeaders(),
                expectedResponseContent(),
                context);

        filterInvokeCount.await(60, TimeUnit.SECONDS);
        assertEquals(0, filterInvokeCount.getCount());
        assertEquals("localhost/foo.html200text/plain; charset=UTF-8value1value2", responseBuffer.toString());
        assertTrue(driver.close());
    }

    @Test(enabled = false)
    public void requireThatResponseFilterHandlesFilterExceptionProperly() throws Exception {
        ResponseFilter filter = new ResponseFilter() {

            @Override
            public ResponseFilterContext filter(ResponseFilterContext filterContext) throws FilterException {
                throw new FilterException("Request aborted.");
            }
        };
        ClientTestDriver driver = ClientTestDriver.newInstance(newFilterModule(filter));
        assertRequest(driver.currentContainer(), driver.server(),
                requestUri("/foo.html"),
                requestHeaders(),
                requestContent(),
                expectedRequestChunks(),
                responseChunks("HTTP/1.1 400 \r\n" +
                        "\r\n"),
                expectedResponseStatus(400),
                expectedResponseMessage("Request aborted."),
                expectedResponseHeaders(),
                expectedResponseContent());
        assertTrue(driver.close());
    }

    private static class MyMetric extends AbstractModule implements MetricConsumer {

        final AtomicInteger numContexts = new AtomicInteger(0);
        final AtomicInteger numCalls = new AtomicInteger(0);
        final Metric.Context context;

        MyMetric(Metric.Context context) {
            this.context = context;
        }

        @Override
        protected void configure() {
            bind(MetricConsumer.class).toInstance(this);
        }

        @Override
        public void set(String key, Number val, Metric.Context context) {
            assertSame(this.context, context);
            numCalls.incrementAndGet();
        }

        @Override
        public void add(String key, Number val, Metric.Context context) {
            assertSame(this.context, context);
            numCalls.incrementAndGet();
        }

        @Override
        public Metric.Context createContext(Map<String, ?> properties) {
            numContexts.incrementAndGet();
            return context;
        }
    }
}
