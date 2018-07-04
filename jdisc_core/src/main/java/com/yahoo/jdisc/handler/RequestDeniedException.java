// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.service.ClientProvider;

import java.net.URI;

/**
 * <p>This exception is used to signal that a {@link Request} was rejected by the corresponding {@link ClientProvider}
 * or {@link RequestHandler}. There is no automation in throwing an instance of this class, but all RequestHandlers are
 * encouraged to use this where appropriate.</p>
 *
 * @author Simon Thoresen Hult
 */
public final class RequestDeniedException extends RuntimeException {

    private final Request request;

    /**
     * <p>Constructs a new instance of this class with a detail message that contains the {@link URI} of the {@link
     * Request} that was denied.</p>
     *
     * @param request The Request that was denied.
     */
    public RequestDeniedException(Request request) {
        super("Request with URI '" + request.getUri() + "' denied.");
        this.request = request;
    }

    /**
     * <p>Returns the {@link Request} that was denied.</p>
     *
     * @return The Request that was denied.
     */
    public Request request() {
        return request;
    }
}
