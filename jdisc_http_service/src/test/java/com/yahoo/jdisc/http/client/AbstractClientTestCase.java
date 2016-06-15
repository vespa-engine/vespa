// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.client;

import com.yahoo.jdisc.HeaderFields;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.BufferedContentChannel;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.RequestDispatch;
import com.yahoo.jdisc.http.HttpResponse;
import com.yahoo.jdisc.http.test.RemoteServer;
import com.yahoo.jdisc.service.CurrentContainer;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.yahoo.jdisc.http.AssertHttp.assertChunk;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
abstract class AbstractClientTestCase {

    protected static void assertRequest(CurrentContainer container, RemoteServer server, String requestUri,
                                        HeaderFields requestHeaders, Iterable<ByteBuffer> requestContent,
                                        Iterable<String> expectedRequestChunks, Iterable<String> responseChunks,
                                        int expectedStatus, String expectedMessage,
                                        HeaderFields expectedResponseHeaders,
                                        Iterable<ByteBuffer> expectedResponseContent,
                                        Map<String,Object> context) throws Exception {
        MyRequestDispatch dispatch = new MyRequestDispatch(container, server.newRequestUri(requestUri),
                requestHeaders, requestContent, context);
        dispatch.dispatch();
        assertRequest(server, expectedRequestChunks, responseChunks, dispatch);
        assertResponse(dispatch.get(60, TimeUnit.SECONDS), expectedStatus, expectedMessage,
                expectedResponseHeaders);
        assertContent(expectedResponseContent, dispatch.responseContent.toReadable());
    }

    protected static void assertRequest(CurrentContainer container, RemoteServer server, String requestUri,
                                        HeaderFields requestHeaders, Iterable<ByteBuffer> requestContent,
                                        Iterable<String> expectedRequestChunks, Iterable<String> responseChunks,
                                        int expectedStatus, String expectedMessage,
                                        HeaderFields expectedResponseHeaders,
                                        Iterable<ByteBuffer> expectedResponseContent) throws Exception {

        assertRequest(container, server, requestUri, requestHeaders, requestContent, expectedRequestChunks,
                responseChunks, expectedStatus, expectedMessage, expectedResponseHeaders, expectedResponseContent,
                Collections.<String, Object>emptyMap());
    }

    protected static void assertRequest(RemoteServer server, Iterable<String> expectedRequestChunks,
                                        Iterable<String> responseChunks, Future<Response> futureResponse)
            throws Exception {
        RemoteServer.Connection cnt = awaitConnection(server, futureResponse);
        assertNotNull(cnt);
        for (String expected : expectedRequestChunks) {
            assertChunk(expected, cnt.readChunk());
        }
        for (String chunk : responseChunks) {
            cnt.writeChunk(chunk);
        }
        cnt.close();
    }

    protected static RemoteServer.Connection awaitConnection(RemoteServer server, Future<Response> futureResponse)
            throws Exception {
        RemoteServer.Connection cnt = null;
        for (int i = 0; i < 6000; ++i) {
            cnt = server.awaitConnection(10, TimeUnit.MILLISECONDS);
            if (cnt != null) {
                break;
            }
            if (futureResponse.isDone()) {
                HttpResponse response = (HttpResponse)futureResponse.get();
                System.err.println("Unexpected " + response.getStatus() + " response: " + response.getMessage());
                Throwable t = response.getError();
                if (t instanceof Exception) {
                    throw (Exception)t;
                } else if (t instanceof Error) {
                    throw (Error)t;
                } else {
                    throw new RuntimeException(t);
                }
            }
        }
        return cnt;
    }

    protected static void assertResponse(Response response, int expectedStatus, String expectedMessage,
                                         HeaderFields expectedHeaders) {
        assertTrue(response instanceof HttpResponse);
        HttpResponse httpResponse = (HttpResponse)response;
        assertEquals(expectedStatus, httpResponse.getStatus());
        assertEquals(expectedMessage, httpResponse.getMessage());

        HeaderFields headers = response.headers();
        for (Map.Entry<String, String> entry : expectedHeaders.entries()) {
            assertTrue(headers.contains(entry.getKey(), entry.getValue()));
        }
    }

    protected static void assertContent(Iterable<ByteBuffer> expected, Iterable<ByteBuffer> actual) {
        Iterator<ByteBuffer> expectedIt = expected.iterator();
        Iterator<ByteBuffer> actualIt = actual.iterator();
        while (expectedIt.hasNext()) {
            assertTrue(actualIt.hasNext());
            assertEquals(expectedIt.next(), actualIt.next());
        }
        assertFalse(actualIt.hasNext());
    }

    protected static String requestUri(String uri) {
        return uri;
    }

    protected static HeaderFields requestHeaders(HeaderEntry... entries) {
        return asHeaders(entries);
    }

    protected static Iterable<ByteBuffer> requestContent(String... chunks) {
        return asContent(chunks);
    }

    protected static Iterable<String> expectedRequestChunks(String... chunks) {
        return Arrays.asList(chunks);
    }

    protected static Iterable<String> responseChunks(String... chunks) {
        return Arrays.asList(chunks);
    }

    protected static int expectedResponseStatus(int status) {
        return status;
    }

    protected static String expectedResponseMessage(String message) {
        return message;
    }

    protected static HeaderFields expectedResponseHeaders(HeaderEntry... entries) {
        return asHeaders(entries);
    }

    protected static Iterable<ByteBuffer> expectedResponseContent(String... chunks) {
        return asContent(chunks);
    }

    protected static HeaderEntry newHeader(String key, String val) {
        return new HeaderEntry(key, val);
    }

    protected static HeaderFields asHeaders(HeaderEntry... entries) {
        HeaderFields ret = new HeaderFields();
        for (HeaderEntry entry : entries) {
            ret.add(entry.key, entry.val);
        }
        return ret;
    }

    protected static Iterable<ByteBuffer> asContent(String... chunks) {
        List<ByteBuffer> ret = new LinkedList<>();
        for (String chunk : chunks) {
            ret.add(ByteBuffer.wrap(chunk.getBytes(StandardCharsets.UTF_8)));
        }
        return ret;
    }

    protected static class MyRequestDispatch extends RequestDispatch {

        final Map<String, Object> context = new HashMap<>();
        final CurrentContainer container;
        final URI requestUri;
        final HeaderFields requestHeaders;
        final Iterable<ByteBuffer> requestContent;
        final BufferedContentChannel responseContent = new BufferedContentChannel();

        MyRequestDispatch(CurrentContainer container, URI requestUri, HeaderFields requestHeaders,
                          Iterable<ByteBuffer> requestContent, Map<String, Object> context) {
            this.container = container;
            this.requestUri = requestUri;
            this.requestHeaders = requestHeaders;
            this.requestContent = requestContent;
            this.context.putAll(context);
        }

        @Override
        protected Request newRequest() {
            Request request = new Request(container, requestUri);
            request.headers().addAll(requestHeaders);
            request.context().putAll(context);
            return request;
        }

        @Override
        protected Iterable<ByteBuffer> requestContent() {
            return requestContent;
        }

        @Override
        public ContentChannel handleResponse(Response response) {
            return responseContent;
        }
    }

    protected static class HeaderEntry {

        final String key;
        final String val;

        HeaderEntry(String key, String val) {
            this.key = key;
            this.val = val;
        }
    }
}
