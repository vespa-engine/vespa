// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.service.ClientProvider;

/**
 * <p>This interface defines a component that is capable of acting as a handler for a {@link Response}. An
 * implementation of this interface is required to be passed alongside every {@link Request} as part of the API (see
 * {@link ClientProvider#handleRequest(Request, ResponseHandler)} and {@link RequestHandler#handleRequest(Request,
 * ResponseHandler)}).</p>
 *
 * <p>The jDISC API is designed to not provide an implicit reference from Response to
 * corresponding Request, but rather leave that to the implementation of context-aware ResponseHandlers. By creating
 * light-weight ResponseHandlers on a per-Request basis, any necessary reference can be embedded within.</p>
 *
 * @author Simon Thoresen Hult
 */
public interface ResponseHandler {

    /**
     * This method will process the given {@link Response} and return a {@link ContentChannel} into which the caller
     * can write the Response's content.
     *
     * @param response the Response to handle
     * @return the ContentChannel to write the Response content to. Notice that the ContentChannel holds a Container
     *         reference, so failure to close this will prevent the Container from ever shutting down.
     */
    ContentChannel handleResponse(Response response);

}
