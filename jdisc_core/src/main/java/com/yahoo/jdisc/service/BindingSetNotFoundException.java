// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.service;

import com.yahoo.jdisc.application.BindingSet;

import java.net.URI;

/**
 * This exception is used to signal that a named {@link BindingSet} was not found. An instance of this class will be
 * thrown by the {@link CurrentContainer#newReference(URI)} method when a BindingSet with the specified name does not
 * exist.
 *
 * @author Simon Thoresen Hult
 */
public final class BindingSetNotFoundException extends RuntimeException {

    private final String bindingSet;

    /**
     * Constructs a new instance of this class with a detail message that contains the name of the {@link BindingSet}
     * that was not found.
     *
     * @param bindingSet The name of the {@link BindingSet} that was not found.
     */
    public BindingSetNotFoundException(String bindingSet) {
        super("No binding set named '" + bindingSet + "'.");
        this.bindingSet = bindingSet;
    }

    /**
     * Returns the name of the {@link BindingSet} that was not found.
     *
     * @return The name of the BindingSet.
     */
    public String bindingSet() {
        return bindingSet;
    }
}
