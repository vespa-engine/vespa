// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http;

import com.google.common.annotations.Beta;
import com.yahoo.jdisc.service.CurrentContainer;

import java.net.SocketAddress;
import java.net.URI;

/**
 * Represents a WebSocket request.
 *
 * @author <a href="mailto:vikasp@yahoo-inc.com">Vikas Panwar</a>
 */
@Beta
public class WebSocketRequest extends HttpRequest {

    @SuppressWarnings("deprecation")
    protected WebSocketRequest(CurrentContainer current, URI uri, Method method, Version version,
                               SocketAddress remoteAddress) {
        super(current, uri, method, version, remoteAddress, null);
    }

    public static WebSocketRequest newServerRequest(CurrentContainer current, URI uri, Method method, Version version,
                                                    SocketAddress remoteAddress) {
        return new WebSocketRequest(current, uri, method, version, remoteAddress);
    }
}
