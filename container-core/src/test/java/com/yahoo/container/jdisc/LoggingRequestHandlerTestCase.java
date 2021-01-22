// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.google.inject.Key;
import com.yahoo.container.handler.Coverage;
import com.yahoo.container.handler.Timing;
import com.yahoo.container.logging.HitCounts;
import com.yahoo.jdisc.Container;
import com.yahoo.jdisc.References;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.service.CurrentContainer;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.fail;

/**
 * Test contracts in LoggingRequestHandler.
 *
 * @author Steinar Knutsen
 */
public class LoggingRequestHandlerTestCase {

    AccessLogTestHandler handler;
    ExecutorService executor;

    public static final class NoTimingResponse extends ExtendedResponse {

        public NoTimingResponse() {
            super(200);
        }


        @Override
        public HitCounts getHitCounts() {
            return new HitCounts(1, 1, 1, 1, 1,
                    getCoverage().toLoggingCoverage());
        }

        @Override
        public Timing getTiming() {
            return null;
        }

        @Override
        public Coverage getCoverage() {
            return new Coverage(1, 1, true);
        }


        @Override
        public void render(OutputStream output, ContentChannel networkChannel,
                CompletionHandler handler) throws IOException {
            networkChannel.close(handler);
        }
    }

    static class CloseableContentChannel implements ContentChannel {

        @Override
        public void write(ByteBuffer buf, CompletionHandler handler) {
            if (handler != null) {
                handler.completed();
            }
        }

        @Override
        public void close(CompletionHandler handler) {
            if (handler != null) {
                handler.completed();
            }
        }

    }

    public static final class MockResponseHandler implements ResponseHandler {
        public final ContentChannel channel = new CloseableContentChannel();

        @Override
        public ContentChannel handleResponse(
                final com.yahoo.jdisc.Response response) {
            return channel;
        }
    }

    static final class AccessLogTestHandler extends LoggingRequestHandler {

        public AccessLogTestHandler(Executor executor) {
            super(executor);
        }

        @Override
        public HttpResponse handle(HttpRequest request) {
            return new NoTimingResponse();
        }

    }

    @Before
    public void setUp() throws Exception {
        executor = Executors.newCachedThreadPool();
        handler = new AccessLogTestHandler(executor);
    }

    @After
    public void tearDown() throws Exception {
        handler = null;
        executor.shutdown();
        executor = null;
    }

    public static com.yahoo.jdisc.http.HttpRequest createRequest() {
        return createRequest("http://localhost/search/?query=geewhiz");
    }

    public static com.yahoo.jdisc.http.HttpRequest createRequest(String uri) {
        com.yahoo.jdisc.http.HttpRequest request = null;
        try {
            request = com.yahoo.jdisc.http.HttpRequest.newClientRequest(new com.yahoo.jdisc.Request(new MockCurrentContainer(), new URI(uri)), new URI(uri),
                                                   com.yahoo.jdisc.http.HttpRequest.Method.GET, com.yahoo.jdisc.http.HttpRequest.Version.HTTP_1_1);
            request.setRemoteAddress(new InetSocketAddress(0));
        } catch (URISyntaxException e) {
            fail("Illegal URI string in test?");
        }
        return request;
    }

    private static class MockCurrentContainer implements CurrentContainer {
        @Override
        public Container newReference(java.net.URI uri) {
            return new Container() {

                @Override
                public RequestHandler resolveHandler(com.yahoo.jdisc.Request request) {
                    return null;
                }

                @Override
                public <T> T getInstance(Key<T> tKey) {
                    return null;
                }

                @Override
                public <T> T getInstance(Class<T> tClass) {
                    return null;
                }

                @Override
                public ResourceReference refer() {
                    return References.NOOP_REFERENCE;
                }

                @Override
                public void release() {
                    // NOP
                }

                @Override
                public long currentTimeMillis() {
                    return 37;
                }
            };
        }
    }

}
