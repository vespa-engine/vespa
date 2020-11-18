// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.google.common.annotations.Beta;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.handler.BufferedContentChannel;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ReadableContentChannel;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.test.TestDriver;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A helper for making tests creating jDisc requests and checking their responses.
 *
 * @author bratseth
 */
@Beta
public class RequestHandlerTestDriver implements AutoCloseable {

    private final TestDriver driver;

    private MockResponseHandler responseHandler = null;

    /** Creates this with a binding to "http://localhost/*" */
    public RequestHandlerTestDriver(RequestHandler handler) {
        this("http://localhost/*", handler);
    }

    public RequestHandlerTestDriver(String binding, RequestHandler handler) {
        driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        builder.serverBindings().bind(binding, handler);
        driver.activateContainer(builder);
    }

    @Override
    public void close() {
        if (responseHandler != null)
            responseHandler.readAll();
        assertTrue("Driver closed", driver.close());
    }

    /** Returns the jDisc level driver wrapped by this */
    public TestDriver jDiscDriver() { return driver; }

    /** Send a GET request */
    public MockResponseHandler sendRequest(String uri) {
        return sendRequest(uri, HttpRequest.Method.GET);
    }

    public MockResponseHandler sendRequest(String uri, HttpRequest.Method method) {
        return sendRequest(uri, method, "");
    }

    /** Send a POST request */
    public MockResponseHandler sendRequest(String uri, HttpRequest.Method method, String body) {
        return sendRequest(uri, method, ByteBuffer.wrap(body.getBytes(StandardCharsets.UTF_8)));
    }

    /** Send a POST request with defined content type */
    public MockResponseHandler sendRequest(String uri, HttpRequest.Method method, String body, String contentType) {
        return sendRequest(uri, method, ByteBuffer.wrap(body.getBytes(StandardCharsets.UTF_8)), contentType);
    }

    public MockResponseHandler sendRequest(String uri, HttpRequest.Method method, ByteBuffer body) {
        responseHandler = new MockResponseHandler();
        Request request = HttpRequest.newServerRequest(driver, URI.create(uri), method);
        request.context().put("contextVariable", 37); // TODO: Add a method for accepting a Request instead
        ContentChannel requestContent = request.connect(responseHandler);
        requestContent.write(body, null);
        requestContent.close(null);
        request.release();
        return responseHandler;
    }

    public MockResponseHandler sendRequest(String uri, HttpRequest.Method method, ByteBuffer body, String contentType) {
        responseHandler = new MockResponseHandler();
        Request request = HttpRequest.newServerRequest(driver, URI.create(uri), method);
        request.context().put("contextVariable", 37); // TODO: Add a method for accepting a Request instead
        request.headers().put(com.yahoo.jdisc.http.HttpHeaders.Names.CONTENT_TYPE, contentType);
        ContentChannel requestContent = request.connect(responseHandler);
        requestContent.write(body, null);
        requestContent.close(null);
        request.release();
        return responseHandler;
    }

    /** Replaces all occurrences of 0-9 digits by d's */
    public String censorDigits(String s) {
        return s.replaceAll("[0-9]","d");
    }

    /** Junit asserts are not available in the runtime dependencies */
    private static void assertTrue(String assertionMessage, boolean expectedTrue) {
        if ( ! expectedTrue)
            throw new RuntimeException("Assertion in ProcessingTestDriver failed: " + assertionMessage);
    }

    public static class MockResponseHandler implements ResponseHandler {

        private final CountDownLatch latch = new CountDownLatch(1);
        private final ReadableContentChannel content = new ReadableContentChannel();
        private final BufferedContentChannel buffer = new BufferedContentChannel();
        Response response = null;

        /** Blocks until there's a response (max 60 seconds). Returns this for chaining convenience */
        public MockResponseHandler awaitResponse() throws InterruptedException {
            assertTrue("Handler responded", latch.await(60, TimeUnit.SECONDS));
            return this;
        }

        /**
         * Read the next piece of data from this channel, blocking if needed.
         * If all data is already read, this returns null.
         */
        public String read() {
            ByteBuffer nextBuffer = content.read();
            if (nextBuffer == null) return null; // end of transmission
            return StandardCharsets.UTF_8.decode(nextBuffer).toString();
        }

        /** Returns the number of bytes available in the handler right now */
        public int available() {
            return content.available();
        }

        /**
         * Reads all data that will ever be produced by the channel attached to this, blocking as necessary.
         * Returns an empty string if there is no data.
         */
        public String readAll() {
            String next;
            StringBuilder responseString = new StringBuilder();
            while (null != (next = read()) )
                responseString.append(next);
            return responseString.toString();
        }

        /** Consumes all <i>currently</i> available data, or returns "" if no data is available right now. Never blocks. */
        public String readIfAvailable() {
            StringBuilder b = new StringBuilder();
            while (content.available()>0) {
                ByteBuffer nextBuffer = content.read();
                b.append(Charset.forName("utf-8").decode(nextBuffer));
            }
            return b.toString();
        }

        @Override
        public ContentChannel handleResponse(Response response) {
            this.response = response;
            latch.countDown();

            buffer.connectTo(this.content);
            return buffer;
        }

        public void clientClose() {
            buffer.close(null);
        }

        /** Returns the status code. Throws an exception if handleResponse is not called prior to calling this */
        public int getStatus() {
            return response.getStatus();
        }

        public Response getResponse() { return response; }

    }

}
