// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.internal.HttpChannelState;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Builder for creating a mock instance of Jetty's {@link Response} type.
 *
 * @author bjorncs
 */
public class JettyMockResponseBuilder {

    private final Request request;
    private int statusCode = 200;

    private JettyMockResponseBuilder(Request request) {
        this.request = request;
    }

    public static JettyMockResponseBuilder newBuilder(Request request) { return new JettyMockResponseBuilder(request); }

    public JettyMockResponseBuilder withStatusCode(int statusCode) {
        this.statusCode = statusCode;
        return this;
    }

    public Response build() { return new DummyResponse(this, request); }

    private static class DummyResponse extends Response.Wrapper {

        private volatile int statusCode;
        private final HttpChannelState.ChannelResponse wrapped;

        public DummyResponse(JettyMockResponseBuilder b, Request request) {
            super(request, mock(Response.class, withSettings().stubOnly()));
            statusCode = b.statusCode;
            wrapped = mock(HttpChannelState.ChannelResponse.class);
            when(wrapped.getContentBytesWritten()).thenReturn(0L);
        }

        @Override public int getStatus() { return statusCode; }
        @Override public void setStatus(int sc) { statusCode = sc; }
        @Override public Response getWrapped() { return wrapped; }

    }

}
