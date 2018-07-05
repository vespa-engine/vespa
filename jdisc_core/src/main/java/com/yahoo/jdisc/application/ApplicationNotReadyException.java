// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

/**
 * This exception is used to signal that no {@link Application} has been configured. An instance of this class will be
 * thrown by the {@link ContainerActivator#activateContainer(ContainerBuilder)} method if it is called before the call
 * to {@link Application#start()} or after the call to {@link Application#stop()}.
 *
 * @author Simon Thoresen Hult
 */
public final class ApplicationNotReadyException extends RuntimeException {

    /**
     * Constructs a new instance of this class with a detail message.
     */
    public ApplicationNotReadyException() {
        super("Application not ready.");
    }
}
