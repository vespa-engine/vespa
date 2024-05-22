// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.server.HttpChannel;
import org.eclipse.jetty.server.Response;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Builder for creating a mock instance of Jetty's {@link Response} type.
 *
 * @author bjorncs
 */
public class JettyMockResponseBuilder {

    private int statusCode = 200;

    private JettyMockResponseBuilder() {}

    public static JettyMockResponseBuilder newBuilder() { return new JettyMockResponseBuilder(); }

    public JettyMockResponseBuilder withStatusCode(int statusCode) {
        this.statusCode = statusCode;
        return this;
    }

    public Response build() {
        MetaData.Response metaData = mock(MetaData.Response.class);
        when(metaData.getStatus()).thenReturn(statusCode);
        Response response = mock(Response.class);
        when(response.getHttpChannel()).thenReturn(mock(HttpChannel.class));
        when(response.getCommittedMetaData()).thenReturn(metaData);
        return response;
    }

}
