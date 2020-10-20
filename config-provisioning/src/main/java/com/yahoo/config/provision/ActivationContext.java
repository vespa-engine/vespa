// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

/**
 * The context passed to the node repo when activating a node.
 *
 * @author bratseth
 */
public class ActivationContext {

    private final long generation;

    public ActivationContext(long generation) {
        this.generation = generation;
    }

    /** Returns the application config generation we are activating */
    public long generation() { return generation; }

}
