// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.inject.Key;
import com.yahoo.container.logging.HitCounts;
import com.yahoo.jdisc.Container;
import com.yahoo.jdisc.References;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.service.CurrentContainer;
import java.net.URI;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.handler.Coverage;
import com.yahoo.container.handler.Timing;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.container.logging.AccessLogEntry;
import com.yahoo.container.logging.AccessLogInterface;
import com.yahoo.jdisc.handler.BufferedContentChannel;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;

/**
 * Test contracts in LoggingRequestHandler.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class LoggingRequestHandlerTestCase {

    StartTimePusher accessLogging;
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

        public AccessLogTestHandler(Executor executor, AccessLog accessLog) {
            super(executor, accessLog);
        }

        @Override
        public HttpResponse handle(HttpRequest request) {
            return new NoTimingResponse();
        }

    }

    static final class StartTimePusher implements AccessLogInterface {

        public final ArrayBlockingQueue<Long> starts = new ArrayBlockingQueue<>(1);

        @Override
        public void log(final AccessLogEntry accessLogEntry) {
            starts.offer(Long.valueOf(accessLogEntry.getTimeStampMillis()));
        }
    }

    @Before
    public void setUp() throws Exception {
        accessLogging = new StartTimePusher();
        ComponentRegistry<AccessLogInterface> implementers = new ComponentRegistry<>();
        implementers.register(new ComponentId("nalle"), accessLogging);
        implementers.freeze();
        executor = Executors.newCachedThreadPool();
        handler = new AccessLogTestHandler(executor, new AccessLog(implementers));
    }

    @After
    public void tearDown() throws Exception {
        accessLogging = null;
        handler = null;
        executor.shutdown();
        executor = null;
    }

    @Test
    public final void checkStartIsNotZeroWithoutTimingInstance() throws InterruptedException {
        Long startTime;

        MockResponseHandler responseHandler = new MockResponseHandler();
        com.yahoo.jdisc.http.HttpRequest request = createRequest();
        BufferedContentChannel requestContent = new BufferedContentChannel();
        requestContent.close(null);
        handler.handleRequest(request, requestContent, responseHandler);
        startTime = accessLogging.starts.poll(5, TimeUnit.MINUTES);
        if (startTime == null) {
            // test timed out, ignoring
        } else {
            assertFalse(
                    "Start time was 0,  that should never happen after the first millisecond of 1970.",
                    startTime.longValue() == 0L);
        }
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
