// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.test;

import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.yahoo.jdisc.NoopSharedResource;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.service.CurrentContainer;
import com.yahoo.jdisc.service.ServerProvider;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Simon Thoresen Hult
 */
public class ServerProviderConformanceTestTest extends ServerProviderConformanceTest {

    @Override
    @Test
    public void testContainerNotReadyException() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testBindingSetNotFoundException() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testNoBindingSetSelectedException() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testBindingNotFoundException() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestHandlerWithSyncCloseResponse() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestHandlerWithSyncWriteResponse() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestHandlerWithSyncHandleResponse() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestHandlerWithAsyncHandleResponse() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestException() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestExceptionWithSyncCloseResponse() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestExceptionWithSyncWriteResponse() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestNondeterministicExceptionWithSyncHandleResponse() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestExceptionBeforeResponseWriteWithSyncHandleResponse() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestExceptionAfterResponseWriteWithSyncHandleResponse() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestNondeterministicExceptionWithAsyncHandleResponse() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestExceptionBeforeResponseWriteWithAsyncHandleResponse() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestExceptionAfterResponseCloseNoContentWithAsyncHandleResponse() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestExceptionAfterResponseWriteWithAsyncHandleResponse() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentWriteWithSyncCompletion() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentWriteWithAsyncCompletion() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentWriteWithNondeterministicSyncFailure() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentWriteWithSyncFailureBeforeResponseWrite() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentWriteWithSyncFailureAfterResponseWrite() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentWriteWithNondeterministicAsyncFailure() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentWriteWithAsyncFailureBeforeResponseWrite() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentWriteWithAsyncFailureAfterResponseWrite() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentWriteWithAsyncFailureAfterResponseCloseNoContent() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentWriteNondeterministicException() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionBeforeResponseWrite() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionAfterResponseWrite() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionAfterResponseCloseNoContent() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentWriteNondeterministicExceptionWithSyncCompletion() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionBeforeResponseWriteWithSyncCompletion() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionAfterResponseWriteWithSyncCompletion() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionAfterResponseCloseNoContentWithSyncCompletion() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentWriteNondeterministicExceptionWithAsyncCompletion() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionBeforeResponseWriteWithAsyncCompletion() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionAfterResponseWriteWithAsyncCompletion() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionAfterResponseCloseNoContentWithAsyncCompletion() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionWithNondeterministicSyncFailure() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionWithSyncFailureBeforeResponseWrite() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionWithSyncFailureAfterResponseWrite() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionWithSyncFailureAfterResponseCloseNoContent() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionWithNondeterministicAsyncFailure() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionWithAsyncFailureBeforeResponseWrite() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionWithAsyncFailureAfterResponseWrite() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionWithAsyncFailureAfterResponseCloseNoContent() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentCloseWithSyncCompletion() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentCloseWithAsyncCompletion() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentCloseWithNondeterministicSyncFailure() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentCloseWithSyncFailureBeforeResponseWrite() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentCloseWithSyncFailureAfterResponseWrite() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentCloseWithSyncFailureAfterResponseCloseNoContent() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentCloseWithNondeterministicAsyncFailure() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentCloseWithAsyncFailureBeforeResponseWrite() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentCloseWithAsyncFailureAfterResponseWrite() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentCloseWithAsyncFailureAfterResponseCloseNoContent() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentCloseNondeterministicException() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionBeforeResponseWrite() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionAfterResponseWrite() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionAfterResponseCloseNoContent() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentCloseNondeterministicExceptionWithSyncCompletion() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionBeforeResponseWriteWithSyncCompletion() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionAfterResponseWriteWithSyncCompletion() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionAfterResponseCloseNoContentWithSyncCompletion() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentCloseNondeterministicExceptionWithAsyncCompletion() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionBeforeResponseWriteWithAsyncCompletion() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionAfterResponseWriteWithAsyncCompletion() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionAfterResponseCloseNoContentWithAsyncCompletion() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentCloseNondeterministicExceptionWithSyncFailure() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionBeforeResponseWriteWithSyncFailure() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionAfterResponseWriteWithSyncFailure() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionAfterResponseCloseNoContentWithSyncFailure() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentCloseNondeterministicExceptionWithAsyncFailure() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionBeforeResponseWriteWithAsyncFailure() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionAfterResponseWriteWithAsyncFailure() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionAfterResponseCloseNoContentWithAsyncFailure() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testResponseWriteCompletionException() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testResponseCloseCompletionException() throws Throwable {
        runTest(new MyAdapter());
    }

