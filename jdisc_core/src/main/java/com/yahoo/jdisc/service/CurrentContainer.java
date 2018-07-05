// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.service;

import com.yahoo.jdisc.Container;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.application.BindingSet;
import com.yahoo.jdisc.application.BindingSetSelector;
import com.yahoo.jdisc.handler.RequestHandler;

import java.net.URI;

/**
 * This interface declares a method to retrieve a reference to the current {@link Container}. Note that a {@link
 * Container} which has <em>not</em> been {@link Container#release() closed} will actively keep it alive, preventing it
 * from shutting down when expired. Failure to call close() will eventually lead to an {@link OutOfMemoryError}. A
 * {@link ServerProvider} should have an instance of this class injected in its constructor, and simply use the {@link
 * Request#Request(CurrentContainer, URI) appropriate Request constructor} to avoid having to worry about the keep-alive
 * issue.
 *
 * @author Simon Thoresen Hult
 */
public interface CurrentContainer {

    /**
     * Returns a reference to the currently active {@link Container}. Until {@link Container#release()} has been called,
     * the Container can not shut down.
     *
     * @param uri The identifier used to match this Request to an appropriate {@link ClientProvider} or {@link
     *            RequestHandler}. The hostname must be "localhost" or a fully qualified domain name.
     * @return A reference to the current Container.
     * @throws NoBindingSetSelectedException If no {@link BindingSet} was selected by the {@link BindingSetSelector}.
     * @throws BindingSetNotFoundException   If the named BindingSet was not found.
     * @throws ContainerNotReadyException    If no active Container was found, this can only happen during initial
     *                                       setup.
     */
    public Container newReference(URI uri);
}
