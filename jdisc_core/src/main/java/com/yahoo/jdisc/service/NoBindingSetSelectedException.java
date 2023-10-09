// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.service;

import com.yahoo.jdisc.application.BindingSet;
import com.yahoo.jdisc.application.BindingSetSelector;

import java.net.URI;

/**
 * This exception is used to signal that no {@link BindingSet} was selected for a given {@link URI}. An instance of this
 * class will be thrown by the {@link CurrentContainer#newReference(URI)} method if {@link
 * BindingSetSelector#select(URI)} returned <em>null</em>.
 *
 * @author Simon Thoresen Hult
 */
public final class NoBindingSetSelectedException extends RuntimeException {

    private final URI uri;

    /**
     * Constructs a new instance of this class with a detail message that contains the {@link URI} for which there was
     * no {@link BindingSet} selected.
     *
     * @param uri The URI for which there was no BindingSet selected.
     */
    public NoBindingSetSelectedException(URI uri) {
        super("No binding set selected for URI '" + uri + "'.");
        this.uri = uri;
    }

    /**
     * Returns the {@link URI} for which there was no {@link BindingSet} selected.
     *
     * @return The URI.
     */
    public URI uri() {
        return uri;
    }
}
