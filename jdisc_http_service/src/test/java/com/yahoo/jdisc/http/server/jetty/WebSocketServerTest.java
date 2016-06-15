// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.google.common.util.concurrent.SettableFuture;
import com.ning.http.client.websocket.WebSocket;
import com.ning.http.client.websocket.WebSocketByteListener;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static com.yahoo.jdisc.Response.Status.OK;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
public class WebSocketServerTest {

    @Test(enabled = false)
    public void requireThatServerCanRespondToRequest() throws Exception {
        final TestDriver driver = TestDrivers.newInstance(new EchoRequestHandler());
        final SimpleWebSocketClient client = new SimpleWebSocketClient(driver);
        final MyWebSocketListener listener = new MyWebSocketListener("Hello World!");
        client.executeRequest("/status.html", listener);
        assertThat(listener.response.get(60, TimeUnit.SECONDS), is("Hello World!"));
        assertThat(client.close(), is(true));
        assertThat(driver.close(), is(true));
    }

    //@Test Ignored: Broken in jetty 9.2.{3,4}
    public void requireThatServerCanRespondToSslRequest() throws Exception {
        final TestDriver driver = TestDrivers.newInstanceWithSsl(new EchoRequestHandler());
        final SimpleWebSocketClient client = new SimpleWebSocketClient(driver);
        final MyWebSocketListener listener = new MyWebSocketListener("Hello World!");
        client.executeRequest("/status.html", listener);
        assertThat(listener.response.get(60, TimeUnit.SECONDS), is("Hello World!"));
        assertThat(client.close(), is(true));
        assertThat(driver.close(), is(true));
    }

    private static class EchoRequestHandler extends AbstractRequestHandler {

        @Override
        public ContentChannel handleRequest(final Request request, final ResponseHandler handler) {
            return handler.handleResponse(new Response(OK));
        }
    }

    private static class MyWebSocketListener implements WebSocketByteListener {

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final SettableFuture<String> response = SettableFuture.create();
        final byte[] requestContent;

        MyWebSocketListener(final String requestContent) {
            this.requestContent = requestContent.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public void onOpen(final WebSocket webSocket) {
            webSocket.sendMessage(requestContent);
            webSocket.close();
        }

        @Override
        public void onClose(final WebSocket webSocket) {
            response.set(new String(out.toByteArray(), StandardCharsets.UTF_8));
        }

        @Override
        public void onError(final Throwable t) {
            response.setException(t);
        }

        @Override
        public void onMessage(final byte[] buf) {
            try {
                out.write(buf);
            } catch (final IOException e) {
                response.setException(e);
            }
        }

        @Override
        public void onFragment(final byte[] buf, final boolean last) {
            try {
                out.write(buf);
            } catch (final IOException e) {
                response.setException(e);
            }
        }
    }
}
