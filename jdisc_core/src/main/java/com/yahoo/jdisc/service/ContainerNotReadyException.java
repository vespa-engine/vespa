// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.service;

import com.yahoo.jdisc.Container;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.application.ContainerActivator;
import com.yahoo.jdisc.application.ContainerBuilder;

import java.net.URI;

/**
 * This exception is used to signal that no {@link Container} is ready to serve {@link Request}s. An instance of this
 * class will be thrown by the {@link CurrentContainer#newReference(URI)} method if it is called before a Container has
 * been activated, or after a <em>null</em> argument has been passed to {@link ContainerActivator#activateContainer(ContainerBuilder)}.
 *
 * @author Simon Thoresen Hult
 */
public final class ContainerNotReadyException extends RuntimeException {

    /**
     * Constructs a new instance of this class with a detail message.
     */
    public ContainerNotReadyException() {
        super("Container not ready.");
    }
}
