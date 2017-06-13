// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler;

import com.google.inject.Key;
import com.yahoo.container.core.VipStatusConfig;
import com.yahoo.jdisc.Container;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.References;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.handler.BufferedContentChannel;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ReadableContentChannel;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.service.CurrentContainer;
import com.yahoo.text.Utf8;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Check semantics of VIP status handler. Do note this handler does not need to
 * care about the incoming URI, that's 100% handled in JDIsc by the binding
 * pattern.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class VipStatusHandlerTestCase {

    public static final class MockResponseHandler implements ResponseHandler {
        final ReadableContentChannel channel = new ReadableContentChannel();

        @Override
        public ContentChannel handleResponse(
                final com.yahoo.jdisc.Response response) {
            return channel;
        }
    }

    Metric metric = Mockito.mock(Metric.class);

    @Test
    public final void testHandleRequest() {
        final VipStatusConfig config = new VipStatusConfig(new VipStatusConfig.Builder().accessdisk(false)
                .noSearchBackendsImpliesOutOfService(false));
        final VipStatusHandler handler = new VipStatusHandler(Executors.newCachedThreadPool(), config, metric);
        final MockResponseHandler responseHandler = new MockResponseHandler();
        final HttpRequest request = createRequest();
        final BufferedContentChannel requestContent = createChannel();
        handler.handleRequest(request, requestContent, responseHandler);
        final ByteBuffer b = responseHandler.channel.read();
        final byte[] asBytes = new byte[b.remaining()];
        b.get(asBytes);
        assertEquals(VipStatusHandler.OK_MESSAGE, Utf8.toString(asBytes));
    }

    public static final class NotFoundResponseHandler implements
            ResponseHandler {
        final ReadableContentChannel channel = new ReadableContentChannel();

        @Override
        public ContentChannel handleResponse(
                final com.yahoo.jdisc.Response response) {
            assertEquals(com.yahoo.jdisc.Response.Status.NOT_FOUND,
                    response.getStatus());
            return channel;
        }
    }

    @Test
    public final void testFileNotFound() {
        final VipStatusConfig config = new VipStatusConfig(new VipStatusConfig.Builder().accessdisk(true)
                .statusfile("/VipStatusHandlerTestCaseFileThatReallyReallyShouldNotExist")
                .noSearchBackendsImpliesOutOfService(false));
        final VipStatusHandler handler = new VipStatusHandler(Executors.newCachedThreadPool(), config, metric);
        final NotFoundResponseHandler responseHandler = new NotFoundResponseHandler();
        final HttpRequest request = createRequest();
        final BufferedContentChannel requestContent = createChannel();
        handler.handleRequest(request, requestContent, responseHandler);
        final ByteBuffer b = responseHandler.channel.read();
        final byte[] asBytes = new byte[b.remaining()];
        b.get(asBytes);
        assertEquals(
                VipStatusHandler.StatusResponse.COULD_NOT_FIND_STATUS_FILE,
                Utf8.toString(asBytes));
    }

    @Test
    public final void testFileFound() throws IOException {
        final File statusFile = File.createTempFile("VipStatusHandlerTestCase",
                null);
        try {
            final FileWriter writer = new FileWriter(statusFile);
            final String OK = "OK\n";
            writer.write(OK);
            writer.close();
            final VipStatusConfig config = new VipStatusConfig(new VipStatusConfig.Builder().accessdisk(true)
                    .statusfile(statusFile.getAbsolutePath()).noSearchBackendsImpliesOutOfService(false));
            final VipStatusHandler handler = new VipStatusHandler(Executors.newCachedThreadPool(), config, metric);
            final MockResponseHandler responseHandler = new MockResponseHandler();
            final HttpRequest request = createRequest();
            final BufferedContentChannel requestContent = createChannel();
            handler.handleRequest(request, requestContent, responseHandler);
            final ByteBuffer b = responseHandler.channel.read();
            final byte[] asBytes = new byte[b.remaining()];
            b.get(asBytes);
            assertEquals(OK, Utf8.toString(asBytes));
        } finally {
            statusFile.delete();
        }
    }

    @Test
    public final void testProgrammaticallyRemovedFromRotation() throws IOException {
        VipStatus vipStatus = new VipStatus();
        final VipStatusConfig config = new VipStatusConfig(new VipStatusConfig.Builder().accessdisk(false)
                .noSearchBackendsImpliesOutOfService(true));
        final VipStatusHandler handler = new VipStatusHandler(Executors.newCachedThreadPool(), config,  metric, vipStatus);

        vipStatus.removeFromRotation(this);

        {
            final MockResponseHandler responseHandler = new MockResponseHandler();
            final HttpRequest request = createRequest();
            final BufferedContentChannel requestContent = createChannel();
            handler.handleRequest(request, requestContent, responseHandler);
            final ByteBuffer b = responseHandler.channel.read();
            final byte[] asBytes = new byte[b.remaining()];
            b.get(asBytes);
            assertEquals(VipStatusHandler.StatusResponse.NO_SEARCH_BACKENDS, Utf8.toString(asBytes));
        }

        vipStatus.addToRotation(this);

        {
            final MockResponseHandler responseHandler = new MockResponseHandler();
            final HttpRequest request = createRequest();
            final BufferedContentChannel requestContent = createChannel();
            handler.handleRequest(request, requestContent, responseHandler);
            final ByteBuffer b = responseHandler.channel.read();
            final byte[] asBytes = new byte[b.remaining()];
            b.get(asBytes);
            assertEquals(VipStatusHandler.OK_MESSAGE, Utf8.toString(asBytes));
        }
    }

    public static HttpRequest createRequest() {
        return createRequest("http://localhost/search/?query=geewhiz");
    }

    public static HttpRequest createRequest(String uri) {
        HttpRequest request = null;
        try {
            request = HttpRequest.newClientRequest(new com.yahoo.jdisc.Request(
                                                           new MockCurrentContainer(), new URI(uri)), new URI(uri),
                                                   HttpRequest.Method.GET, HttpRequest.Version.HTTP_1_1);
            request.setRemoteAddress(new InetSocketAddress(0));
        } catch (URISyntaxException e) {
            fail("Illegal URI string in test?");
        }
        return request;
    }

    public static BufferedContentChannel createChannel() {
        BufferedContentChannel channel = new BufferedContentChannel();
        channel.close(null);
        return channel;
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
                    return 0;
                }
            };
        }
    }

}
