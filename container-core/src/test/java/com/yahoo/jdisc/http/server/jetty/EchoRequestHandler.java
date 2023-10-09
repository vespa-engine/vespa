// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;

import static com.yahoo.jdisc.Response.Status.OK;

/**
 * @author bjorncs
 */
class EchoRequestHandler extends AbstractRequestHandler {
    @Override
    public ContentChannel handleRequest(final Request request, final ResponseHandler handler) {
        int port = request.getUri().getPort();
        Response response = new Response(OK);
        response.headers().put("Jdisc-Local-Port", Integer.toString(port));
        return handler.handleResponse(response);
    }
}

