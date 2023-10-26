// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.service;

import com.google.inject.Inject;
import com.yahoo.jdisc.AbstractResource;
import com.yahoo.jdisc.Request;

import java.util.Objects;

/**
 * <p>This is a convenient parent class for {@link ServerProvider} with default implementations for all but the
 * essential {@link #start()} and {@link #close()} methods. It requires that the {@link CurrentContainer} is injected in
 * the constructor, since that interface is needed to dispatch {@link Request}s.</p>
 *
 * @author Simon Thoresen Hult
 */
public abstract class AbstractServerProvider extends AbstractResource implements ServerProvider {

    private final CurrentContainer container;

    @Inject
    protected AbstractServerProvider(CurrentContainer container) {
        Objects.requireNonNull(container, "container");
        this.container = container;
    }

    public final CurrentContainer container() {
        return container;
    }
}