    @Override
    @Test
    public void testResponseCloseCompletionExceptionNoContent() throws Throwable {
        runTest(new MyAdapter());
    }

    private static void tryWrite(final ContentChannel out, final String str) {
        try {
            out.write(StandardCharsets.UTF_8.encode(str), null);
        } catch (Throwable t) {
            // Simulate handling the failure.
            t.getMessage();
        }
    }

    private static void tryClose(final ContentChannel out) {
        try {
            out.close(null);
        } catch (Throwable t) {
            // Simulate handling the failure.
            t.getMessage();
        }
    }

    private static void tryComplete(final CompletionHandler handler) {
        try {
            handler.completed();
        } catch (Throwable t) {
            // Simulate handling the failure.
            t.getMessage();
        }
    }

    private static class MyServer extends NoopSharedResource implements ServerProvider {

        final CurrentContainer container;

        @Inject
        MyServer(final CurrentContainer container) {
            this.container = container;
        }

        @Override
        public void start() {

        }

        @Override
        public void close() {

        }
    }

    private static class MyClient {

        final MyServer server;

        MyClient(final MyServer server) {
            this.server = server;
        }

        MyResponseHandler executeRequest(final boolean withRequestContent)
                throws InterruptedException, ExecutionException, TimeoutException {
            final MyResponseHandler responseHandler = new MyResponseHandler();
            final Request request;
            try {
                request = new Request(server.container, URI.create("http://localhost/"));
            } catch (Throwable t) {
                responseHandler.response.complete(new Response(Response.Status.INTERNAL_SERVER_ERROR, t));
                return responseHandler;
            }
            try {
                final ContentChannel out = request.connect(responseHandler);
                if (withRequestContent) {
                    tryWrite(out, "myRequestContent");
                }
                tryClose(out);
            } catch (Throwable t) {
                responseHandler.response.complete(new Response(Response.Status.INTERNAL_SERVER_ERROR, t));
                // Simulate handling the failure.
                t.getMessage();
                return responseHandler;
            } finally {
                request.release();
            }
            return responseHandler;
        }
    }

    private static class MyResponseHandler implements ResponseHandler {

        final CompletableFuture<Response> response = new CompletableFuture<>();
        final CompletableFuture<String> content = new CompletableFuture<>();
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        @Override
        public ContentChannel handleResponse(final Response response) {
            this.response.complete(response);
            return new ContentChannel() {

                @Override
                public void write(final ByteBuffer buf, final CompletionHandler handler) {
                    while (buf.hasRemaining()) {
                        out.write(buf.get());
                    }
                    tryComplete(handler);
                }

                @Override
                public void close(final CompletionHandler handler) {
                    content.complete(new String(out.toByteArray(), StandardCharsets.UTF_8));
                    tryComplete(handler);
                }
            };
        }
    }

    private static class MyAdapter implements Adapter<MyServer, MyClient, MyResponseHandler> {

        @Override
        public Module newConfigModule() {
            return Modules.EMPTY_MODULE;
        }

        @Override
        public Class<MyServer> getServerProviderClass() {
            return MyServer.class;
        }

        @Override
        public MyClient newClient(final MyServer server) throws Throwable {
            return new MyClient(server);
        }

        @Override
        public MyResponseHandler executeRequest(
                final MyClient client,
                final boolean withRequestContent) throws Throwable {
            return client.executeRequest(withRequestContent);
        }

        @Override
        public Iterable<ByteBuffer> newResponseContent() {
            return Collections.singleton(StandardCharsets.UTF_8.encode("myResponseContent"));
        }

        @Override
        public void validateResponse(final MyResponseHandler responseHandler) throws Throwable {
            responseHandler.response.get(600, TimeUnit.SECONDS);
        }
    }
}
