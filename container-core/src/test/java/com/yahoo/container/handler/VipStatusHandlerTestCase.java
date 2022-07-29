// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Check semantics of VIP status handler. Do note this handler does not need to
 * care about the incoming URI, that's 100% handled in JDIsc by the binding
 * pattern.
 *
 * @author Steinar Knutsen
 */
public class VipStatusHandlerTestCase {

    public static class MockResponseHandler implements ResponseHandler {

        final ReadableContentChannel channel = new ReadableContentChannel();

        @Override
        public ContentChannel handleResponse(com.yahoo.jdisc.Response response) {
            return channel;
        }
    }

    Metric metric = Mockito.mock(Metric.class);

    @Test
    void testHandleRequest() {
        VipStatusConfig config = new VipStatusConfig(new VipStatusConfig.Builder().accessdisk(false));
        VipStatusHandler handler = new VipStatusHandler(Executors.newCachedThreadPool(), config, metric);
        MockResponseHandler responseHandler = new MockResponseHandler();
        HttpRequest request = createRequest();
        BufferedContentChannel requestContent = createChannel();
        handler.handleRequest(request, requestContent, responseHandler);
        ByteBuffer b = responseHandler.channel.read();
        byte[] asBytes = new byte[b.remaining()];
        b.get(asBytes);
        assertEquals(VipStatusHandler.OK_MESSAGE, Utf8.toString(asBytes));
    }

    public static class NotFoundResponseHandler implements ResponseHandler {

        final ReadableContentChannel channel = new ReadableContentChannel();

        @Override
        public ContentChannel handleResponse(com.yahoo.jdisc.Response response) {
            assertEquals(com.yahoo.jdisc.Response.Status.NOT_FOUND, response.getStatus());
            return channel;
        }

    }

    @Test
    void testFileNotFound() {
        VipStatusConfig config = new VipStatusConfig(new VipStatusConfig.Builder().accessdisk(true)
                .statusfile("/VipStatusHandlerTestCaseFileThatReallyReallyShouldNotExist"));
        VipStatusHandler handler = new VipStatusHandler(Executors.newCachedThreadPool(), config, metric);
        NotFoundResponseHandler responseHandler = new NotFoundResponseHandler();
        HttpRequest request = createRequest();
        BufferedContentChannel requestContent = createChannel();
        handler.handleRequest(request, requestContent, responseHandler);
        ByteBuffer b = responseHandler.channel.read();
        byte[] asBytes = new byte[b.remaining()];
        b.get(asBytes);
        assertEquals(
                VipStatusHandler.StatusResponse.COULD_NOT_FIND_STATUS_FILE,
                Utf8.toString(asBytes));
    }

    @Test
    void testFileFound() throws IOException {
        File statusFile = File.createTempFile("VipStatusHandlerTestCase", null);
        try {
            FileWriter writer = new FileWriter(statusFile);
            String OK = "OK\n";
            writer.write(OK);
            writer.close();
            VipStatusConfig config = new VipStatusConfig(
                    new VipStatusConfig.Builder()
                            .accessdisk(true)
                            .statusfile(statusFile.getAbsolutePath()));
            VipStatusHandler handler = new VipStatusHandler(Executors.newCachedThreadPool(), config, metric);
            MockResponseHandler responseHandler = new MockResponseHandler();
            HttpRequest request = createRequest();
            BufferedContentChannel requestContent = createChannel();
            handler.handleRequest(request, requestContent, responseHandler);
            ByteBuffer b = responseHandler.channel.read();
            byte[] asBytes = new byte[b.remaining()];
            b.get(asBytes);
            assertEquals(OK, Utf8.toString(asBytes));
        } finally {
            statusFile.delete();
        }
    }

    @Test
    void testExplicitlyRotationControl() {
        VipStatus vipStatus = new VipStatus();
        VipStatusConfig config = new VipStatusConfig(new VipStatusConfig.Builder().accessdisk(false));
        VipStatusHandler handler = new VipStatusHandler(Executors.newCachedThreadPool(), config,  metric, vipStatus);

        vipStatus.setInRotation(false);

        {
            MockResponseHandler responseHandler = new MockResponseHandler();
            HttpRequest request = createRequest();
            BufferedContentChannel requestContent = createChannel();
            handler.handleRequest(request, requestContent, responseHandler);
            ByteBuffer b = responseHandler.channel.read();
            byte[] asBytes = new byte[b.remaining()];
            b.get(asBytes);
            assertEquals(VipStatusHandler.StatusResponse.NO_SEARCH_BACKENDS, Utf8.toString(asBytes));
        }

        vipStatus.setInRotation(true);

        {
            MockResponseHandler responseHandler = new MockResponseHandler();
            HttpRequest request = createRequest();
            BufferedContentChannel requestContent = createChannel();
            handler.handleRequest(request, requestContent, responseHandler);
            ByteBuffer b = responseHandler.channel.read();
            byte[] asBytes = new byte[b.remaining()];
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
