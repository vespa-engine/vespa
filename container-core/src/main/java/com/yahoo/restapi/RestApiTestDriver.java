// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.restapi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.jdisc.http.server.jetty.testutils.TestDriver;
import com.yahoo.jdisc.test.MockMetric;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.OptionalInt;
import java.util.concurrent.Executors;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * Test driver for {@link RestApi}
 *
 * @author bjorncs
 */
public class RestApiTestDriver implements AutoCloseable {

    private final RestApiRequestHandler<?> handler;
    private final TestDriver testDriver;

    private RestApiTestDriver(Builder builder) {
        this.handler = builder.handler;
        this.testDriver = builder.jdiscHttpServer ? TestDriver.newBuilder().withRequestHandler(builder.handler).build() : null;
    }

    public static Builder newBuilder(RestApiRequestHandler<?> handler) { return new Builder(handler); }

    @FunctionalInterface public interface RestApiRequestHandlerFactory { RestApiRequestHandler<?> create(ThreadedHttpRequestHandler.Context context); }
    public static Builder newBuilder(RestApiRequestHandlerFactory factory) { return new Builder(factory); }

    public static ThreadedHttpRequestHandler.Context createHandlerTestContext() {
        return new ThreadedHttpRequestHandler.Context(Executors.newSingleThreadExecutor(), new MockMetric());
    }

    public OptionalInt listenPort() {
        return testDriver != null ? OptionalInt.of(testDriver.server().getListenPort()) : OptionalInt.empty();
    }

    public RestApiRequestHandler<?> handler() { return handler; }
    public RestApi restApi() { return handler.restApi(); }
    public ObjectMapper jacksonJsonMapper() { return handler.restApi().jacksonJsonMapper(); }

    public HttpResponse executeRequest(HttpRequest request) { return handler.handle(request); }

    public InputStream requestContentOf(Object jacksonEntity) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        uncheck(() -> handler.restApi().jacksonJsonMapper().writeValue(out, jacksonEntity));
        return new ByteArrayInputStream(out.toByteArray());
    }

    public <T> T parseJacksonResponseContent(HttpResponse response, TypeReference<T> type) {
        return uncheck(() -> handler.restApi().jacksonJsonMapper().readValue(responseData(response), type));
    }

    public <T> T parseJacksonResponseContent(HttpResponse response, Class<T> type) {
        return uncheck(() -> handler.restApi().jacksonJsonMapper().readValue(responseData(response), type));
    }

    private static byte[] responseData(HttpResponse response) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        uncheck(() -> response.render(out));
        return out.toByteArray();
    }

    @Override
    public void close() throws Exception {
        if (testDriver != null) {
            testDriver.close();
        }
    }

    public static class Builder {
        private final RestApiRequestHandler<?> handler;
        private boolean jdiscHttpServer = false;

        private Builder(RestApiRequestHandler<?> handler) {
            this.handler = handler;
        }

        private Builder(RestApiRequestHandlerFactory factory) { this(factory.create(createHandlerTestContext())); }

        public Builder withJdiscHttpServer() { this.jdiscHttpServer = true; return this; }

        public RestApiTestDriver build() { return new RestApiTestDriver(this); }
    }

}
