// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.client;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.ListenableFuture;
import com.ning.http.client.Request;
import com.ning.http.client.websocket.WebSocket;
import com.ning.http.client.websocket.WebSocketUpgradeHandler;
import com.yahoo.jdisc.handler.CompletionHandler;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.testng.AssertJUnit.fail;

/**
 * @author <a href="mailto:vikasp@yahoo-inc.com">Vikas Panwar</a>
 */
@SuppressWarnings("unchecked")
public class WebSocketContentTestCase {

    private final byte[] TEST_DATA = "test data".getBytes(StandardCharsets.UTF_8);

    @Test(enabled = false)
    public void testContentChannelWriteAndClose() throws Exception{
        AsyncHttpClient client = Mockito.mock(AsyncHttpClient.class);
        com.yahoo.jdisc.Request request = Mockito.mock(com.yahoo.jdisc.Request.class);
        Mockito.when(request.getUri()).thenReturn(new URI(""));

        WebSocket websocket = Mockito.mock(WebSocket.class);
        Mockito.when(websocket.isOpen()).thenReturn(true);
        ListenableFuture<WebSocket> future = Mockito.mock(ListenableFuture.class);
        Mockito.when(client.executeRequest((Request)Mockito.isNotNull(), (WebSocketUpgradeHandler)Mockito.anyObject()))
               .thenReturn(future);
        Mockito.when(future.get()).thenReturn(websocket);

        WebSocketContent underTest = new WebSocketContent(client, request, Mockito.mock(WebSocketUpgradeHandler.class));

        CompletionHandler completionHandler = Mockito.mock(CompletionHandler.class);
        underTest.write(ByteBuffer.wrap(TEST_DATA),completionHandler);

        Mockito.verify(completionHandler,Mockito.atLeastOnce()).completed();

        CompletionHandler closeHandler = Mockito.mock(CompletionHandler.class);
        underTest.close(closeHandler);
        Mockito.verify(closeHandler).completed();
        Mockito.verify(websocket).close();
        Mockito.verify(websocket).sendMessage(TEST_DATA);
    }

    @Test(enabled = false)
    public void testWritingToAClosedContentChannel() throws Exception{
        AsyncHttpClient client = Mockito.mock(AsyncHttpClient.class);
        com.yahoo.jdisc.Request request = Mockito.mock(com.yahoo.jdisc.Request.class);
        Mockito.when(request.getUri()).thenReturn(new URI(""));
        WebSocket websocket = Mockito.mock(WebSocket.class);
        ListenableFuture<WebSocket> future = Mockito.mock(ListenableFuture.class);
        Mockito.when(client.executeRequest((Request)Mockito.isNotNull(), (WebSocketUpgradeHandler)Mockito.anyObject()))
               .thenReturn(future);
        Mockito.when(future.get()).thenReturn(websocket);

        WebSocketContent underTest = new WebSocketContent(client, request, Mockito.mock(WebSocketUpgradeHandler.class));
        underTest.close(Mockito.mock(CompletionHandler.class));

        // opens a new websocket
        underTest.write(ByteBuffer.wrap(TEST_DATA), Mockito.mock(CompletionHandler.class));
    }

    @Test(enabled = false)
    public void testExceptionalPathInExecuteRequest() throws Exception{
        AsyncHttpClient client = Mockito.mock(AsyncHttpClient.class);
        com.yahoo.jdisc.Request request = Mockito.mock(com.yahoo.jdisc.Request.class);
        Mockito.when(request.getUri()).thenReturn(new URI(""));

        WebSocket websocket = Mockito.mock(WebSocket.class);
        Mockito.when(websocket.isOpen()).thenReturn(true);
        ListenableFuture<WebSocket> future = Mockito.mock(ListenableFuture.class);
        Mockito.when(client.executeRequest((Request)Mockito.isNotNull(), (WebSocketUpgradeHandler)Mockito.anyObject()))
                .thenReturn(future);
        Mockito.when(future.get()).thenReturn(websocket);
        Mockito.when(websocket.sendMessage((byte[])Mockito.any())).thenThrow(new RuntimeException());

        WebSocketContent underTest = new WebSocketContent(client, request, Mockito.mock(WebSocketUpgradeHandler.class));

        CompletionHandler completionHandler = Mockito.mock(CompletionHandler.class);

        try {
            underTest.write(ByteBuffer.wrap(TEST_DATA),completionHandler);
            fail();
        } catch(RuntimeException e) {
            // Expected
        }
    }
}
