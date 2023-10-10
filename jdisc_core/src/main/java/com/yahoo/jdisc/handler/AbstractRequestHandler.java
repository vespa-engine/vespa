// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;

/**
 * <p>This class provides an abstract {@link RequestHandler} implementation with reasonable defaults for everything but
 * {@link #handleRequest(Request, ResponseHandler)}.</p>
 *
 * <p>A very simple hello world handler could be implemented like this:</p>
 * <pre>
 * class HelloWorldHandler extends AbstractRequestHandler {
 *
 *     &#64;Override
 *     public ContentChannel handleRequest(Request request, ResponseHandler handler) {
 *         ContentWriter writer = ResponseDispatch.newInstance(Response.Status.OK).connectWriter(handler);
 *         try {
 *             writer.write("Hello World!");
 *         } finally {
 *             writer.close();
 *         }
 *         return null;
 *     }
 * }
 * </pre>
 *
 * @author Simon Thoresen Hult
 */
public abstract class AbstractRequestHandler extends com.yahoo.jdisc.AbstractResource implements RequestHandler {

    @Override
    public void handleTimeout(Request request, ResponseHandler responseHandler) {
        Response.dispatchTimeout(responseHandler);
    }

}
