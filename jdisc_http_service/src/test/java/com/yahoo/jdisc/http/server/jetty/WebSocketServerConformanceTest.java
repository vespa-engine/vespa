// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.ning.http.client.websocket.WebSocket;
import com.ning.http.client.websocket.WebSocketByteListener;
import com.yahoo.jdisc.application.BindingRepository;
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.jdisc.http.filter.RequestFilter;
import com.yahoo.jdisc.http.filter.ResponseFilter;
import com.yahoo.jdisc.http.guiceModules.ConnectorFactoryRegistryModule;
import com.yahoo.jdisc.http.server.FilterBindings;
import com.yahoo.jdisc.test.ServerProviderConformanceTest;
import org.glassfish.grizzly.websockets.HandshakeException;
import org.hamcrest.Matcher;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
//Ignore: Broken by jetty 9.2.{3,4}
class WebSocketServerConformanceTestIgnored extends ServerProviderConformanceTest {

    /* Some tests here are disabled. What they have in common is that the scenario
     * involves waiting for an event (response write) in the request content channel's close()
     * method, but Jetty will sometimes use the thread that is supposed to generate that event
     * (the thread that writes the response) to deliver the close() notification, causing a
     * deadlock.
     *
     * All in all, the WebSocket protocol doesn't map beautifully to JDisc APIs, which makes
     * it hard to do proper testing here. Specifically, in order to cause the request content
     * channel to be closed, we have to close the socket from the client side, which means
     * that all bets are off regarding what response the client will see. So, the tests here
     * that close the socket early can do no verification at all. However, it will be
     * verified by the test framework that the server-side request processing finishes
     * without any unexpected side effects.
     */

    @Override
    @Test
    public void testContainerNotReadyException() throws Throwable {
        new TestRunner().expectedError(instanceOf(HandshakeException.class))
                        .execute();
    }

    @Override
    @Test
    public void testBindingSetNotFoundException() throws Throwable {
        new TestRunner().expectedError(instanceOf(HandshakeException.class))
                        .execute();
    }

    @Override
    @Test
    public void testNoBindingSetSelectedException() throws Throwable {
        new TestRunner().expectedError(instanceOf(HandshakeException.class))
                        .execute();
    }

    @Override
    @Test
    public void testBindingNotFoundException() throws Throwable {
        new TestRunner().expectedError(instanceOf(HandshakeException.class))
                        .execute();
    }

    @Override
    @Test
    public void testRequestHandlerWithSyncCloseResponse() throws Throwable {
        new TestRunner().expectResponseContent(is("myResponseContent"))
                        .execute();
    }

    @Override
    @Test
    public void testRequestHandlerWithSyncWriteResponse() throws Throwable {
        new TestRunner().expectResponseContent(is("myResponseContent"))
                        .execute();
    }

    @Override
    @Test
    public void testRequestHandlerWithSyncHandleResponse() throws Throwable {
        new TestRunner().expectResponseContent(is("myResponseContent"))
                        .execute();
    }

    @Override
    @Test
    public void testRequestHandlerWithAsyncHandleResponse() throws Throwable {
        new TestRunner().expectResponseContent(is("myResponseContent"))
                        .execute();
    }

    @Override
    @Test
    public void testRequestException() throws Throwable {
        new TestRunner().expectedError(instanceOf(HandshakeException.class))
                        .execute();
    }

    @Override
    @Test
    public void testRequestExceptionWithSyncCloseResponse() throws Throwable {
        new TestRunner().expectedError(instanceOf(HandshakeException.class))
                        .execute();
    }

    @Override
    @Test
    public void testRequestExceptionWithSyncWriteResponse() throws Throwable {
        new TestRunner().expectedError(instanceOf(HandshakeException.class))
                        .execute();
    }

    @Override
    @Test
    public void testRequestNondeterministicExceptionWithSyncHandleResponse() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testRequestExceptionBeforeResponseWriteWithSyncHandleResponse() throws Throwable {
        new TestRunner().expectedError(instanceOf(HandshakeException.class))
                        .execute();
    }

    @Override
    @Test
    public void testRequestExceptionAfterResponseWriteWithSyncHandleResponse() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testRequestNondeterministicExceptionWithAsyncHandleResponse() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testRequestExceptionBeforeResponseWriteWithAsyncHandleResponse() throws Throwable {
        new TestRunner().expectedError(instanceOf(HandshakeException.class))
                        .execute();
    }

