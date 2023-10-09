// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container.handlers;

import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;

/**
* @author Einar M R Rosenvinge
*/
public class EchoRequestHandler extends AbstractRequestHandler {

    @Override
    public ContentChannel handleRequest(com.yahoo.jdisc.Request request, ResponseHandler handler) {
        return handler.handleResponse(new com.yahoo.jdisc.Response(Response.Status.OK));
    }

}
