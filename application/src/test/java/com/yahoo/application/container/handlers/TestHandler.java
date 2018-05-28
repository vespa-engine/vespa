// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container.handlers;

import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.FastContentWriter;
import com.yahoo.jdisc.handler.ResponseDispatch;
import com.yahoo.jdisc.handler.ResponseHandler;

/**
 * @author gjoranv
 * @author ollivir
 */
public class TestHandler extends AbstractRequestHandler {
    public static final String RESPONSE = "Hello, World!";

    public ContentChannel handleRequest(com.yahoo.jdisc.Request request, ResponseHandler handler) {
        FastContentWriter writer = ResponseDispatch.newInstance(com.yahoo.jdisc.Response.Status.OK)
                .connectFastWriter(handler);
        writer.write(RESPONSE);
        writer.close();
        return null;
    }

}