    @Override
    @Test
    public void testRequestExceptionAfterResponseCloseNoContentWithAsyncHandleResponse() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testRequestExceptionAfterResponseWriteWithAsyncHandleResponse() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testRequestContentWriteWithSyncCompletion() throws Throwable {
        new TestRunner().expectResponseContent(is("myResponseContent"))
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentWriteWithAsyncCompletion() throws Throwable {
        new TestRunner().expectResponseContent(is("myResponseContent"))
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentWriteWithNondeterministicSyncFailure() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testRequestContentWriteWithSyncFailureBeforeResponseWrite() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testRequestContentWriteWithSyncFailureAfterResponseWrite() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testRequestContentWriteWithNondeterministicAsyncFailure() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testRequestContentWriteWithAsyncFailureBeforeResponseWrite() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testRequestContentWriteWithAsyncFailureAfterResponseWrite() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testRequestContentWriteWithAsyncFailureAfterResponseCloseNoContent() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testRequestContentWriteNondeterministicException() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionBeforeResponseWrite() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionAfterResponseWrite() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionAfterResponseCloseNoContent() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testRequestContentWriteNondeterministicExceptionWithSyncCompletion() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionBeforeResponseWriteWithSyncCompletion() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionAfterResponseWriteWithSyncCompletion() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionAfterResponseCloseNoContentWithSyncCompletion() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testRequestContentWriteNondeterministicExceptionWithAsyncCompletion() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionBeforeResponseWriteWithAsyncCompletion() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionAfterResponseWriteWithAsyncCompletion() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionAfterResponseCloseNoContentWithAsyncCompletion() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionWithNondeterministicSyncFailure() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionWithSyncFailureBeforeResponseWrite() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionWithSyncFailureAfterResponseWrite() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionWithSyncFailureAfterResponseCloseNoContent() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionWithNondeterministicAsyncFailure() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionWithAsyncFailureBeforeResponseWrite() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionWithAsyncFailureAfterResponseWrite() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionWithAsyncFailureAfterResponseCloseNoContent() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testRequestContentCloseWithSyncCompletion() throws Throwable {
        new TestRunner().expectResponseContent(is("myResponseContent"))
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseWithAsyncCompletion() throws Throwable {
        new TestRunner().expectResponseContent(is("myResponseContent"))
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseWithNondeterministicSyncFailure() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testRequestContentCloseWithSyncFailureBeforeResponseWrite() throws Throwable {
        new TestRunner().setCloseRequestEarly(true)
                        .execute();
    }

    @Override
    @Test(enabled = false)
    public void testRequestContentCloseWithSyncFailureAfterResponseWrite() throws Throwable {
    }

    @Override
    @Test
    public void testRequestContentCloseWithSyncFailureAfterResponseCloseNoContent() throws Throwable {
        new TestRunner().setCloseRequestEarly(true)
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseWithNondeterministicAsyncFailure() throws Throwable {
        new TestRunner().setCloseRequestEarly(true)
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseWithAsyncFailureBeforeResponseWrite() throws Throwable {
        new TestRunner().setCloseRequestEarly(true)
                        .execute();
    }

    @Override
    @Test(enabled = false)
    public void testRequestContentCloseWithAsyncFailureAfterResponseWrite() throws Throwable {
    }

    @Override
    @Test
    public void testRequestContentCloseWithAsyncFailureAfterResponseCloseNoContent() throws Throwable {
        new TestRunner().setCloseRequestEarly(true)
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseNondeterministicException() throws Throwable {
        new TestRunner().setCloseRequestEarly(true)
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionBeforeResponseWrite() throws Throwable {
        new TestRunner().setCloseRequestEarly(true)
                        .execute();
    }

    @Override
    @Test(enabled = false)
    public void testRequestContentCloseExceptionAfterResponseWrite() throws Throwable {
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionAfterResponseCloseNoContent() throws Throwable {
        new TestRunner().setCloseRequestEarly(true)
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseNondeterministicExceptionWithSyncCompletion() throws Throwable {
        new TestRunner().setCloseRequestEarly(true)
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionBeforeResponseWriteWithSyncCompletion() throws Throwable {
        new TestRunner().setCloseRequestEarly(true)
                        .execute();
    }

    @Override
    @Test(enabled = false)
    public void testRequestContentCloseExceptionAfterResponseWriteWithSyncCompletion() throws Throwable {
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionAfterResponseCloseNoContentWithSyncCompletion() throws Throwable {
        new TestRunner().setCloseRequestEarly(true)
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseNondeterministicExceptionWithAsyncCompletion() throws Throwable {
        new TestRunner().setCloseRequestEarly(true)
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionBeforeResponseWriteWithAsyncCompletion() throws Throwable {
        new TestRunner().setCloseRequestEarly(true)
                        .execute();
    }

    @Override
    @Test(enabled = false)
    public void testRequestContentCloseExceptionAfterResponseWriteWithAsyncCompletion() throws Throwable {
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionAfterResponseCloseNoContentWithAsyncCompletion() throws Throwable {
        new TestRunner().setCloseRequestEarly(true)
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseNondeterministicExceptionWithSyncFailure() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionBeforeResponseWriteWithSyncFailure() throws Throwable {
        new TestRunner().setCloseRequestEarly(true)
                        .execute();
    }

    @Override
    @Test(enabled = false)
    public void testRequestContentCloseExceptionAfterResponseWriteWithSyncFailure() throws Throwable {
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionAfterResponseCloseNoContentWithSyncFailure() throws Throwable {
        new TestRunner().setCloseRequestEarly(true)
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseNondeterministicExceptionWithAsyncFailure() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionBeforeResponseWriteWithAsyncFailure() throws Throwable {
        new TestRunner().setCloseRequestEarly(true)
                        .execute();
    }

    @Override
    @Test(enabled = false)
    public void testRequestContentCloseExceptionAfterResponseWriteWithAsyncFailure() throws Throwable {
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionAfterResponseCloseNoContentWithAsyncFailure() throws Throwable {
        new TestRunner().setCloseRequestEarly(true)
                        .execute();
    }

    @Override
    @Test
    public void testResponseWriteCompletionException() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testResponseCloseCompletionException() throws Throwable {
        new TestRunner().execute();
    }

    @Override
    @Test
    public void testResponseCloseCompletionExceptionNoContent() throws Throwable {
        new TestRunner().execute();
    }

    @SuppressWarnings("deprecation")
    private class TestRunner implements Adapter<JettyHttpServer, WebSocketClient, Future<String>> {

        Matcher<String> expectedContent = null;
        Matcher<Object> expectedError = null;
        boolean closeRequestEarly;

        void execute() throws Throwable {
            runTest(this);
        }

        TestRunner expectResponseContent(final Matcher<String> matcher) {
            assertThat(expectedError, is(nullValue()));
            expectedContent = matcher;
            return this;
        }

        TestRunner expectedError(final Matcher<Object> matcher) {
            assertThat(expectedContent, is(nullValue()));
            expectedError = matcher;
            return this;
        }

        @Override
        public Module newConfigModule() {
            return Modules.combine(
                    new AbstractModule() {
                        @Override
                        protected void configure() {
                            bind(FilterBindings.class)
                                    .toInstance(new FilterBindings(
                                            new BindingRepository<RequestFilter>(),
                                            new BindingRepository<ResponseFilter>()));
                            bind(ServerConfig.class)
                                    .toInstance(new ServerConfig(new ServerConfig.Builder()));
                        }
                    },
                    new ConnectorFactoryRegistryModule());
        }

        @Override
        public Class<JettyHttpServer> getServerProviderClass() {
            return JettyHttpServer.class;
        }

        @Override
        public WebSocketClient newClient(final JettyHttpServer server) throws Throwable {
            return new WebSocketClient(server.getListenPort());
        }

        @Override
        public Future<String> executeRequest(
                final WebSocketClient client,
                final boolean withRequestContent) throws Throwable {
            final String requestContent = withRequestContent ? "myRequestContent" : null;
            return client.executeRequest(requestContent, closeRequestEarly);
        }

        @Override
        public Iterable<ByteBuffer> newResponseContent() {
            return Collections.singleton(StandardCharsets.UTF_8.encode("myResponseContent"));
        }

        @Override
        public void validateResponse(final Future<String> responseFuture) throws Throwable {
            String content = null;
            Throwable error = null;
            try {
                content = responseFuture.get(60, TimeUnit.SECONDS);
            } catch (final ExecutionException e) {
                error = e.getCause();
            }
            if (expectedContent != null) {
                assertThat(content, expectedContent);
            }
            if (expectedError != null) {
                assertThat(error, expectedError);
            }
        }

        public TestRunner setCloseRequestEarly(final boolean closeRequestEarly) {
            this.closeRequestEarly = closeRequestEarly;
            return this;
        }
    }

    private static class WebSocketClient implements Closeable {

        final SimpleWebSocketClient delegate;

        WebSocketClient(final int listenPort) {
            delegate = new SimpleWebSocketClient(null, listenPort);
        }

        Future<String> executeRequest(final String requestContent, final boolean closeRequest) throws Exception {
            final MyWebSocketListener listener = new MyWebSocketListener(requestContent, closeRequest);
            delegate.executeRequest("/status.html", listener);
            return listener.response;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }

    // You may find this class slightly ugly, with all the logic to do closing in various places.
    // The reason this is necessary is the combination of several things:
    // 1) The way WebSocket is implemented in JDisc and mapped to JDisc APIs, specifically:
    //    - When the client closes a socket, it is not guaranteed to receive anything more from the server
    //      (the protocol could support it, but neither the client nor server library that we use do)
    //    - The server won't close a socket until the client does, but by then it is too late for the
    //      server to send responses.
    // 2) The conformance test framework is designed mostly for request-response protocols. It assumes that
    // it is self-evident when communication is over, and only _then_ moves on to validating the response.
    //
    // The problem is that we cannot close the socket right after sending the request, as we are then
    // not guaranteed to receive response data (nondeterministic behavior). We cannot close the socket when
    // the conformance test framework asks us to validate the response, because we'd never get to that
    // - the request processing isn't finished until some party closes the socket! So how do we decide when
    // to close the socket? Well, what any "real" client would do is close it when we're satisfied with the
    // response. And these tests never return anything more than a single response message, so if we get one,
    // we consider ourselves done. Also if we get an error.
    private static class MyWebSocketListener implements WebSocketByteListener {

        // This is used to temporarily concatenate response fragments until we have a complete response message.
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        // This is used to signal that we have received a response, which may be data or an error.
        // This is attempted set from multiple code locations, but the first one "wins" (the others are ignored).
        final SettableFuture<String> response = SettableFuture.create();

        // If this is true, the client will close the socket immediately after sending the request content.
        // This means that there is no guarantee that the client will receive any response from the server.
        // If this is false, the socket is closed after receiving a response from the server. Since the server
        // response in theory can be infinitely long, we define "receive a response" as receiving a single message,
        // since that is what is sent from the server in these tests.
        final boolean closeEarly;

        final byte[] requestContent;

        // We need to be able to close the WebSocket in methods that are not handed the WebSocket instance.
        // We use this to keep a reference to it.
        private final AtomicReference<WebSocket> webSocketRef = new AtomicReference<>(null);

        MyWebSocketListener(final String requestContent, final boolean closeEarly) {
            this.closeEarly = closeEarly;
            this.requestContent = requestContent != null ? requestContent.getBytes(StandardCharsets.UTF_8) : null;
        }

        @Override
        public void onOpen(final WebSocket webSocket) {
            this.webSocketRef.set(webSocket);
            if (requestContent != null) {
                webSocket.sendMessage(requestContent);
            }
            if (closeEarly) {
                webSocket.close();
            }
        }

        @Override
        public void onClose(final WebSocket webSocket) {
            response.set("");
            this.webSocketRef.set(null);
        }

        @Override
        public void onError(final Throwable t) {
            response.setException(t);
            closeSocket();
        }

        @Override
        public void onMessage(final byte[] buf) {
            final String message = new String(buf, StandardCharsets.UTF_8);
            response.set(message);
            closeSocket();
        }

        @Override
        public void onFragment(final byte[] buf, final boolean last) {
            try {
                out.write(buf);
                if (last) {
                    response.set(new String(out.toByteArray(), StandardCharsets.UTF_8));
                    closeSocket();
                }
            } catch (final IOException e) {
                response.setException(e);
            }
        }

        private void closeSocket() {
            final WebSocket webSocket = webSocketRef.get();
            if (webSocket != null) {
                webSocket.close();
            }
        }
    }
}
