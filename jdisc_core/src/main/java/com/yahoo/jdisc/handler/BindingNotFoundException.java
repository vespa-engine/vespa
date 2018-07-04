// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.application.BindingSet;

import java.net.URI;

/**
 * This exception is used to signal that no binding was found for the {@link URI} of a given {@link Request}. An
 * instance of this class will be thrown by the {@link Request#connect(ResponseHandler)} method when the current {@link
 * BindingSet} has not binding that matches the corresponding Request's URI.
 *
 * @author Simon Thoresen Hult
 */
public final class BindingNotFoundException extends RuntimeException {

    private final URI uri;

    /**
     * Constructs a new instance of this class with a detail message that contains the {@link URI} that has no binding.
     *
     * @param uri The URI that has no binding.
     */
    public BindingNotFoundException(URI uri) {
        super("No binding for URI '" + uri + "'.");
        this.uri = uri;
    }

    /**
     * Returns the {@link URI} that has no binding.
     *
     * @return The URI.
     */
    public URI uri() {
        return uri;
    }

}
