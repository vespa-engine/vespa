// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.container.jdisc.HttpResponse;

/**
 * Interface for creating responses for SessionCreateHandler.
 *
 * @author musum
 * @since 5.1.27
 */
public interface SessionCreateResponse {

    public HttpResponse createResponse(String hostName, int port, long sessionId);
}
